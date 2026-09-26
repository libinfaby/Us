// Text-only link previews for Stash, for IMDb and Google Maps links only.
// IMDb films are looked up on Wikidata + Wikipedia. Maps links carry the place
// in the URL they redirect to, so those redirects are followed by hand and no
// Google page is ever loaded. No images on purpose.

const FETCH_TIMEOUT_MS = 6000;
const MAX_MAPS_REDIRECTS = 5;
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

function decodePathSegment(segment: string): string | null {
  try {
    return clean(decodeURIComponent(segment.replace(/\+/g, ' ')));
  } catch {
    return null;
  }
}

/**
 * The place a Maps URL points at: /maps/place/{name}, /maps/search/{query} or ?q={query}.
 * Only read from real Maps URLs - a Google captcha page (/sorry/index) also has a ?q=, and
 * it's an opaque token, not a place.
 */
function placeFromMapsUrl(url: URL): string | null {
  if (!isMapsUrl(url)) return null;
  const pathMatch = url.pathname.match(/\/maps\/(?:place|search)\/([^/]+)/);
  const place = pathMatch ? decodePathSegment(pathMatch[1]) : clean(url.searchParams.get('q') ?? undefined);
  // A bare "lat,lng" isn't a name worth saving.
  return place && !/^-?[\d.]+,\s*-?[\d.]+$/.test(place) ? place : null;
}

/** When Google rate-limits us it redirects to /sorry/index?continue={where we were going}. */
function unwrapGoogleCaptcha(url: URL): URL {
  if (!/(^|\.)google\.[a-z.]+$/i.test(url.hostname) || !url.pathname.startsWith('/sorry')) return url;
  return parseHttpUrl(url.searchParams.get('continue') ?? '') ?? url;
}

/**
 * Follows a Maps link's redirects one hop at a time (maps.app.goo.gl -> google.com/maps/place/...)
 * and returns the place named along the way. The Maps pages themselves are never loaded: their
 * tags only ever say "Google Maps", and Google often answers Cloudflare's servers with a captcha.
 */
async function placeFromMapsLink(input: URL): Promise<string | null> {
  let current = unwrapGoogleCaptcha(input);
  for (let hop = 0; hop <= MAX_MAPS_REDIRECTS; hop++) {
    const place = placeFromMapsUrl(current);
    if (place) return place;
    if (hop === MAX_MAPS_REDIRECTS) break;

    let res: Response;
    try {
      res = await fetch(current.toString(), {
        redirect: 'manual',
        headers: { 'User-Agent': USER_AGENT, 'Accept-Language': 'en' },
        signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
      });
    } catch {
      return null;
    }
    const location = res.status >= 300 && res.status < 400 ? res.headers.get('location') : null;
    if (!location) return null;
    let next: URL | null;
    try {
      next = parseHttpUrl(new URL(location, current).toString());
    } catch {
      next = null;
    }
    if (!next) return null;
    current = unwrapGoogleCaptcha(next);
  }
  return null;
}

/** "Science fiction film" / "heist film" -> "science fiction" / "heist"; keeps the first few, no repeats. */
function toGenreTags(names: (string | null | undefined)[]): string[] {
  const tags = names
    .map((name) => clean(name ?? undefined)?.toLowerCase().replace(/\s+film$/, '').trim())
    .filter((tag): tag is string => !!tag);
  return [...new Set(tags)].slice(0, MAX_GENRES);
}

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

  const place = await placeFromMapsLink(input);
  if (!place) return null;
  // Keep the link as shared (e.g. the short maps.app.goo.gl one) rather than the long redirect target.
  return { url: input.toString(), title: place, description: null, genres: [], suggestedType: 'place' };
}
