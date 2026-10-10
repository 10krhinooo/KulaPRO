// Walks the whole ownership flow against the emulator, using the deployed rules file:
// a diner asks, cannot grant it to themselves, a reviewer approves, and the diner can
// then manage the restaurant but not hand it on.
import { after, before, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { addDoc, collection, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore';

let env;
const DINER = 'diner-1';

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'kulapro-e2e',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8') },
  });
  await env.withSecurityRulesDisabled(async (c) => {
    await setDoc(doc(c.firestore(), 'restaurants', 'the-bistro'), { name: 'The Bistro', ownerUserId: '' });
  });
});
after(async () => env.cleanup());

const diner = () => env.authenticatedContext(DINER).firestore();
const reviewer = () => env.authenticatedContext('rev', { role: 'PLATFORM_ADMIN' }).firestore();

describe('claim flow end to end', () => {
  let requestId;

  it('the diner asks', async () => {
    const ref = await assertSucceeds(addDoc(collection(diner(), 'ownershipRequests'), {
      userId: DINER, userEmail: 'd@e.com', type: 'CLAIM',
      restaurantId: 'the-bistro', restaurantName: 'The Bistro',
      role: 'Owner', contactPhone: '+254700', evidence: 'licence',
      status: 'PENDING', reviewedBy: '', reviewNote: '',
    }));
    requestId = ref.id;
  });

  it('the diner cannot manage the restaurant yet', async () => {
    await assertFails(updateDoc(doc(diner(), 'restaurants', 'the-bistro'), { name: 'Mine' }));
  });

  it('the diner cannot approve themselves', async () => {
    await assertFails(updateDoc(doc(diner(), 'ownershipRequests', requestId), { status: 'APPROVED' }));
  });

  it('the reviewer sees it waiting', async () => {
    const snap = await assertSucceeds(
      getDocs(query(collection(reviewer(), 'ownershipRequests'), where('status', '==', 'PENDING'))),
    );
    assert.equal(snap.size, 1);
  });

  it('the reviewer approves, which grants the restaurant', async () => {
    await assertSucceeds(updateDoc(doc(reviewer(), 'restaurants', 'the-bistro'), { ownerUserId: DINER }));
    await assertSucceeds(updateDoc(doc(reviewer(), 'ownershipRequests', requestId), {
      status: 'APPROVED', reviewedBy: 'rev',
    }));
  });

  it('the diner can now run the restaurant', async () => {
    await assertSucceeds(updateDoc(doc(diner(), 'restaurants', 'the-bistro'), { name: 'The Bistro' }));
    await assertSucceeds(setDoc(doc(diner(), 'restaurants/the-bistro/menuItems/m1'), {
      restaurantId: 'the-bistro', name: 'Steak', priceCents: 1000,
    }));
  });

  it('the diner still cannot hand it to anyone else', async () => {
    await assertFails(updateDoc(doc(diner(), 'restaurants', 'the-bistro'), { ownerUserId: 'someone' }));
  });

  it('the diner still cannot take a different restaurant', async () => {
    await env.withSecurityRulesDisabled(async (c) => {
      await setDoc(doc(c.firestore(), 'restaurants', 'other'), { name: 'Other' });
    });
    await assertFails(updateDoc(doc(diner(), 'restaurants', 'other'), { name: 'Mine now' }));
  });

  it('the request cannot be decided twice', async () => {
    await assertFails(updateDoc(doc(reviewer(), 'ownershipRequests', requestId), {
      status: 'REJECTED', reviewedBy: 'rev',
    }));
  });

  it('the diner can read the outcome', async () => {
    const snap = await assertSucceeds(getDoc(doc(diner(), 'ownershipRequests', requestId)));
    assert.equal(snap.data().status, 'APPROVED');
  });
});
