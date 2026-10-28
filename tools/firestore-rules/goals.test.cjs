const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc } = require('firebase/firestore');

let env;
const goalId = 'goal-emergency-fund';
const baseGoal = (extra = {}) => ({
  id: goalId,
  userId: 'alice',
  name: 'Emergency Fund',
  description: 'Six months of living expenses',
  targetMinor: 500000,
  currency: 'LKR',
  targetDateEpochDay: 21000,
  createdAt: 10,
  updatedAt: 20,
  ...extra
});

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-clarity-goals',
    firestore: {
      host: '127.0.0.1',
      port: 8086,
      rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8')
    }
  });
});

after(async () => {
  if (env) await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
});

const db = uid => env.authenticatedContext(uid).firestore();
const goalRef = (uid, owner = 'alice', id = goalId) => doc(db(uid), `users/${owner}/goals/${id}`);

test('owner can create, read, and update goal details; other users, unauthenticated, and deletes are denied', async () => {
  await assertSucceeds(setDoc(goalRef('alice'), baseGoal()));
  await assertSucceeds(getDoc(goalRef('alice')));
  await assertSucceeds(getDocs(collection(db('alice'), 'users/alice/goals')));
  await assertSucceeds(updateDoc(goalRef('alice'), {
    name: 'Updated Emergency Fund',
    description: 'Updated description',
    targetMinor: 600000,
    targetDateEpochDay: 21500,
    updatedAt: 25
  }));

  await assertFails(getDoc(goalRef('bob')));
  await assertFails(setDoc(goalRef('bob'), baseGoal()));
  await assertFails(updateDoc(goalRef('bob'), { name: 'Hijack', updatedAt: 26 }));
  await assertFails(deleteDoc(goalRef('bob')));
  await assertFails(deleteDoc(goalRef('alice')));
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(), 'users/alice/goals')));
});

test('goal validation enforces exact 9-field schema, rejects savedMinor/status/pendingSync, and blocks immutable field edits', async () => {
  await assertSucceeds(setDoc(goalRef('alice'), baseGoal()));

  const invalidUpdates = [
    { userId: 'bob' },
    { id: 'different-id' },
    { currency: 'USD' },
    { createdAt: 15 },
    { updatedAt: 19 },
    { name: '' },
    { name: '   ' },
    { name: 'x'.repeat(101) },
    { description: 'x'.repeat(501) },
    { targetMinor: 0 },
    { targetMinor: -100 },
    { currency: 'ZZZ' },
    { savedMinor: 10000 },
    { status: 'ACTIVE' },
    { pendingSync: true }
  ];

  for (const invalid of invalidUpdates) {
    await assertFails(updateDoc(goalRef('alice'), {
      ...invalid,
      ...(invalid.updatedAt === undefined ? { updatedAt: 30 } : {})
    }));
  }

  const charlieDb = db('charlie');
  const invalidCreates = [
    { userId: 'alice' },
    { id: 'wrong-id' },
    { name: '' },
    { name: '   ' },
    { name: 'x'.repeat(101) },
    { description: 'x'.repeat(501) },
    { targetMinor: 0 },
    { targetMinor: -1 },
    { currency: 'XYZ' },
    { createdAt: -1 },
    { updatedAt: 5 },
    { savedMinor: 0 },
    { status: 'ACTIVE' },
    { pendingSync: false }
  ];

  for (const invalid of invalidCreates) {
    await assertFails(setDoc(doc(charlieDb, `users/charlie/goals/${goalId}`), {
      ...baseGoal({ userId: 'charlie' }),
      ...invalid
    }));
  }

  for (const field of Object.keys(baseGoal())) {
    const missing = baseGoal({ userId: 'charlie' });
    delete missing[field];
    await assertFails(setDoc(doc(charlieDb, `users/charlie/goals/${goalId}`), missing));
  }
});
