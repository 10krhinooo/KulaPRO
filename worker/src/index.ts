import { AuthError, verifyIdToken } from './auth';
import { estimateNutrition, sanitise } from './nutrition';
import { consumeQuota, imageKey, readCached, writeCached, type Store } from './store';

export interface Env {
  SCANNER: KVNamespace;
  FIREBASE_PROJECT_ID: string;
  ANTHROPIC_MODEL: string;
  ANTHROPIC_API_KEY: string;
  DAILY_SCAN_LIMIT: string;
}

/**
 * An image larger than this is refused rather than forwarded.
 *
 * Claude's vision resolution tops out around 1568px on the long edge, and the client
 * already downscales to that, where a JPEG lands well under a megabyte. Anything much
 * bigger is a client that stopped downscaling, and forwarding it would cost tokens without
 * buying accuracy.
 */
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

const ALLOWED_MEDIA_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

interface ScanBody {
  image?: unknown;
  mediaType?: unknown;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method !== 'POST') {
      return problem(405, 'method_not_allowed', 'Send a POST.');
    }
    if (new URL(request.url).pathname !== '/scan') {
      return problem(404, 'not_found', 'Nothing lives at that path.');
    }
    return handleScan(request, env);
  },
};

async function handleScan(request: Request, env: Env): Promise<Response> {
  // Identity first, before anything that costs money or writes state.
  const uid = await authenticate(request, env);
  if (typeof uid !== 'string') return uid;

  const body = (await safeJson(request)) as ScanBody | null;
  if (!body) return problem(400, 'bad_request', 'That was not JSON.');

  const image = typeof body.image === 'string' ? body.image : null;
  if (!image) {
    return problem(400, 'bad_request', 'Send the photo as base64 in an "image" field.');
  }
  if (image.length > MAX_IMAGE_BYTES) {
    return problem(
      413,
      'image_too_large',
      'That photo is too large. Take it again at a lower resolution.',
    );
  }

  const mediaType = typeof body.mediaType === 'string' ? body.mediaType : 'image/jpeg';
  if (!isAllowedMediaType(mediaType)) {
    return problem(415, 'unsupported_media', 'Send a JPEG, PNG or WebP.');
  }

  const store = env.SCANNER as unknown as Store;

  // The cache is checked before the quota, so looking at a dish someone has already scanned
  // costs the user nothing from their day and costs nobody anything at all.
  const key = await imageKey(image);
  const cached = await readCached(store, key);
  if (cached) return json({ ...cached, cached: true });

  const limit = Number(env.DAILY_SCAN_LIMIT ?? '25');
  const quota = await consumeQuota(store, uid, limit);
  if (!quota.allowed) {
    return problem(
      429,
      'daily_limit_reached',
      `You have used all ${quota.limit} scans for today. They reset at midnight UTC.`,
    );
  }

  let result;
  try {
    result = await estimateNutrition({
      imageBase64: image,
      mediaType,
      model: env.ANTHROPIC_MODEL || 'claude-opus-5',
      apiKey: env.ANTHROPIC_API_KEY,
    });
  } catch (error) {
    // The model's own error text is for us, not for the person holding the phone.
    console.error('scan failed', error);
    return problem(502, 'scan_failed', 'We could not read that photo. Try again.');
  }

  if (!result) {
    return problem(502, 'scan_failed', 'We could not read that photo. Try again.');
  }

  const safe = sanitise(result);
  // Non-food is not cached. It is almost always a mis-aimed camera rather than a dish
  // anyone will photograph twice, and caching it would fill the namespace with nothing.
  if (safe.is_food) await writeCached(store, key, safe);

  return json({ ...safe, cached: false });
}

/** @returns the uid, or the Response to send back instead. */
async function authenticate(request: Request, env: Env): Promise<string | Response> {
  const header = request.headers.get('authorization') ?? '';
  const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
  if (!token) {
    return problem(401, 'unauthenticated', 'Sign in to scan a dish.');
  }
  try {
    const user = await verifyIdToken(token, env.FIREBASE_PROJECT_ID);
    return user.uid;
  } catch (error) {
    if (error instanceof AuthError) {
      return problem(401, 'unauthenticated', 'Your session expired. Sign in again.');
    }
    throw error;
  }
}

function isAllowedMediaType(value: string): value is (typeof ALLOWED_MEDIA_TYPES)[number] {
  return (ALLOWED_MEDIA_TYPES as readonly string[]).includes(value);
}

async function safeJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

/**
 * An error the client can act on.
 *
 * [code] is for the app to branch on and [message] is for the person to read. Sending only
 * a message would make the client match on English; sending only a code would make it
 * invent its own wording.
 */
function problem(status: number, code: string, message: string): Response {
  return json({ error: { code, message } }, status);
}
