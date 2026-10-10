import { beforeEach, describe, expect, it, vi } from 'vitest';

const verifyIdToken = vi.fn();
const estimateNutrition = vi.fn();

vi.mock('../src/auth', async () => {
  const actual = await vi.importActual<typeof import('../src/auth')>('../src/auth');
  return { ...actual, verifyIdToken };
});

vi.mock('../src/nutrition', async () => {
  const actual =
    await vi.importActual<typeof import('../src/nutrition')>('../src/nutrition');
  return { ...actual, estimateNutrition };
});

const { default: worker } = await import('../src/index');
const { notFood } = await import('../src/nutrition');

class FakeKv {
  readonly values = new Map<string, string>();
  async get(key: string) {
    return this.values.get(key) ?? null;
  }
  async put(key: string, value: string) {
    this.values.set(key, value);
  }
}

function envWith(kv: FakeKv, overrides: Record<string, string> = {}) {
  return {
    SCANNER: kv,
    FIREBASE_PROJECT_ID: 'kulapro-61969',
    ANTHROPIC_MODEL: 'claude-opus-5',
    ANTHROPIC_API_KEY: 'test-key',
    DAILY_SCAN_LIMIT: '2',
    ...overrides,
  } as never;
}

function scanRequest(body: unknown, token = 'a.token.here') {
  return new Request('https://scanner.example/scan', {
    method: 'POST',
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

const A_DISH = { ...notFood(), is_food: true, dish_name: 'Ugali', calories_kcal: 780 };

describe('the scan endpoint', () => {
  let kv: FakeKv;

  beforeEach(() => {
    kv = new FakeKv();
    verifyIdToken.mockReset().mockResolvedValue({ uid: 'user-1' });
    estimateNutrition.mockReset().mockResolvedValue(A_DISH);
  });

  it('refuses a request with no token, before spending anything', async () => {
    const response = await worker.fetch(
      new Request('https://scanner.example/scan', { method: 'POST', body: '{}' }),
      envWith(kv),
    );

    expect(response.status).toBe(401);
    expect(estimateNutrition).not.toHaveBeenCalled();
  });

  it('refuses a token that does not verify', async () => {
    const { AuthError } = await import('../src/auth');
    verifyIdToken.mockRejectedValue(new AuthError('nope'));

    const response = await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));

    expect(response.status).toBe(401);
    expect(estimateNutrition).not.toHaveBeenCalled();
  });

  it('scans a photo and returns the estimate', async () => {
    const response = await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    const body = (await response.json()) as Record<string, unknown>;

    expect(response.status).toBe(200);
    expect(body.dish_name).toBe('Ugali');
    expect(body.cached).toBe(false);
  });

  it('serves a repeat of the same photo from the cache, with no model call', async () => {
    await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    estimateNutrition.mockClear();

    const response = await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    const body = (await response.json()) as Record<string, unknown>;

    expect(body.cached).toBe(true);
    expect(estimateNutrition).not.toHaveBeenCalled();
  });

  it('does not charge a cached scan against the daily limit', async () => {
    await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));

    // One model call, so one scan counted, despite three requests.
    const counters = [...kv.values.entries()].filter(([key]) => key.startsWith('quota:'));
    expect(counters).toHaveLength(1);
    expect(counters[0]?.[1]).toBe('1');
  });

  it('stops a user once they have used their day', async () => {
    await worker.fetch(scanRequest({ image: 'one' }), envWith(kv));
    await worker.fetch(scanRequest({ image: 'two' }), envWith(kv));

    const response = await worker.fetch(scanRequest({ image: 'three' }), envWith(kv));
    const body = (await response.json()) as { error: { code: string } };

    expect(response.status).toBe(429);
    expect(body.error.code).toBe('daily_limit_reached');
  });

  it('never returns macros for something that is not food', async () => {
    estimateNutrition.mockResolvedValue({
      ...notFood(),
      is_food: false,
      dish_name: 'a stapler',
      calories_kcal: 450,
    });

    const response = await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    const body = (await response.json()) as Record<string, unknown>;

    expect(body.is_food).toBe(false);
    expect(body.calories_kcal).toBe(0);
  });

  it('does not cache a non-food photo, which nobody scans twice', async () => {
    estimateNutrition.mockResolvedValue({ ...notFood(), is_food: false });

    await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));

    expect([...kv.values.keys()].some((key) => key.startsWith('dish:'))).toBe(false);
  });

  it('refuses an image that arrives without downscaling', async () => {
    const huge = 'x'.repeat(3 * 1024 * 1024);

    const response = await worker.fetch(scanRequest({ image: huge }), envWith(kv));
    const body = (await response.json()) as { error: { code: string } };

    expect(response.status).toBe(413);
    expect(body.error.code).toBe('image_too_large');
    expect(estimateNutrition).not.toHaveBeenCalled();
  });

  it('refuses a media type the vision API does not take', async () => {
    const response = await worker.fetch(
      scanRequest({ image: 'abc', mediaType: 'image/gif' }),
      envWith(kv),
    );

    expect(response.status).toBe(415);
  });

  it('refuses a request with no image at all', async () => {
    const response = await worker.fetch(scanRequest({}), envWith(kv));

    expect(response.status).toBe(400);
  });

  it('turns a model failure into something the user can act on', async () => {
    estimateNutrition.mockRejectedValue(new Error('upstream exploded'));

    const response = await worker.fetch(scanRequest({ image: 'abc' }), envWith(kv));
    const body = (await response.json()) as { error: { code: string; message: string } };

    expect(response.status).toBe(502);
    // The model's own error text is for the logs, never for the person holding the phone.
    expect(body.error.message).not.toContain('exploded');
  });

  it('answers nothing outside the scan path', async () => {
    const response = await worker.fetch(
      new Request('https://scanner.example/', { method: 'POST' }),
      envWith(kv),
    );

    expect(response.status).toBe(404);
  });

  it('refuses a GET, so a scan cannot be triggered by a link', async () => {
    const response = await worker.fetch(
      new Request('https://scanner.example/scan'),
      envWith(kv),
    );

    expect(response.status).toBe(405);
  });
});
