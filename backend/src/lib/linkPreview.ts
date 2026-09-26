// Text-only link previews for Stash, for IMDb and Google Maps links only.
// IMDb films come from OMDb (IMDb's plot and genres), with Wikidata + Wikipedia
// as the fallback and for finding films by name. Maps links carry the place
// in the URL they redirect to, so those redirects are followed by hand and no
// Google page is ever loaded. No images on purpose.

const FETCH_TIMEOUT_MS = 6000;
const MAX_MAPS_REDIRECTS = 5;
const MAX_GENRES = 3;
// Wikimedia gives clients a far higher rate limit (200/min vs 10/min) when the User-Agent carries contact info.
const USER_AGENT = 'PinguCoduLinkPreview/1.0 (https://github.com/libinfaby/Us)';

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

/** GETs JSON; on HTTP 429 (Wikimedia rate-limits shared Cloudflare IPs now and then) waits a moment and tries once more. */
async function fetchJson<T>(url: string): Promise<T | null> {
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT }, signal: AbortSignal.timeout(FETCH_TIMEOUT_MS) });
      if (res.status === 429 && attempt === 0) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
        continue;
      }
      return res.ok ? ((await res.json()) as T) : null;
    } catch {
      return null;
    }
  }
  return null;
}

const WIKIPEDIA_API = 'https://en.wikipedia.org/w/api.php';
const MAX_SUMMARY_LENGTH = 700;
const MAX_PLOT_LENGTH = 450;
const MAX_SEARCH_RESULTS = 5;

/** Cuts [text] at the last full sentence that fits in [max], so a long summary doesn't end mid-word. */
function truncateAtSentence(text: string, max: number): string {
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  const lastStop = cut.lastIndexOf('. ');
  return lastStop > 0 ? cut.slice(0, lastStop + 1) : truncate(text, max);
}

/** The opening paragraph of an English Wikipedia article - for a film, its genre, director and cast. */
async function wikipediaSummary(articleTitle: string): Promise<string | null> {
  const summary = await fetchJson<{ extract?: string }>(
    `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(articleTitle.replace(/ /g, '_'))}`,
  );
  const extract = clean(summary?.extract);
  return extract ? truncateAtSentence(extract, MAX_SUMMARY_LENGTH) : null;
}

// Plain-text article headings look like "== Plot ==" ("=== ... ===" is a subsection).
const PLOT_HEADING = /^==\s*(?:plot|plot summary|synopsis|premise|story)\s*==\s*$/im;

/**
 * The first few sentences of a film article's Plot section: who the characters are and the setup,
 * cut short to stay clear of spoilers. Null when the article has no such section.
 */
async function wikipediaPlotOpening(articleTitle: string): Promise<string | null> {
  const res = await fetchJson<{ query?: { pages?: Record<string, { extract?: string }> } }>(
    `${WIKIPEDIA_API}?action=query&prop=extracts&explaintext=1&redirects=1&titles=${encodeURIComponent(articleTitle)}&format=json`,
  );
  const text = Object.values(res?.query?.pages ?? {})[0]?.extract;
  const heading = text?.match(PLOT_HEADING);
  if (!text || !heading || heading.index === undefined) return null;
  const paragraph = text
    .slice(heading.index + heading[0].length)
    .split(/\n+/)
    .map((line) => line.trim())
    .find((line) => line.length > 0 && !line.startsWith('='));
  const cleaned = clean(paragraph);
  return cleaned ? truncateAtSentence(cleaned, MAX_PLOT_LENGTH) : null;
}

// Wikidata keeps many names under the language-neutral "mul" label instead of "en" (Toy Story has no "en").
type WikidataLabels = { en?: { value: string }; mul?: { value: string } };

function wikidataLabel(labels: WikidataLabels | undefined): string | null {
  return clean(labels?.en?.value) ?? clean(labels?.mul?.value);
}

type WikidataFilm = {
  labels?: WikidataLabels;
  descriptions?: { en?: { value: string } };
  sitelinks?: { enwiki?: { title: string } };
  claims?: {
    P136?: { mainsnak?: { datavalue?: { value?: { id?: string } } } }[];
    P345?: { mainsnak?: { datavalue?: { value?: unknown } } }[];
  };
};

type OmdbFilm = { title: string | null; plot: string | null; genres: string[] };

