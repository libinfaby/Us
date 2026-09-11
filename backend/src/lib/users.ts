export const USERNAMES = ['pingu', 'codu'] as const;
export type Username = (typeof USERNAMES)[number];

export function isUsername(value: unknown): value is Username {
  return typeof value === 'string' && (USERNAMES as readonly string[]).includes(value);
}
