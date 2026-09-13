import { USERNAMES, type Username } from './users';

/** Always an even split between the two (and only two) users. */
export function equalSplit(amountCents: number, paidBy: Username): Record<Username, number> {
  const half = Math.floor(amountCents / 2);
  const remainder = amountCents - half * 2;
  const other = USERNAMES.find((u) => u !== paidBy)!;
  // Give any odd cent to the payer so the shares always sum to amountCents.
  return { [paidBy]: half + remainder, [other]: half } as Record<Username, number>;
}

/** Validates a client-supplied custom split, returning null if it's malformed. */
export function validateCustomSplit(amountCents: number, split: unknown): Record<Username, number> | null {
  if (typeof split !== 'object' || split === null) return null;
  const entries = Object.entries(split as Record<string, unknown>);
  if (entries.length !== USERNAMES.length) return null;

  const result = {} as Record<Username, number>;
  let sum = 0;
  for (const [key, value] of entries) {
    if (!USERNAMES.includes(key as Username) || typeof value !== 'number' || !Number.isInteger(value) || value < 0) {
      return null;
    }
    result[key as Username] = value;
    sum += value;
  }
  if (sum !== amountCents) return null;
  return result;
}