/**
 * IMDb's own details for a film via OMDb (IMDb itself blocks non-browser fetches): the title and
 * year, IMDb's one-line plot ("An insurance salesman begins to suspect that his whole life is
 * actually some sort of reality TV show.") and its genres. Null without a key, or when OMDb fails.
 */
async function omdbFilm(imdbId: string, apiKey: string | undefined): Promise<OmdbFilm | null> {
  if (!apiKey) return null;
  const res = await fetchJson<{ Response?: string; Title?: string; Year?: string; Plot?: string; Genre?: string }>(
    `https://www.omdbapi.com/?i=${imdbId}&plot=short&apikey=${encodeURIComponent(apiKey)}`,
  );
  if (res?.Response !== 'True') return null;
  // OMDb says "N/A" for anything it doesn't have.
  const known = (value: string | undefined) => (value && value !== 'N/A' ? clean(value) : null);
  const name = known(res.Title);
  // A series' Year is a range like "2019–2022"; the first year is enough.
  const year = known(res.Year)?.match(/^\d{4}/)?.[0];
  return {
    title: name ? (year ? `${name} (${year})` : name) : null,
    plot: known(res.Plot),
    genres: toGenreTags(known(res.Genre)?.split(',') ?? []),
  };
}

/**
 * Everything the Stash form fills in for a film, from its Wikidata item (e.g. Q25188 = Inception):
 * "Inception (2010)", a short plot, up to three genres, and its IMDb link (empty when Wikidata has
 * none). The plot and genres are IMDb's via OMDb when the film has an IMDb id; otherwise, or when
 * OMDb has nothing, the opening of the Wikipedia plot and Wikidata's genres.
 */
export async function filmPreview(entityId: string, omdbApiKey?: string): Promise<LinkPreview | null> {
  if (!/^Q\d+$/.test(entityId)) return null;
  const entities = await fetchJson<{ entities?: Record<string, WikidataFilm> }>(
    `${WIKIDATA_API}?action=wbgetentities&ids=${entityId}&props=labels|descriptions|sitelinks|claims&languages=en|mul&sitefilter=enwiki&format=json`,
  );
  const entity = entities?.entities?.[entityId];
  const articleTitle = entity?.sitelinks?.enwiki?.title;
  // Last resort for the name: the Wikipedia article's title, minus "(2013 film)"-style disambiguation.
  const label = wikidataLabel(entity?.labels) ?? clean(articleTitle?.replace(/\s*\([^)]*\)$/, ''));
  if (!entity || !label) return null;
  const tagline = clean(entity.descriptions?.en?.value);
  const imdbId = (entity.claims?.P345 ?? [])
    .map((claim) => claim.mainsnak?.datavalue?.value)
    .find((value): value is string => typeof value === 'string' && /^tt\d+$/.test(value));
  const omdb = imdbId ? await omdbFilm(imdbId, omdbApiKey) : null;
  // Genre (P136) values are entity ids like Q2484376; their names need one more lookup.
  const genreIds = (entity.claims?.P136 ?? [])
    .map((claim) => claim.mainsnak?.datavalue?.value?.id)
    .filter((id): id is string => !!id && /^Q\d+$/.test(id))
    .slice(0, MAX_GENRES);
  // Only ask Wikipedia / Wikidata for what OMDb didn't give us.
  const [wikiPlot, wikiGenres] = await Promise.all([
    !omdb?.plot && articleTitle ? wikipediaPlotOpening(articleTitle) : Promise.resolve(null),
    omdb?.genres.length ? Promise.resolve([]) : wikidataLabels(genreIds),
  ]);
  // No Plot section -> the article's opening paragraph; no article at all -> the short tagline.
  const plot = omdb?.plot ?? wikiPlot ?? (articleTitle ? await wikipediaSummary(articleTitle) : null);
  // "Title (year)" when the tagline leads with the year.
  const year = tagline?.match(/^(\d{4})\b/)?.[1];
  return {
    url: imdbId ? `https://www.imdb.com/title/${imdbId}/` : '',
    title: year ? `${label} (${year})` : (omdb?.title ?? label),
    description: plot ?? (tagline ? tagline.charAt(0).toUpperCase() + tagline.slice(1) : null),
    genres: omdb?.genres.length ? omdb.genres : toGenreTags(wikiGenres),
    suggestedType: 'movie',
  };
}

/**
 * A pasted IMDb link: OMDb alone usually has everything (one request). When it doesn't, find the
 * film's Wikidata item by its IMDb id (property P345) and build the preview from there.
 */
