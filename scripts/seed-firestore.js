#!/usr/bin/env node
/**
 * Seeds restaurants and menu items.
 *
 * Uses the Admin SDK, which bypasses security rules, so run it with credentials you trust:
 *   FIRESTORE_EMULATOR_HOST=localhost:8080 node scripts/seed-firestore.js   (emulator)
 *   GOOGLE_APPLICATION_CREDENTIALS=key.json node scripts/seed-firestore.js  (real project)
 *
 * Idempotent: restaurants use fixed ids, so re-running updates rather than duplicating.
 */
const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getFirestore, GeoPoint } = require('firebase-admin/firestore');

const PROJECT_ID = process.env.GCLOUD_PROJECT || 'kulapro-61969';
const usingEmulator = Boolean(process.env.FIRESTORE_EMULATOR_HOST);

initializeApp(
  usingEmulator
    ? { projectId: PROJECT_ID }
    : { credential: applicationDefault(), projectId: PROJECT_ID },
);

const db = getFirestore();

const WEEKDAY = '12:00-22:00';
const WEEKEND = '11:00-23:00';
const standardHours = {
  monday: WEEKDAY,
  tuesday: WEEKDAY,
  wednesday: WEEKDAY,
  thursday: WEEKDAY,
  friday: WEEKEND,
  saturday: WEEKEND,
  sunday: '11:00-21:00',
};

const restaurants = [
  {
    id: 'the-bistro',
    name: 'The Bistro',
    description: 'Neighbourhood European cooking, long lunches, short menu.',
    cuisine: 'European',
    priceBand: 3,
    address: 'Westlands, Nairobi',
    phone: '+254700000001',
    location: new GeoPoint(-1.2649, 36.8038),
    capacityPerSlot: 24,
    slotDurationMinutes: 90,
    menu: [
      { name: 'Steak frites', description: 'Sirloin, peppercorn sauce, thin fries', priceCents: 185000, category: 'Mains' },
      { name: 'French onion soup', description: 'Slow onions, gruyere crouton', priceCents: 75000, category: 'Starters' },
      { name: 'Creme brulee', description: 'Vanilla custard, burnt sugar', priceCents: 65000, category: 'Desserts' },
    ],
  },
  {
    id: 'sushi-den',
    name: 'Sushi Den',
    description: 'Counter seating, daily fish, omakase on request.',
    cuisine: 'Japanese',
    priceBand: 4,
    address: 'Kilimani, Nairobi',
    phone: '+254700000002',
    location: new GeoPoint(-1.2921, 36.7856),
    capacityPerSlot: 16,
    slotDurationMinutes: 120,
    menu: [
      { name: 'Omakase set', description: "Twelve pieces, chef's choice", priceCents: 420000, category: 'Sets' },
      { name: 'Salmon nigiri', description: 'Two pieces', priceCents: 60000, category: 'Nigiri' },
      { name: 'Miso soup', description: 'White miso, tofu, spring onion', priceCents: 35000, category: 'Sides' },
    ],
  },
  {
    id: 'the-grill',
    name: 'The Grill',
    description: 'Open fire, big plates, built for sharing.',
    cuisine: 'Grill',
    priceBand: 2,
    address: 'Karen, Nairobi',
    phone: '+254700000003',
    location: new GeoPoint(-1.3194, 36.7073),
    capacityPerSlot: 40,
    slotDurationMinutes: 90,
    menu: [
      { name: 'Nyama choma platter', description: 'Goat, beef, kachumbari, ugali', priceCents: 165000, category: 'Mains' },
      { name: 'Grilled tilapia', description: 'Whole fish, lemon, chilli', priceCents: 140000, category: 'Mains' },
      { name: 'Chapati', description: 'Two pieces', priceCents: 20000, category: 'Sides' },
    ],
  },
  {
    id: 'forno-pizza',
    name: 'Forno',
    description: 'Wood-fired Neapolitan pizza, sixty second bake.',
    cuisine: 'Italian',
    priceBand: 2,
    address: 'Lavington, Nairobi',
    phone: '+254700000004',
    location: new GeoPoint(-1.2785, 36.7669),
    capacityPerSlot: 32,
    slotDurationMinutes: 60,
    menu: [
      { name: 'Margherita', description: 'San Marzano, fior di latte, basil', priceCents: 95000, category: 'Pizza' },
      { name: 'Diavola', description: 'Spicy salami, chilli honey', priceCents: 115000, category: 'Pizza' },
      { name: 'Tiramisu', description: 'Espresso, mascarpone', priceCents: 55000, category: 'Desserts' },
    ],
  },
];

async function seed() {
  console.log(
    `Seeding ${restaurants.length} restaurants into ` +
      `${usingEmulator ? `emulator at ${process.env.FIRESTORE_EMULATOR_HOST}` : `project ${PROJECT_ID}`}`,
  );

  for (const { id, menu, ...restaurant } of restaurants) {
    await db.collection('restaurants').doc(id).set(
      {
        ...restaurant,
        imageUrl: '',
        geohash: '',
        averageRating: 0,
        reviewCount: 0,
        openingHours: standardHours,
      },
      { merge: true },
    );

    for (const item of menu) {
      const itemId = item.name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
      await db
        .collection('restaurants').doc(id)
        .collection('menuItems').doc(itemId)
        .set({ ...item, restaurantId: id, currency: 'KES', imageUrl: '', nutrition: null }, { merge: true });
    }
    console.log(`  ${restaurant.name}: ${menu.length} menu items`);
  }

  console.log('Done.');
}

seed().catch((error) => {
  console.error('Seed failed:', error.message);
  process.exit(1);
});
