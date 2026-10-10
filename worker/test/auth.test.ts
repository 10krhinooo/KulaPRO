import { describe, expect, it } from 'vitest';
import { AuthError, assertClaims } from '../src/auth';

const PROJECT = 'kulapro-61969';
const NOW = Date.UTC(2026, 8, 20, 12, 0, 0);
const NOW_SECONDS = Math.floor(NOW / 1000);

function claims(overrides: Record<string, unknown> = {}) {
  return {
    aud: PROJECT,
    iss: `https://securetoken.google.com/${PROJECT}`,
    sub: 'user-1',
    exp: NOW_SECONDS + 3600,
    iat: NOW_SECONDS - 60,
    ...overrides,
  };
}

describe('assertClaims', () => {
  it('accepts a token this project issued to a signed in user', () => {
    expect(() => assertClaims(claims(), PROJECT, NOW)).not.toThrow();
  });

  it('rejects a token minted for another Firebase project', () => {
    expect(() => assertClaims(claims({ aud: 'someone-elses-app' }), PROJECT, NOW)).toThrow(
      AuthError,
    );
  });

  it('rejects a token from the wrong issuer even when the audience matches', () => {
    expect(() =>
      assertClaims(claims({ iss: 'https://evil.example/kulapro-61969' }), PROJECT, NOW),
    ).toThrow(AuthError);
  });

  it('rejects an expired token', () => {
    expect(() => assertClaims(claims({ exp: NOW_SECONDS - 1 }), PROJECT, NOW)).toThrow(
      AuthError,
    );
  });

  it('rejects a token that expires exactly now, rather than allowing it through', () => {
    expect(() => assertClaims(claims({ exp: NOW_SECONDS }), PROJECT, NOW)).toThrow(AuthError);
  });

  it('rejects a token dated in the future beyond clock skew', () => {
    expect(() => assertClaims(claims({ iat: NOW_SECONDS + 600 }), PROJECT, NOW)).toThrow(
      AuthError,
    );
  });

  it('tolerates a minute of clock skew, because clocks disagree', () => {
    expect(() => assertClaims(claims({ iat: NOW_SECONDS + 30 }), PROJECT, NOW)).not.toThrow();
  });

  it('rejects a token that names no user', () => {
    expect(() => assertClaims(claims({ sub: undefined }), PROJECT, NOW)).toThrow(AuthError);
  });

  it('rejects a token with no expiry at all', () => {
    expect(() => assertClaims(claims({ exp: undefined }), PROJECT, NOW)).toThrow(AuthError);
  });
});
