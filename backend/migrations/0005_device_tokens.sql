-- One FCM registration token per user, overwritten whenever the app gets a
-- fresh token (fresh install, token rotation, or explicit re-registration
-- after login). Only ever 2 users, so username doubles as PK like `users`.
CREATE TABLE device_tokens (
  username TEXT PRIMARY KEY REFERENCES users(username),
  fcm_token TEXT NOT NULL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
