// Text-only link previews for Stash: fetch a page and pull out a title and
// description from its OpenGraph / meta tags using Workers' built-in
// HTMLRewriter, so there's no HTML-parsing dependency. No images on purpose.

const FETCH_TIMEOUT_MS = 6000;
const MAX_DESCRIPTION_LENGTH = 300;
const USER_AGENT = 'PinguCoduLinkPreview/1.0 (+personal app)';

export type LinkPreview = {
  url: string;
  title: string;
  description: string | null;
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

export function isMapsUrl(url: URL): boolean {
  const host = url.hostname.toLowerCase();
  if (host === 'maps.app.goo.gl' || host === 'maps.google.com') return true;
  if (host === 'goo.gl') return url.pathname.startsWith('/maps');
  return /(^|\.)google\.[a-z.]+$/.test(host) && url.pathname.startsWith('/maps');
}

type Meta = { ogTitle?: string; twitterTitle?: string; docTitle: string; ogDescription?: string; metaDescription?: string };

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

/** Strips site branding, e.g. "Inception (2010) • Letterboxd" -> "Inception (2010)". */
function cleanTitle(title: string, host: string): string {
  let t = title;
  if (host.endsWith('letterboxd.com')) {
    t = t.replace(/\s*[•|-]\s*Letterboxd\s*$/i, '');
  }
  return t.trim();
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
async function imdbPreviewFromWikidata(imdbId: string): Promise<{ title: string; description: string | null } | null> {
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
      }
    >;
  }>(
    `${WIKIDATA_API}?action=wbgetentities&ids=${entityId}&props=labels|descriptions|sitelinks&languages=en&sitefilter=enwiki&format=json`,
  );
  const entity = entities?.entities?.[entityId];
  const label = clean(entity?.labels?.en?.value);
  if (!label) return null;
  const tagline = clean(entity?.descriptions?.en?.value);
  const articleTitle = entity?.sitelinks?.enwiki?.title;
  const summary = articleTitle ? await wikipediaSummary(articleTitle) : null;
  // Match Letterboxd's "Title (year)" style when the tagline leads with the year.
  const year = tagline?.match(/^(\d{4})\b/)?.[1];
  const capitalizedTagline = tagline ? tagline.charAt(0).toUpperCase() + tagline.slice(1) : null;
  const description = [capitalizedTagline, summary].filter((part): part is string => part !== null).join('\n\n');
  return {
    title: year ? `${label} (${year})` : label,
    description: description.length > 0 ? description : null,
  };
}

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`;
}

/** Returns null when the page can't be fetched or has nothing usable in it. */
export async function fetchLinkPreview(input: URL): Promise<LinkPreview | null> {
  const imdbId = /(^|\.)imdb\.com$/i.test(input.hostname) ? input.pathname.match(/\/title\/(tt\d+)/)?.[1] : undefined;
  if (imdbId) {
    const fromWikidata = await imdbPreviewFromWikidata(imdbId);
    return fromWikidata ? { url: input.toString(), ...fromWikidata, suggestedType: 'movie' } : null;
  }

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
  const isMaps = isMapsUrl(input) || isMapsUrl(finalUrl);
  const host = finalUrl.hostname.toLowerCase();

  let meta: Meta = { docTitle: '' };
  const contentType = res.headers.get('content-type') ?? '';
  if (res.ok && contentType.includes('html')) {
    try {
      meta = await extractMeta(res);
    } catch {
      // Fall through - for Maps links the URL alone can still give us a name.
    }
  }

  let title = clean(meta.ogTitle) ?? clean(meta.twitterTitle) ?? clean(meta.docTitle);
  if (title) title = cleanTitle(title, host);
  if (isMaps && (!title || /^google maps$/i.test(title))) {
    title =
      placeNameFromMapsUrl(finalUrl) ??
      placeNameFromMapsUrl(input) ??
      searchQueryFromMapsUrl(finalUrl) ??
      searchQueryFromMapsUrl(input);
  }
  if (!title) return null;

  let description = clean(meta.ogDescription) ?? clean(meta.metaDescription);
  if (isMaps && description && GENERIC_MAPS_DESCRIPTION.test(description)) description = null;
  // Keep the link as shared (e.g. the short maps.app.goo.gl one) rather than the long redirect target.
  return {
    url: input.toString(),
    title,
    description: description ? truncate(description, MAX_DESCRIPTION_LENGTH) : null,
    suggestedType: isMaps ? 'place' : 'movie',
  };
}
