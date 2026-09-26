export type Env = {
  DB: D1Database;
  SESSION_SECRET: string;
  FCM_SERVICE_ACCOUNT_JSON: string;
  /** omdbapi.com key for IMDb plots and genres in Stash movie previews; optional - Wikipedia is the fallback. */
  OMDB_API_KEY?: string;
};
