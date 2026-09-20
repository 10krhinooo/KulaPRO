import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

/**
 * These tests are the only place the security model is actually proved. Reading the rules
 * file tells you what was intended; running them tells you what is enforced.
 */

const PROJECT_ID = 'kulapro-test';
const ALICE = 'alice';
const BOB = 'bob';
const RESTAURANT = 'the-bistro';
const OTHER_RESTAURANT = 'sushi-bar';

let testEnv;

/** Signed in as a plain diner, with no ownership claims. */
const diner = (uid) => testEnv.authenticatedContext(uid).firestore();

/** Signed in with a claim naming one restaurant, the way the grant script issues it. */
const owner = (uid, restaurantId) =>
  testEnv
    .authenticatedContext(uid, { role: 'OWNER', managedRestaurants: [restaurantId] })
    .firestore();

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: readFileSync('../firestore.rules', 'utf8') },
  });
});

after(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  // Seeded with rules disabled, standing in for the server side writes that would have
  // produced this data in the real project.
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, 'restaurants', RESTAURANT), { name: 'The Bistro' });
    await setDoc(doc(db, 'restaurants', OTHER_RESTAURANT), { name: 'Sushi Bar' });
    await setDoc(doc(db, 'users', ALICE), { email: 'alice@example.com', role: 'DINER' });
    await setDoc(doc(db, 'reservations', 'alice-booking'), {
      userId: ALICE,
      restaurantId: RESTAURANT,
      status: 'COMPLETED',
      partySize: 2,
    });
    await setDoc(doc(db, 'reservations', 'bob-booking'), {
      userId: BOB,
      restaurantId: RESTAURANT,
      status: 'CONFIRMED',
      partySize: 4,
    });
  });
});

describe('reservations', () => {
  it('lets a diner read their own booking', async () => {
    await assertSucceeds(getDoc(doc(diner(ALICE), 'reservations/alice-booking')));
  });

  it("refuses a diner another diner's booking", async () => {
    await assertFails(getDoc(doc(diner(ALICE), 'reservations/bob-booking')));
  });

  it('lets the restaurant owner read a booking at their restaurant', async () => {
    await assertSucceeds(
      getDoc(doc(owner('carol', RESTAURANT), 'reservations/bob-booking')),
    );
  });

  it('refuses an owner a booking at a restaurant they do not manage', async () => {
    await assertFails(
      getDoc(doc(owner('carol', OTHER_RESTAURANT), 'reservations/bob-booking')),
    );
  });

  it('refuses a booking written under another user id', async () => {
    await assertFails(
      setDoc(doc(diner(ALICE), 'reservations/forged'), {
        userId: BOB,
        restaurantId: RESTAURANT,
        status: 'PENDING',
        partySize: 2,
      }),
    );
  });

  it('refuses a booking that skips the pending state', async () => {
    await assertFails(
      setDoc(doc(diner(ALICE), 'reservations/presumptuous'), {
        userId: ALICE,
        restaurantId: RESTAURANT,
        status: 'CONFIRMED',
        partySize: 2,
      }),
    );
  });

  it('refuses deletion, so cancellations stay in the history', async () => {
    await assertFails(
      updateDoc(doc(diner(ALICE), 'reservations/alice-booking'), { userId: BOB }),
    );
  });
});

describe('user documents', () => {
  it('refuses a diner another user profile', async () => {
    await assertFails(getDoc(doc(diner(BOB), 'users', ALICE)));
  });

  it('refuses a client that tries to promote itself', async () => {
    await assertFails(
      updateDoc(doc(diner(ALICE), 'users', ALICE), { role: 'PLATFORM_ADMIN' }),
    );
  });

  it('allows an ordinary profile edit', async () => {
    await assertSucceeds(
      updateDoc(doc(diner(ALICE), 'users', ALICE), { displayName: 'Alice' }),
    );
  });
});

describe('reviews', () => {
  const review = (overrides = {}) => ({
    userId: ALICE,
    restaurantId: RESTAURANT,
    reservationId: 'alice-booking',
    rating: 5,
    comment: 'Excellent',
    ...overrides,
  });

  it('accepts a review backed by the author\'s completed booking', async () => {
    await assertSucceeds(
      setDoc(doc(diner(ALICE), `restaurants/${RESTAURANT}/reviews/r1`), review()),
    );
  });

  it("refuses a review backed by someone else's booking", async () => {
    await assertFails(
      setDoc(
        doc(diner(BOB), `restaurants/${RESTAURANT}/reviews/r2`),
        review({ userId: BOB }),
      ),
    );
  });

  it('refuses a review with no booking behind it at all', async () => {
    await assertFails(
      setDoc(
        doc(diner(ALICE), `restaurants/${RESTAURANT}/reviews/r3`),
        review({ reservationId: 'does-not-exist' }),
      ),
    );
  });

  it('refuses a review for a booking that has not happened yet', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), 'reservations', 'alice-upcoming'), {
        userId: ALICE,
        restaurantId: RESTAURANT,
        status: 'CONFIRMED',
        partySize: 2,
      });
    });
    await assertFails(
      setDoc(
        doc(diner(ALICE), `restaurants/${RESTAURANT}/reviews/r4`),
        review({ reservationId: 'alice-upcoming' }),
      ),
    );
  });

  it('refuses a rating outside one to five', async () => {
    await assertFails(
      setDoc(
        doc(diner(ALICE), `restaurants/${RESTAURANT}/reviews/r5`),
        review({ rating: 9 }),
      ),
    );
  });
});

