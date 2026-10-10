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
  if (value.__time) {
    return { timestampValue: value.value };
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
const reviews = require('./seed-reviews.json');

/** Seconds since the epoch, some whole number of days ago. */
function daysAgo(days) {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
}

async function main() {
  console.log(`Seeding ${restaurants.length} restaurants into ${PROJECT_ID}`);
  // floorPlan is pulled out here rather than written. It is a generator spec for the tables
  // subcollection below, not a field of the restaurant, and writing it put a key on every
  // document that the client has no model for. Firestore logged a warning per listing per
  // load for it.
  for (const { id, menu, location, floorPlan, ...rest } of restaurants) {
    await writeDoc(`restaurants/${id}`, {
      averageRating: 0,
      reviewCount: 0,
      // A restaurant that keeps its own hours overrides the default. Several close on a
      // Monday or do not open for lunch, which is what makes the availability screen worth
      // looking at during a demo.
      openingHours,
      ...rest,
      location: { __geo: true, lat: location[0], lng: location[1] },
      // Photography is not seeded. The app falls back to a cuisine matched image it ships
      // with, so a listing without a photo still looks like a restaurant.
      imageUrl: '',
      geohash: '',
    });
    for (const item of menu) {
      const itemId = item.name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      await writeDoc(`restaurants/${id}/menuItems/${itemId}`, {
        nutrition: null,
        ...item,
        restaurantId: id,
        currency: 'KES',
        imageUrl: '',
      });
    }
    console.log(`  ${rest.name}: ${menu.length} menu items`);
  }

  // Floor plans. Generated rather than hand listed: the shape of a room is a few numbers
  // (how many of each size, in which zone), and writing out sixty table documents by hand
  // would be noise. A restaurant that opts out simply gets no tables and books on seats.
  let tableCount = 0;
  for (const restaurant of restaurants) {
    const plan = restaurant.floorPlan;
    if (!plan) continue;
    let index = 0;
    for (const zone of plan) {
      for (let row = 0; row < zone.rows; row += 1) {
        for (let column = 0; column < zone.columns; column += 1) {
          index += 1;
          const id = `t${String(index).padStart(2, '0')}`;
          await writeDoc(`restaurants/${restaurant.id}/tables/${id}`, {
            restaurantId: restaurant.id,
            label: `${zone.prefix}${row * zone.columns + column + 1}`,
            seats: zone.seats,
            zone: zone.name,
            row,
            column,
          });
          tableCount += 1;
        }
      }
    }
  }
  console.log(`  ${tableCount} tables`);

  // Seeded reviews carry no reservationId. Security rules require one for a client write,
  // but this script writes with an owner token, so these stand in for the reviews real
  // diners would have left. They exist so the detail screen has something to show.
  let count = 0;
  for (const review of reviews) {
    await writeDoc(`restaurants/${review.restaurantId}/reviews/${review.id}`, {
      restaurantId: review.restaurantId,
      userId: `seed-${review.id}`,
      authorName: review.authorName,
      reservationId: '',
      rating: review.rating,
      comment: review.comment,
      createdAt: { __time: true, value: daysAgo(review.daysAgo) },
      ownerReply: review.ownerReply || '',
    });
    count += 1;
  }
  console.log(`  ${count} reviews`);
  console.log('Done.');
}

main().catch((e) => {
  console.error('Seed failed:', e.message);
  if (e.cause) console.error('Cause:', e.cause.code || e.cause.message, e.cause);
  process.exit(1);
});
