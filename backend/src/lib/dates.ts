// Calendar-date helpers for yyyy-MM-dd strings. Dates are treated as plain
// calendar days (anchored at UTC midnight) so day arithmetic never drifts
// across DST or timezone offsets.

const MS_PER_DAY = 24 * 60 * 60 * 1000;

const ISO_DATE_REGEX = /^\d{4}-\d{2}-\d{2}$/;

export function isIsoDate(value: unknown): value is string {
  if (typeof value !== 'string' || !ISO_DATE_REGEX.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value;
}

/** Today's calendar date for the two of us - both live in India, so "today" is IST, not UTC. */
export function todayIst(): string {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' });
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

export function daysBetween(fromIso: string, toIso: string): number {
  const from = new Date(`${fromIso}T00:00:00Z`).getTime();
  const to = new Date(`${toIso}T00:00:00Z`).getTime();
  return Math.round((to - from) / MS_PER_DAY);
}

function isLeapYear(year: number): boolean {
  return (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0;
}

/** [isoDate]'s month/day in [year]; Feb 29 falls back to Feb 28 in non-leap years. */
export function sameDayInYear(isoDate: string, year: number): string {
  const monthDay = isoDate.slice(5);
  const adjusted = monthDay === '02-29' && !isLeapYear(year) ? '02-28' : monthDay;
  return `${String(year).padStart(4, '0')}-${adjusted}`;
}

/** A milestone's next yearly anniversary on or after [today], and whole years since it as of then. */
export function nextAnniversary(date: string, today: string): { date: string; years: number } {
  const startYear = Number(date.slice(0, 4));
  const thisYear = Number(today.slice(0, 4));
  const candidate = sameDayInYear(date, thisYear);
  const year = candidate >= today ? thisYear : thisYear + 1;
  return { date: sameDayInYear(date, year), years: year - startYear };
}