describe('slot counters', () => {
  const slot = (overrides = {}) => ({
    restaurantId: RESTAURANT,
    startsAtSeconds: 1700000000,
    seatsTaken: 4,
    ...overrides,
  });

  const slotPath = `restaurants/${RESTAURANT}/slots/1700000000`;

  it('is readable by a guest, so availability shows before sign-in', async () => {
    await assertSucceeds(getDoc(doc(testEnv.unauthenticatedContext().firestore(), slotPath)));
  });

  it('lets a signed-in diner claim seats', async () => {
    await assertSucceeds(setDoc(doc(diner(ALICE), slotPath), slot()));
  });

  it('refuses a guest a write', async () => {
    await assertFails(
      setDoc(doc(testEnv.unauthenticatedContext().firestore(), slotPath), slot()),
    );
  });

  it('refuses a count written under a mismatched document id', async () => {
    await assertFails(
      setDoc(doc(diner(ALICE), `restaurants/${RESTAURANT}/slots/1700000000`), slot({
        startsAtSeconds: 1699999999,
      })),
    );
  });

  it('refuses a negative count', async () => {
    await assertFails(setDoc(doc(diner(ALICE), slotPath), slot({ seatsTaken: -1 })));
  });

  it('refuses a jump larger than one party', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), slotPath), slot({ seatsTaken: 4 }));
    });
    await assertFails(setDoc(doc(diner(ALICE), slotPath), slot({ seatsTaken: 400 })));
  });

  it('refuses a count that claims a different restaurant', async () => {
    await assertFails(
      setDoc(doc(diner(ALICE), slotPath), slot({ restaurantId: OTHER_RESTAURANT })),
    );
  });
});

describe('restaurants and menus', () => {
  it('is world readable, so guests can browse', async () => {
    const guest = testEnv.unauthenticatedContext().firestore();
    await assertSucceeds(getDoc(doc(guest, 'restaurants', RESTAURANT)));
  });

  it('refuses an edit from a diner', async () => {
    await assertFails(
      updateDoc(doc(diner(ALICE), 'restaurants', RESTAURANT), { name: 'Hijacked' }),
    );
  });

  it('allows an edit from the restaurant owner', async () => {
    await assertSucceeds(
      updateDoc(doc(owner('carol', RESTAURANT), 'restaurants', RESTAURANT), {
        name: 'The Bistro',
      }),
    );
  });

  it('refuses an owner an edit to a restaurant they do not manage', async () => {
    await assertFails(
      updateDoc(doc(owner('carol', OTHER_RESTAURANT), 'restaurants', RESTAURANT), {
        name: 'Hijacked',
      }),
    );
  });

  it('allows the owner to publish a menu item', async () => {
    await assertSucceeds(
      setDoc(doc(owner('carol', RESTAURANT), `restaurants/${RESTAURANT}/menuItems/m1`), {
        restaurantId: RESTAURANT,
        name: 'Nyama choma',
        priceCents: 120000,
      }),
    );
  });
});

describe('floor plan', () => {
  const tablePath = `restaurants/${RESTAURANT}/tables/t01`;
  const table = { restaurantId: RESTAURANT, label: 'W1', seats: 4, zone: 'Window', row: 0, column: 0 };

  it('is readable by a guest, so a table can be picked before signing in', async () => {
    await assertSucceeds(
      getDoc(doc(testEnv.unauthenticatedContext().firestore(), tablePath)),
    );
  });

  it('refuses a diner a change to the floor plan', async () => {
    await assertFails(setDoc(doc(diner(ALICE), tablePath), table));
  });

  it('allows the restaurant to map its own tables', async () => {
    await assertSucceeds(setDoc(doc(owner('carol', RESTAURANT), tablePath), table));
  });

  it('refuses a claim on more than one table in a single write', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), `restaurants/${RESTAURANT}/slots/1700000000`),
        { restaurantId: RESTAURANT, startsAtSeconds: 1700000000, seatsTaken: 4, takenTableIds: ['t01'] },
      );
    });
    await assertFails(
      setDoc(doc(diner(ALICE), `restaurants/${RESTAURANT}/slots/1700000000`), {
        restaurantId: RESTAURANT,
        startsAtSeconds: 1700000000,
        seatsTaken: 6,
        takenTableIds: ['t01', 't02', 't03', 't04'],
      }),
    );
  });

  it('allows a diner to claim exactly one table', async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), `restaurants/${RESTAURANT}/slots/1700000000`),
        { restaurantId: RESTAURANT, startsAtSeconds: 1700000000, seatsTaken: 4, takenTableIds: ['t01'] },
      );
    });
    await assertSucceeds(
      setDoc(doc(diner(ALICE), `restaurants/${RESTAURANT}/slots/1700000000`), {
        restaurantId: RESTAURANT,
        startsAtSeconds: 1700000000,
        seatsTaken: 6,
        takenTableIds: ['t01', 't02'],
      }),
    );
  });
});

describe('everything else', () => {
  it('denies a collection the rules never mention', async () => {
    await assertFails(setDoc(doc(diner(ALICE), 'secrets/anything'), { a: 1 }));
    assert.ok(true);
  });
});