async function imdbPreview(imdbId: string, omdbApiKey: string | undefined): Promise<LinkPreview | null> {
  const omdb = await omdbFilm(imdbId, omdbApiKey);
  if (omdb?.title && omdb.plot) {
    return { url: `https://www.imdb.com/title/${imdbId}/`, title: omdb.title, description: omdb.plot, genres: omdb.genres, suggestedType: 'movie' };
  }
  const search = await fetchJson<{ query?: { search?: { title: string }[] } }>(
    `${WIKIDATA_API}?action=query&list=search&srsearch=haswbstatement:P345=${imdbId}&srlimit=1&format=json`,
  );
  const entityId = search?.query?.search?.[0]?.title;
  // OMDb already came up short, so don't ask it again - but keep IMDb's genres if it had them.
  const film = entityId ? await filmPreview(entityId) : null;
  return film && omdb?.genres.length ? { ...film, genres: omdb.genres } : film;
}

export type MovieSearchResult = {
  /** Wikidata item id, passed back to [filmPreview] once one is picked. */
  id: string;
  title: string;
  /** Wikipedia's short description, e.g. "2013 Indian film by Jeethu Joseph" - tells same-named films apart. */
  description: string;
};

// Film articles' short descriptions lead with the year ("1995 film by John Lasseter"); people
// ("Malayalam film director") and extras ("Music of the 2001 feature film") don't.
const FILM_DESCRIPTION = /^\d{4}\b[^.]*\bfilm\b/i;
const NOT_A_FILM = /\b(soundtrack|series|video game|franchise)\b/i;

/**
 * Films matching a typed name, best match first, using Wikipedia's search (it ranks the well-known
 * film above remakes and namesakes). Null when the search itself fails.
 */
export async function searchMovies(query: string): Promise<MovieSearchResult[] | null> {
  const res = await fetchJson<{
    query?: { pages?: Record<string, { title: string; index: number; description?: string; pageprops?: { wikibase_item?: string } }> };
  }>(
    `${WIKIPEDIA_API}?action=query&generator=search&gsrsearch=${encodeURIComponent(`${query} film`)}&gsrlimit=15&prop=description|pageprops&ppprop=wikibase_item&format=json`,
  );
  if (!res) return null;
  return Object.values(res.query?.pages ?? {})
    .sort((a, b) => a.index - b.index)
    .flatMap((page) => {
      const id = page.pageprops?.wikibase_item;
      const description = clean(page.description);
      if (!id || !description || !FILM_DESCRIPTION.test(description) || NOT_A_FILM.test(description)) return [];
      // "Drishyam 2 (2022 film)" -> "Drishyam 2"; the description already carries the year.
      return [{ id, title: page.title.replace(/\s*\([^)]*\bfilm\)$/i, ''), description }];
    })
    .slice(0, MAX_SEARCH_RESULTS);
}

/** English names for Wikidata ids, in the same order; ids without a name are dropped. */
async function wikidataLabels(ids: string[]): Promise<string[]> {
  if (ids.length === 0) return [];
  const res = await fetchJson<{ entities?: Record<string, { labels?: WikidataLabels }> }>(
    `${WIKIDATA_API}?action=wbgetentities&ids=${ids.join('|')}&props=labels&languages=en|mul&format=json`,
  );
  return ids.map((id) => wikidataLabel(res?.entities?.[id]?.labels)).filter((name): name is string => !!name);
}

function truncate(text: string, max: number): string {
  return text.length <= max ? text : `${text.slice(0, max - 1).trimEnd()}…`;
}

/**
 * Returns null for links other than IMDb / Google Maps, or when there's nothing usable to show.
 * [omdbApiKey] is the OMDB_API_KEY secret, used for IMDb's plot and genres.
 */
export async function fetchLinkPreview(input: URL, omdbApiKey?: string): Promise<LinkPreview | null> {
  const imdbId = imdbIdFromUrl(input);
  if (imdbId) {
    const film = await imdbPreview(imdbId, omdbApiKey);
    // Keep the link as pasted.
    return film ? { ...film, url: input.toString() } : null;
  }
  if (!isMapsUrl(input)) return null;

  const place = await placeFromMapsLink(input);
  if (!place) return null;
  // Keep the link as shared (e.g. the short maps.app.goo.gl one) rather than the long redirect target.
  return { url: input.toString(), title: place, description: null, genres: [], suggestedType: 'place' };
}
