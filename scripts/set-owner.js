#!/usr/bin/env node
/**
 * Grants a user ownership of a restaurant.
 *
 * Ownership is stored as Firebase Auth custom claims, not as a field on the user's Firestore
 * document. A user can write their own profile document, so a role kept there could be
 * raised by the client; claims are signed by Firebase and are the only thing security rules
 * can safely trust.
 *
 *   export ACCESS_TOKEN=$(gcloud auth print-access-token)
 *   node scripts/set-owner.js owner@example.com the-bistro
 *
 * The user must sign out and back in, or the app must call getIdToken(true), before the new
 * claim appears in their token.
 */
const PROJECT_ID = process.env.GCLOUD_PROJECT || 'kulapro-61969';
const TOKEN = process.env.ACCESS_TOKEN;
const [, , email, ...restaurantIds] = process.argv;

if (!TOKEN) {
  console.error('ACCESS_TOKEN is required: export ACCESS_TOKEN=$(gcloud auth print-access-token)');
  process.exit(1);
}
if (!email || restaurantIds.length === 0) {
  console.error('Usage: node scripts/set-owner.js <email> <restaurantId> [moreRestaurantIds...]');
  process.exit(1);
}

const IDENTITY = `https://identitytoolkit.googleapis.com/v1/projects/${PROJECT_ID}`;

async function call(path, body) {
  const res = await fetch(`${IDENTITY}${path}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      // User credentials need an explicit quota project for this API.
      'x-goog-user-project': PROJECT_ID,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${path}: ${res.status} ${text}`);
  return JSON.parse(text || '{}');
}

async function main() {
  const lookup = await call('/accounts:lookup', { email: [email] });
  const user = lookup.users && lookup.users[0];
  if (!user) {
    throw new Error(`No account found for ${email}. Have them sign up first.`);
  }

  const existing = user.customAttributes ? JSON.parse(user.customAttributes) : {};
  const claims = {
    ...existing,
    role: 'OWNER',
    managedRestaurants: Array.from(
      new Set([...(existing.managedRestaurants || []), ...restaurantIds]),
    ),
  };

  await call('/accounts:update', {
    localId: user.localId,
    customAttributes: JSON.stringify(claims),
  });

  console.log(`${email} now manages: ${claims.managedRestaurants.join(', ')}`);
  console.log('They must sign out and back in for the change to take effect.');
}

main().catch((e) => {
  console.error('Failed:', e.message);
  process.exit(1);
});
