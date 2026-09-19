#!/usr/bin/env node
/**
 * Seeds restaurants and menu items over the Firestore REST API.
 *
 * A companion to seed-firestore.js for machines that have a gcloud login but no application
 * default credentials, which is the common case on a developer laptop:
 *
 *   ACCESS_TOKEN=$(gcloud auth print-access-token) node scripts/seed-rest.js
 *
 * Idempotent: documents use fixed ids, so re-running updates rather than duplicating.
 */
const PROJECT_ID = process.env.GCLOUD_PROJECT || 'kulapro-61969';
const TOKEN = process.env.ACCESS_TOKEN;

if (!TOKEN) {
  console.error('ACCESS_TOKEN is required. Run: export ACCESS_TOKEN=$(gcloud auth print-access-token)');
  process.exit(1);
}

const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents`;

/** Converts a plain JS value into the Firestore REST typed-value envelope. */
function toValue(value) {
  if (value === null || value === undefined) return { nullValue: null };
  if (typeof value === 'string') return { stringValue: value };
  if (typeof value === 'boolean') return { booleanValue: value };
  if (typeof value === 'number') {
    return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  }
  if (Array.isArray(value)) {
    return { arrayValue: { values: value.map(toValue) } };
  }
  if (value.__geo) {
    return { geoPointValue: { latitude: value.lat, longitude: value.lng } };
  }
  return { mapValue: { fields: toFields(value) } };
}

function toFields(obj) {
  return Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, toValue(v)]));
}

async function writeDoc(path, data) {
  const res = await fetch(`${BASE}/${path}`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: toFields(data) }),
  });
  if (!res.ok) {
    throw new Error(`${path}: ${res.status} ${await res.text()}`);
  }
}

const WEEKDAY = '12:00-22:00';
const WEEKEND = '11:00-23:00';
const openingHours = {
  monday: WEEKDAY,
  tuesday: WEEKDAY,
  wednesday: WEEKDAY,
  thursday: WEEKDAY,
  friday: WEEKEND,
  saturday: WEEKEND,
  sunday: '11:00-21:00',
};

const restaurants = require('./seed-data.json');

async function main() {
  console.log(`Seeding ${restaurants.length} restaurants into ${PROJECT_ID}`);
  for (const { id, menu, location, ...rest } of restaurants) {
    await writeDoc(`restaurants/${id}`, {
      ...rest,
      location: { __geo: true, lat: location[0], lng: location[1] },
      imageUrl: '',
      geohash: '',
      averageRating: 0,
      reviewCount: 0,
      openingHours,
    });
    for (const item of menu) {
      const itemId = item.name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      await writeDoc(`restaurants/${id}/menuItems/${itemId}`, {
        ...item,
        restaurantId: id,
        currency: 'KES',
        imageUrl: '',
        nutrition: null,
      });
    }
    console.log(`  ${rest.name}: ${menu.length} menu items`);
  }
  console.log('Done.');
}

main().catch((e) => {
  console.error('Seed failed:', e.message);
  if (e.cause) console.error('Cause:', e.cause.code || e.cause.message, e.cause);
  process.exit(1);
});
