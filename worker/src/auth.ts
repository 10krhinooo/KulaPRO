/**
 * Verifies a Firebase ID token, without the Admin SDK.
 *
 * The Admin SDK does not run on Workers, so the checks Firebase would do are done here
 * against Google's published signing certificates. Every one of them matters: skipping the
 * audience check would accept a token minted for a different Firebase project, and skipping
 * the signature check would accept a token anybody wrote.
 */

const CERT_URL =
  'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';

export interface VerifiedUser {
  uid: string;
}

export class AuthError extends Error {}

interface CachedCerts {
  keys: Record<string, CryptoKey>;
  expiresAt: number;
}

let certCache: CachedCerts | null = null;

/**
 * Google's current signing certificates, held until they expire.
 *
 * Cached in module scope, which on Workers lives as long as the isolate. Fetching them on
 * every scan would add a round trip to Google in front of every round trip to Anthropic.
 */
async function signingKeys(now: number): Promise<Record<string, CryptoKey>> {
  if (certCache && certCache.expiresAt > now) return certCache.keys;

  const response = await fetch(CERT_URL);
  if (!response.ok) throw new AuthError('Could not fetch Google signing certificates');

  const pemByKid = (await response.json()) as Record<string, string>;
  const keys: Record<string, CryptoKey> = {};
  for (const [kid, pem] of Object.entries(pemByKid)) {
    keys[kid] = await importCertificate(pem);
  }

  certCache = { keys, expiresAt: now + cacheLifetimeMillis(response) };
  return keys;
}

/** Honours Google's own cache header, so a rotated certificate is picked up on time. */
function cacheLifetimeMillis(response: Response): number {
  const header = response.headers.get('cache-control') ?? '';
  const maxAge = /max-age=(\d+)/.exec(header);
  const seconds = maxAge?.[1] ? Number(maxAge[1]) : 3600;
  return seconds * 1000;
}

/**
 * Pulls the public key out of an X.509 certificate.
 *
 * WebCrypto imports a SubjectPublicKeyInfo, not a certificate, so the SPKI has to be found
 * inside the DER. Rather than write an ASN.1 parser, the key is located by its algorithm
 * identifier, which is a fixed byte sequence for RSA.
 */
async function importCertificate(pem: string): Promise<CryptoKey> {
  const der = derFromPem(pem);
  const spki = extractSpki(der);
  return crypto.subtle.importKey(
    'spki',
    spki,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['verify'],
  );
}

function derFromPem(pem: string): Uint8Array {
  const body = pem
    .replace(/-----BEGIN CERTIFICATE-----/, '')
    .replace(/-----END CERTIFICATE-----/, '')
    .replace(/\s+/g, '');
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** The DER encoding of the rsaEncryption OID, which opens the SubjectPublicKeyInfo. */
const RSA_OID = [0x30, 0x0d, 0x06, 0x09, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01];

function extractSpki(der: Uint8Array): Uint8Array {
  for (let i = 0; i + RSA_OID.length < der.length; i += 1) {
    if (RSA_OID.every((byte, offset) => der[i + offset] === byte)) {
      // The SPKI is the SEQUENCE wrapping this OID. Walk back to its header, which is the
      // 0x30 two or four bytes earlier depending on the length encoding.
      const start = findSequenceStart(der, i);
      if (start !== null) return der.slice(start, der.length);
    }
  }
  throw new AuthError('Certificate did not contain an RSA public key');
}

function findSequenceStart(der: Uint8Array, oidAt: number): number | null {
  for (let back = 2; back <= 6; back += 1) {
    const candidate = oidAt - back;
    if (candidate >= 0 && der[candidate] === 0x30) return candidate;
  }
  return null;
}

function base64UrlToBytes(value: string): Uint8Array {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(padded + '='.repeat((4 - (padded.length % 4)) % 4));
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

interface TokenClaims {
  aud?: string;
  iss?: string;
  sub?: string;
  exp?: number;
  iat?: number;
}

/**
 * @throws AuthError if the token is missing, malformed, expired, unsigned by Google, or
 *   minted for a different Firebase project.
 */
export async function verifyIdToken(
  token: string,
  projectId: string,
  now: number = Date.now(),
): Promise<VerifiedUser> {
  const parts = token.split('.');
  if (parts.length !== 3) throw new AuthError('Malformed token');
  const [rawHeader, rawPayload, rawSignature] = parts as [string, string, string];

  const header = JSON.parse(new TextDecoder().decode(base64UrlToBytes(rawHeader))) as {
    kid?: string;
    alg?: string;
  };
  if (header.alg !== 'RS256') throw new AuthError('Unexpected signing algorithm');
  if (!header.kid) throw new AuthError('Token named no signing key');

  const keys = await signingKeys(now);
  const key = keys[header.kid];
  if (!key) throw new AuthError('Token was signed by an unknown key');

  const signed = new TextEncoder().encode(`${rawHeader}.${rawPayload}`);
  const valid = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    key,
    base64UrlToBytes(rawSignature),
    signed,
  );
  if (!valid) throw new AuthError('Signature did not verify');

  const claims = JSON.parse(
    new TextDecoder().decode(base64UrlToBytes(rawPayload)),
  ) as TokenClaims;
  assertClaims(claims, projectId, now);

  return { uid: claims.sub as string };
}

/** Exported so the claim rules can be tested without minting a signed token. */
export function assertClaims(claims: TokenClaims, projectId: string, now: number): void {
  const nowSeconds = Math.floor(now / 1000);
  if (claims.aud !== projectId) throw new AuthError('Token was issued for another project');
  if (claims.iss !== `https://securetoken.google.com/${projectId}`) {
    throw new AuthError('Token came from the wrong issuer');
  }
  if (!claims.sub) throw new AuthError('Token named no user');
  if (typeof claims.exp !== 'number' || claims.exp <= nowSeconds) {
    throw new AuthError('Token has expired');
  }
  if (typeof claims.iat !== 'number' || claims.iat > nowSeconds + 60) {
    throw new AuthError('Token is dated in the future');
  }
}
