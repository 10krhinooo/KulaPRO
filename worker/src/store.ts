import type { DishNutrition } from './nutrition';

/**
 * The cache of results, and the per user daily counters.
 *
 * Both live in the same KV namespace because both are disposable. Losing the namespace
 * costs one model call per dish on the next scan and resets everyone's quota, which is
 * annoying and not damaging.
 */
export interface Store {
  get(key: string): Promise<string | null>;
  put(key: string, value: string, options?: { expirationTtl?: number }): Promise<void>;
}

/** A month. Long enough that a popular dish is paid for once, short enough to stay fresh. */
const RESULT_TTL_SECONDS = 30 * 24 * 60 * 60;

/** Two days, so a counter for a past day cleans itself up. */
const COUNTER_TTL_SECONDS = 2 * 24 * 60 * 60;

/**
 * The cache key for an image.
 *
 * Content addressed, so the same dish photographed by two different people is paid for
 * once. It also means the key reveals nothing: a hash of the bytes identifies no person.
 */
export async function imageKey(imageBase64: string): Promise<string> {
  const bytes = new TextEncoder().encode(imageBase64);
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  const hex = Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
  return `dish:${hex}`;
}

export async function readCached(
  store: Store,
  key: string,
): Promise<DishNutrition | null> {
  const raw = await store.get(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as DishNutrition;
  } catch {
    // A corrupt entry is a cache miss, not an error. Paying for one more call beats
    // failing a scan over a value nobody will miss.
    return null;
  }
}

export async function writeCached(
  store: Store,
  key: string,
  result: DishNutrition,
): Promise<void> {
  await store.put(key, JSON.stringify(result), { expirationTtl: RESULT_TTL_SECONDS });
}

/** The counter key for one user on one day, in UTC so it cannot be reset by travelling. */
export function quotaKey(uid: string, now: number): string {
  const day = new Date(now).toISOString().slice(0, 10);
  return `quota:${day}:${uid}`;
}

export interface QuotaResult {
  allowed: boolean;
  used: number;
  limit: number;
}

/**
 * Counts a scan against the user's day.
 *
 * Read then write rather than an atomic increment, because KV offers no atomic increment
 * and the consequence of losing a race is one extra scan. Paying for correctness here would
 * mean Durable Objects, which are not free, to prevent an overrun of one.
 */
export async function consumeQuota(
  store: Store,
  uid: string,
  limit: number,
  now: number = Date.now(),
): Promise<QuotaResult> {
  const key = quotaKey(uid, now);
  const used = Number((await store.get(key)) ?? '0');
  if (used >= limit) return { allowed: false, used, limit };

  await store.put(key, String(used + 1), { expirationTtl: COUNTER_TTL_SECONDS });
  return { allowed: true, used: used + 1, limit };
}
