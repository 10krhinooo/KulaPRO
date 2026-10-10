import { beforeEach, describe, expect, it } from 'vitest';
import {
  consumeQuota,
  imageKey,
  quotaKey,
  readCached,
  writeCached,
  type Store,
} from '../src/store';
import { notFood } from '../src/nutrition';

/** An in memory stand in for KV, close enough that the code under test cannot tell. */
class FakeStore implements Store {
  readonly values = new Map<string, string>();

  async get(key: string): Promise<string | null> {
    return this.values.get(key) ?? null;
  }

  async put(key: string, value: string): Promise<void> {
    this.values.set(key, value);
  }
}

describe('imageKey', () => {
  it('gives the same photo the same key, which is what makes the cache work', async () => {
    expect(await imageKey('abc')).toBe(await imageKey('abc'));
  });

  it('gives different photos different keys', async () => {
    expect(await imageKey('abc')).not.toBe(await imageKey('abd'));
  });

  it('is a hash, so the key carries nothing about who took the photo', async () => {
    expect(await imageKey('abc')).toMatch(/^dish:[0-9a-f]{64}$/);
  });
});

describe('the result cache', () => {
  let store: FakeStore;

  beforeEach(() => {
    store = new FakeStore();
  });

  it('returns nothing for a dish nobody has scanned', async () => {
    expect(await readCached(store, 'dish:missing')).toBeNull();
  });

  it('reads back what was written', async () => {
    const result = { ...notFood(), is_food: true, dish_name: 'Ugali' };
    await writeCached(store, 'dish:1', result);

    expect(await readCached(store, 'dish:1')).toEqual(result);
  });

  it('treats a corrupt entry as a miss rather than failing the scan', async () => {
    await store.put('dish:1', 'not json');

    expect(await readCached(store, 'dish:1')).toBeNull();
  });
});

describe('the daily quota', () => {
  let store: FakeStore;
  const NOW = Date.UTC(2026, 8, 20, 12, 0, 0);

  beforeEach(() => {
    store = new FakeStore();
  });

  it('allows the first scan of the day', async () => {
    const result = await consumeQuota(store, 'user-1', 3, NOW);

    expect(result.allowed).toBe(true);
    expect(result.used).toBe(1);
  });

  it('counts each scan against the limit', async () => {
    await consumeQuota(store, 'user-1', 3, NOW);
    await consumeQuota(store, 'user-1', 3, NOW);
    const third = await consumeQuota(store, 'user-1', 3, NOW);

    expect(third.used).toBe(3);
    expect(third.allowed).toBe(true);
  });

  it('refuses once the limit is reached', async () => {
    for (let i = 0; i < 3; i += 1) await consumeQuota(store, 'user-1', 3, NOW);
    const fourth = await consumeQuota(store, 'user-1', 3, NOW);

    expect(fourth.allowed).toBe(false);
    expect(fourth.used).toBe(3);
  });

  it('does not charge a refused scan against the counter again', async () => {
    for (let i = 0; i < 4; i += 1) await consumeQuota(store, 'user-1', 3, NOW);

    expect(await store.get(quotaKey('user-1', NOW))).toBe('3');
  });

  it('counts each user separately', async () => {
    for (let i = 0; i < 3; i += 1) await consumeQuota(store, 'user-1', 3, NOW);
    const other = await consumeQuota(store, 'user-2', 3, NOW);

    expect(other.allowed).toBe(true);
  });

  it('starts a fresh count on the next day', async () => {
    for (let i = 0; i < 3; i += 1) await consumeQuota(store, 'user-1', 3, NOW);
    const tomorrow = await consumeQuota(store, 'user-1', 3, NOW + 24 * 60 * 60 * 1000);

    expect(tomorrow.allowed).toBe(true);
    expect(tomorrow.used).toBe(1);
  });

  it('keys the day in UTC, so the quota cannot be reset by changing time zone', () => {
    const morningInNairobi = Date.UTC(2026, 8, 20, 22, 0, 0);
    const laterSameUtcDay = Date.UTC(2026, 8, 20, 23, 59, 0);

    expect(quotaKey('user-1', morningInNairobi)).toBe(quotaKey('user-1', laterSameUtcDay));
  });
});
