// Text-only link previews for Stash, for IMDb and Google Maps links only.
// IMDb films are looked up on Wikidata + Wikipedia; Maps pages are read for
// their OpenGraph / meta tags with Workers' built-in HTMLRewriter, so there's
// no HTML-parsing dependency. No images on purpose.

const FETCH_TIMEOUT_MS = 6000;
const MAX_DESCRIPTION_LENGTH = 300;
const MAX_GENRES = 3;
const USER_AGENT = 'PinguCoduLinkPreview/1.0 (+personal app)';

export type LinkPreview = {
  url: string;
  title: string;
  description: string | null;
  /** Lowercase genre names for a movie, e.g. ["heist", "science fiction"]; empty for places. */
  genres: string[];
  suggestedType: 'movie' | 'place';
};

/** Returns the parsed http(s) URL, or null for anything else (javascript:, file:, garbage). */
export function parseHttpUrl(raw: string): URL | null {
  try {
    const url = new URL(raw.trim());
    return url.protocol === 'http:' || url.protocol === 'https:' ? url : null;
  } catch {
    return null;
  }
}

/** The film id in an IMDb title link, e.g. "tt1375666", or null for any other link. */
function imdbIdFromUrl(url: URL): string | null {
  if (!/(^|\.)imdb\.com$/i.test(url.hostname)) return null;
  return url.pathname.match(/\/title\/(tt\d+)/)?.[1] ?? null;
}

/** Only IMDb title links and Google Maps links get a preview. */
export function isSupportedPreviewUrl(url: URL): boolean {
  return imdbIdFromUrl(url) !== null || isMapsUrl(url);
}

export function isMapsUrl(url: URL): boolean {
  const host = url.hostname.toLowerCase();
  if (host === 'maps.app.goo.gl' || host === 'maps.google.com') return true;
  if (host === 'goo.gl') return url.pathname.startsWith('/maps');
  return /(^|\.)google\.[a-z.]+$/.test(host) && url.pathname.startsWith('/maps');
}

type Meta = {
  ogTitle?: string;
  twitterTitle?: string;
  docTitle: string;
  ogDescription?: string;
  metaDescription?: string;
};

async function extractMeta(res: Response): Promise<Meta> {
  const meta: Meta = { docTitle: '' };
  let inTitle = false;
  const rewriter = new HTMLRewriter()
    .on('meta', {
      element(el) {
        const key = (el.getAttribute('property') ?? el.getAttribute('name') ?? '').toLowerCase();
        const content = el.getAttribute('content');
        if (!content) return;
        if (key === 'og:title') meta.ogTitle ??= content;
        else if (key === 'twitter:title') meta.twitterTitle ??= content;
        else if (key === 'og:description') meta.ogDescription ??= content;
        else if (key === 'description') meta.metaDescription ??= content;
      },
    })
    .on('title', {
      element(el) {
        if (meta.docTitle.length > 0) return;
        inTitle = true;
        el.onEndTag(() => {
          inTitle = false;
        });
      },
      text(chunk) {
        if (inTitle) meta.docTitle += chunk.text;
      },
    });
  // Consume the transformed body so the handlers run; we only care about the side effects.
  await rewriter.transform(res).arrayBuffer();
  return meta;
}

function decodeEntities(text: string): string {
  return text
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#0?39;|&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&#(\d+);/g, (_, code) => String.fromCodePoint(Number(code)));
}

function clean(text: string | undefined): string | null {
  if (!text) return null;
  const cleaned = decodeEntities(text).replace(/\s+/g, ' ').trim();
  return cleaned.length > 0 ? cleaned : null;
}

/** Google Maps pages often only say "Google Maps" in their tags; the place name is in the URL path. */
function placeNameFromMapsUrl(url: URL): string | null {
  const match = url.pathname.match(/\/maps\/place\/([^/]+)/);
  if (!match) return null;
  try {
    return clean(decodeURIComponent(match[1].replace(/\+/g, ' ')));
  } catch {
    return null;
  }
}

/** Maps links sometimes carry the place as a search instead: /maps/search/{q} or ?q={q}. */
function searchQueryFromMapsUrl(url: URL): string | null {
  const match = url.pathname.match(/\/maps\/search\/([^/]+)/);
  const raw = match ? match[1] : url.searchParams.get('q');
  if (!raw) return null;
  try {
    const decoded = clean(decodeURIComponent(raw.replace(/\+/g, ' ')));
    // A bare "lat,lng" query isn't a name worth saving.
    return decoded && !/^-?[\d.]+,\s*-?[\d.]+$/.test(decoded) ? decoded : null;
  } catch {
    return null;
  }
}

/** "Science fiction film" / "heist film" -> "science fiction" / "heist"; keeps the first few, no repeats. */
function toGenreTags(names: (string | null | undefined)[]): string[] {
  const tags = names
    .map((name) => clean(name ?? undefined)?.toLowerCase().replace(/\s+film$/, '').trim())
    .filter((tag): tag is string => !!tag);
  return [...new Set(tags)].slice(0, MAX_GENRES);
}

// Google's boilerplate og:description on every Maps page - not worth saving.
const GENERIC_MAPS_DESCRIPTION = /^find local businesses, view maps and get driving directions/i;

const WIKIDATA_API = 'https://www.wikidata.org/w/api.php';

async function fetchJson<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT }, signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
    return res.ok ? ((await res.json()) as T) : null;
  } catch {
    return null;
  }
}

const MAX_SUMMARY_LENGTH = 700;

/** Cuts [text] at the last full sentence that fits in [max], so a long summary doesn't end mid-word. */
function truncateAtSentence(text: string, max: number): string {
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  const lastStop = cut.lastIndexOf('. ');
  return lastStop > 0 ? cut.slice(0, lastStop + 1) : truncate(text, max);
}

