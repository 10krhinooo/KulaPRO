#!/usr/bin/env node
/**
 * Makes a user a platform reviewer.
 *
 * This is the one grant the app cannot make, and deliberately so. A reviewer can hand any
 * restaurant to anyone from inside the app, so the right to review has to come from someone
 * who already has access to the project itself. Run it once, for yourself.
 *
 *   export ACCESS_TOKEN=$(gcloud auth print-access-token)
 *   node scripts/set-reviewer.js you@example.com
 *
 * Pass --revoke to take it away again:
 *
 *   node scripts/set-reviewer.js you@example.com --revoke
 *
 * The user must sign out and back in, or the app must refresh their token, before the
 * change appears. KulaPro refreshes on sign-in, so signing out and in is enough.
 */
const PROJECT_ID = process.env.GCLOUD_PROJECT || 'kulapro-61969';
const TOKEN = process.env.ACCESS_TOKEN;
const args = process.argv.slice(2);
const revoke = args.includes('--revoke');
const email = args.find((a) => !a.startsWith('--'));

if (!TOKEN) {
  console.error('ACCESS_TOKEN is required: export ACCESS_TOKEN=$(gcloud auth print-access-token)');
  process.exit(1);
}
if (!email) {
  console.error('Usage: node scripts/set-reviewer.js <email> [--revoke]');
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
    throw new Error(`No account found for ${email}. Have them sign up in the app first.`);
  }

  const existing = user.customAttributes ? JSON.parse(user.customAttributes) : {};
  const claims = { ...existing };
  if (revoke) {
    delete claims.role;
  } else {
    claims.role = 'PLATFORM_ADMIN';
  }

  await call('/accounts:update', {
    localId: user.localId,
    customAttributes: JSON.stringify(claims),
  });

  console.log(
    revoke
      ? `${email} is no longer a reviewer.`
      : `${email} can now review ownership requests.`,
  );
  console.log('They must sign out and back in for the change to take effect.');
}

main().catch((e) => {
  console.error('Failed:', e.message);
  process.exit(1);
});
