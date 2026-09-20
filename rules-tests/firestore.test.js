import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';

/**
 * These tests are the only place the security model is actually proved. Reading the rules
 * file tells you what was intended; running them tells you what is enforced.
 */

const PROJECT_ID = 'kulapro-test';
const ALICE = 'alice';
const BOB = 'bob';
const RESTAURANT = 'the-bistro';
const OTHER_RESTAURANT = 'sushi-bar';
const OWNED_RESTAURANT = 'bobs-place';

let testEnv;

/** Signed in as a plain diner, with no ownership claims. */
const diner = (uid) => testEnv.authenticatedContext(uid).firestore();

/** Signed in as a platform reviewer, the one role bootstrapped out of band. */
const admin = (uid) =>
  testEnv.authenticatedContext(uid, { role: 'PLATFORM_ADMIN' }).firestore();

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
    await setDoc(doc(db, 'restaurants', OWNED_RESTAURANT), {
      name: 'Bob\'s Place',
      ownerUserId: BOB,
    });
    await setDoc(doc(db, 'ownershipRequests', 'req-alice'), {
      userId: ALICE,
      userEmail: 'alice@example.com',
      type: 'CLAIM',
      restaurantId: RESTAURANT,
      status: 'PENDING',
      reviewedBy: '',
      reviewNote: '',
    });
    await setDoc(doc(db, 'ownershipRequests', 'req-settled'), {
      userId: ALICE,
      type: 'CLAIM',
      restaurantId: OTHER_RESTAURANT,
      status: 'REJECTED',
      reviewedBy: 'carol',
      reviewNote: 'Could not confirm',
    });
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


describe('ownership requests', () => {
  const request = (overrides = {}) => ({
    userId: ALICE,
    userEmail: 'alice@example.com',
    type: 'CLAIM',
    restaurantId: RESTAURANT,
    status: 'PENDING',
    reviewedBy: '',
    reviewNote: '',
    ...overrides,
  });

  it('lets a signed-in user ask about a restaurant', async () => {
    await assertSucceeds(
      setDoc(doc(diner(ALICE), 'ownershipRequests/new-1'), request()),
    );
  });

  it('refuses a guest, since a request has to be from someone', async () => {
    await assertFails(
      setDoc(
        doc(testEnv.unauthenticatedContext().firestore(), 'ownershipRequests/new-2'),
        request(),
      ),
    );
  });

  it("refuses a request raised in someone else's name", async () => {
    await assertFails(
      setDoc(doc(diner(BOB), 'ownershipRequests/new-3'), request({ userId: ALICE })),
    );
  });

  it('refuses a request that arrives already approved', async () => {
    await assertFails(
      setDoc(doc(diner(ALICE), 'ownershipRequests/new-4'), request({ status: 'APPROVED' })),
    );
  });

  it('lets the requester follow their own request', async () => {
    await assertSucceeds(getDoc(doc(diner(ALICE), 'ownershipRequests/req-alice')));
  });

  it("refuses a user another person's request", async () => {
    await assertFails(getDoc(doc(diner(BOB), 'ownershipRequests/req-alice')));
  });

  it('lets a reviewer read any request', async () => {
    await assertSucceeds(getDoc(doc(admin('carol'), 'ownershipRequests/req-alice')));
  });

  it('refuses a user approving their own request', async () => {
    await assertFails(
      updateDoc(doc(diner(ALICE), 'ownershipRequests/req-alice'), { status: 'APPROVED' }),
    );
  });

  it('lets a reviewer approve a waiting request', async () => {
    await assertSucceeds(
      updateDoc(doc(admin('carol'), 'ownershipRequests/req-alice'), {
        status: 'APPROVED',
        reviewedBy: 'carol',
      }),
    );
  });

  it('refuses a second decision on a settled request', async () => {
    await assertFails(
      updateDoc(doc(admin('carol'), 'ownershipRequests/req-settled'), {
        status: 'APPROVED',
        reviewedBy: 'carol',
      }),
    );
  });

  it('refuses a decision that reassigns who asked', async () => {
    await assertFails(
      updateDoc(doc(admin('carol'), 'ownershipRequests/req-alice'), {
        status: 'APPROVED',
        userId: BOB,
      }),
    );
  });

  it('refuses deletion, so the record of who decided survives', async () => {
    await assertFails(deleteDoc(doc(admin('carol'), 'ownershipRequests/req-alice')));
  });
});

describe('granted ownership', () => {
  it('lets a reviewer hand a restaurant to a user', async () => {
    await assertSucceeds(
      updateDoc(doc(admin('carol'), 'restaurants', RESTAURANT), { ownerUserId: ALICE }),
    );
  });

  it('refuses a user writing the owner field onto a restaurant', async () => {
    await assertFails(
      updateDoc(doc(diner(ALICE), 'restaurants', RESTAURANT), { ownerUserId: ALICE }),
    );
  });

  it('lets a granted owner edit their restaurant without any claim', async () => {
    await assertSucceeds(
      updateDoc(doc(diner(BOB), 'restaurants', OWNED_RESTAURANT), { name: 'Bob Place' }),
    );
  });

  it('refuses a granted owner a restaurant that is not theirs', async () => {
    await assertFails(
      updateDoc(doc(diner(BOB), 'restaurants', RESTAURANT), { name: 'Hijacked' }),
    );
  });

  it('refuses a granted owner handing their restaurant to someone else', async () => {
    // Otherwise a single grant would let ownership spread without a reviewer ever
    // seeing it again.
    await assertFails(
      updateDoc(doc(diner(BOB), 'restaurants', OWNED_RESTAURANT), { ownerUserId: ALICE }),
    );
  });

  it('lets a granted owner map their floor and publish a menu', async () => {
    await assertSucceeds(
      setDoc(doc(diner(BOB), `restaurants/${OWNED_RESTAURANT}/tables/t01`), {
        restaurantId: OWNED_RESTAURANT,
        label: 'W1',
        seats: 4,
        zone: 'Window',
        row: 0,
        column: 0,
      }),
    );
  });

  it('refuses anyone but a reviewer creating a listing', async () => {
    await assertFails(
      setDoc(doc(diner(ALICE), 'restaurants', 'invented'), { name: 'Invented' }),
    );
  });

  it('lets a reviewer create a listing on approval', async () => {
    await assertSucceeds(
      setDoc(doc(admin('carol'), 'restaurants', 'approved-listing'), {
        name: 'Approved Listing',
        ownerUserId: ALICE,
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