/** The opening paragraph of an English Wikipedia article, e.g. the premise and cast of a film. */
async function wikipediaSummary(articleTitle: string): Promise<string | null> {
  const summary = await fetchJson<{ extract?: string }>(
    `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(articleTitle.replace(/ /g, '_'))}`,
  );
  const extract = clean(summary?.extract);
  return extract ? truncateAtSentence(extract, MAX_SUMMARY_LENGTH) : null;
}

/**
 * IMDb answers every non-browser fetch with a bot challenge (HTTP 202), so its pages can't be
 * read. Wikidata indexes films by IMDb id (property P345) and needs no API key, so look the title
 * up there instead: "Inception" + "2010 film directed by Christopher Nolan", then add the opening
 * paragraph of the film's English Wikipedia article as a short summary.
 */
async function imdbPreviewFromWikidata(
  imdbId: string,
): Promise<{ title: string; description: string | null; genres: string[] } | null> {
  const search = await fetchJson<{ query?: { search?: { title: string }[] } }>(
    `${WIKIDATA_API}?action=query&list=search&srsearch=haswbstatement:P345=${imdbId}&srlimit=1&format=json`,
  );
  const entityId = search?.query?.search?.[0]?.title;
  if (!entityId || !/^Q\d+$/.test(entityId)) return null;

  const entities = await fetchJson<{
    entities?: Record<
      string,
      {
        labels?: { en?: { value: string } };
        descriptions?: { en?: { value: string } };
        sitelinks?: { enwiki?: { title: string } };
        claims?: { P136?: { mainsnak?: { datavalue?: { value?: { id?: string } } } }[] };
      }
    >;
  }>(
    `${WIKIDATA_API}?action=wbgetentities&ids=${entityId}&props=labels|descriptions|sitelinks|claims&languages=en&sitefilter=enwiki&format=json`,
  );
  const entity = entities?.entities?.[entityId];
  const label = clean(entity?.labels?.en?.value);
  if (!label) return null;
  const tagline = clean(entity?.descriptions?.en?.value);
  const articleTitle = entity?.sitelinks?.enwiki?.title;
  // Genre (P136) values are entity ids like Q2484376; their names need one more lookup.
  const genreIds = (entity?.claims?.P136 ?? [])
    .map((claim) => claim.mainsnak?.datavalue?.value?.id)
    .filter((id): id is string => !!id && /^Q\d+$/.test(id))
    .slice(0, MAX_GENRES);
  const [summary, genres] = await Promise.all([
    articleTitle ? wikipediaSummary(articleTitle) : Promise.resolve(null),
    wikidataLabels(genreIds),
  ]);
  // "Title (year)" when the tagline leads with the year.
  const year = tagline?.match(/^(\d{4})\b/)?.[1];
  // The summary paragraph on its own; the short tagline only when there's no article.
  const description = summary ?? (tagline ? tagline.charAt(0).toUpperCase() + tagline.slice(1) : null);
  return {
    title: year ? `${label} (${year})` : label,
    description,
    genres: toGenreTags(genres),
  };
}

/** English names for Wikidata ids, in the same order; ids without a name are dropped. */
async function wikidataLabels(ids: string[]): Promise<string[]> {
  if (ids.length === 0) return [];
  const res = await fetchJson<{ entities?: Record<string, { labels?: { en?: { value: string } } }> }>(
    `${WIKIDATA_API}?action=wbgetentities&ids=${ids.join('|')}&props=labels&languages=en&format=json`,
  );
  return ids.map((id) => res?.entities?.[id]?.labels?.en?.value).filter((name): name is string => !!name);
}

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`;
}

/** Returns null for links other than IMDb / Google Maps, or when there's nothing usable to show. */
export async function fetchLinkPreview(input: URL): Promise<LinkPreview | null> {
  const imdbId = imdbIdFromUrl(input);
  if (imdbId) {
    const fromWikidata = await imdbPreviewFromWikidata(imdbId);
    return fromWikidata ? { url: input.toString(), ...fromWikidata, suggestedType: 'movie' } : null;
  }
  if (!isMapsUrl(input)) return null;

  let res: Response;
  try {
    res = await fetch(input.toString(), {
      redirect: 'follow',
      headers: { 'User-Agent': USER_AGENT, Accept: 'text/html,application/xhtml+xml', 'Accept-Language': 'en' },
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    });
  } catch {
    return null;
  }

  const finalUrl = parseHttpUrl(res.url) ?? input;

  let meta: Meta = { docTitle: '' };
  const contentType = res.headers.get('content-type') ?? '';
  if (res.ok && contentType.includes('html')) {
    try {
      meta = await extractMeta(res);
    } catch {
      // Fall through - the URL alone can still give us a name.
    }
  }

  let title = clean(meta.ogTitle) ?? clean(meta.twitterTitle) ?? clean(meta.docTitle);
  if (!title || /^google maps$/i.test(title)) {
    title =
      placeNameFromMapsUrl(finalUrl) ??
      placeNameFromMapsUrl(input) ??
      searchQueryFromMapsUrl(finalUrl) ??
      searchQueryFromMapsUrl(input);
  }
  if (!title) return null;

  let description = clean(meta.ogDescription) ?? clean(meta.metaDescription);
  if (description && GENERIC_MAPS_DESCRIPTION.test(description)) description = null;
  // Keep the link as shared (e.g. the short maps.app.goo.gl one) rather than the long redirect target.
  return {
    url: input.toString(),
    title,
    description: description ? truncate(description, MAX_DESCRIPTION_LENGTH) : null,
    genres: [],
    suggestedType: 'place',
  };
}
