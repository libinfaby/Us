export type Env = {
  DB: D1Database;
  SESSION_SECRET: string;
  FCM_SERVICE_ACCOUNT_JSON: string;
  /** omdbapi.com key for IMDb plots and genres in Stash movie previews; optional - Wikipedia is the fallback. */
  OMDB_API_KEY?: string;
  /** Google Books API key for Stash book search and previews. Needed in practice: keyless requests get a quota of 0. */
  GOOGLE_BOOKS_API_KEY?: string;
};
