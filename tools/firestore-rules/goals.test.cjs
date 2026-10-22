const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc, writeBatch } = require('firebase/firestore');

let env;
const goalId = 'goal-emergency-fund';
const baseGoal = (extra = {}) => ({
  id: goalId,
  userId: 'alice',
  name: 'Emergency Fund',
  description: 'Six months of living expenses',
  targetMinor: 500000,
  savedMinor: 100000,
  currency: 'LKR',
  targetDateEpochDay: 21000,
  status: 'ACTIVE',
  createdAt: 10,
  updatedAt: 20,
  ...extra
});

const baseOp = (opId = 'op-1', extra = {}) => ({
  id: opId,
  userId: 'alice',
  goalId,
  type: 'ADD',
  amountMinor: 50000,
  createdAt: 30,
  ...extra
});

const baseAccount = (accountId = 'acc-lkr', extra = {}) => ({
  id: accountId,
  userId: 'alice',
  name: 'Main Savings',
  bankName: 'BOC',
  accountType: 'SAVINGS',
  currency: 'LKR',
  isDefault: true,
  createdAt: 5,
  updatedAt: 5,
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
const opRef = (uid, opId = 'op-1', owner = 'alice', id = goalId) =>
  doc(db(uid), `users/${owner}/goals/${id}/progress_operations/${opId}`);
const accountRef = (uid, accountId = 'acc-lkr', owner = 'alice') =>
  doc(db(uid), `users/${owner}/accounts/${accountId}`);

test('owner can create, read, and update goal details while preserving savedMinor; other users and unauthenticated denied', async () => {
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

test('goal validation rejects invalid amounts, status mismatch, bad currency, extra/missing fields, and immutable field changes', async () => {
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
    { targetMinor: 50000 }, // below savedMinor (100000)
    { status: 'COMPLETED' }, // savedMinor (100000) != targetMinor (500000)
    { status: 'INVALID' },
    { currency: 'ZZZ' },
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
    { savedMinor: -1 },
    { savedMinor: 600000 },
    { status: 'COMPLETED' },
    { savedMinor: 500000, status: 'ACTIVE' },
    { currency: 'XYZ' },
    { createdAt: -1 },
    { updatedAt: 5 },
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

test('savedMinor change requires a new matching progress operation in the same transaction; replay, update, and delete are denied', async () => {
  await assertSucceeds(setDoc(goalRef('alice'), baseGoal()));

  // Changing savedMinor directly without creating a matching operation is denied.
  await assertFails(updateDoc(goalRef('alice'), {
    savedMinor: 150000,
    updatedAt: 30
  }));

  // Creating an operation when parent goal does not exist is denied.
  await assertFails(setDoc(opRef('alice', 'op-orphan', 'alice', 'missing-goal'), {
    ...baseOp('op-orphan'),
    goalId: 'missing-goal'
  }));

  // Legacy ADD operation (without sourceAccountId) + goal savedMinor update in one atomic batch succeeds.
  const batchAdd = writeBatch(db('alice'));
  batchAdd.set(opRef('alice', 'op-add-1'), baseOp('op-add-1', { type: 'ADD', amountMinor: 50000, createdAt: 30 }));
  batchAdd.update(goalRef('alice'), {
    savedMinor: 150000,
    lastOperationId: 'op-add-1',
    updatedAt: 30
  });
  await assertSucceeds(batchAdd.commit());

  await assertSucceeds(getDoc(opRef('alice', 'op-add-1')));
  await assertFails(getDoc(opRef('bob', 'op-add-1')));

  // Operation update and delete are denied.
  await assertFails(updateDoc(opRef('alice', 'op-add-1'), { amountMinor: 60000 }));
  await assertFails(deleteDoc(opRef('alice', 'op-add-1')));

  // Operation replay (reusing op-add-1 to increment savedMinor again) is denied.
  const batchReplay = writeBatch(db('alice'));
  batchReplay.set(opRef('alice', 'op-add-1'), baseOp('op-add-1', { type: 'ADD', amountMinor: 50000, createdAt: 30 }));
  batchReplay.update(goalRef('alice'), {
    savedMinor: 200000,
    lastOperationId: 'op-add-1',
    updatedAt: 35
  });
  await assertFails(batchReplay.commit());

  // Mismatched ADD amount is denied.
  const batchMismatch = writeBatch(db('alice'));
  batchMismatch.set(opRef('alice', 'op-mismatch'), baseOp('op-mismatch', { type: 'ADD', amountMinor: 50000, createdAt: 40 }));
  batchMismatch.update(goalRef('alice'), {
    savedMinor: 250000,
    lastOperationId: 'op-mismatch',
    updatedAt: 40
  });
  await assertFails(batchMismatch.commit());

  // Valid SET operation reaching target transitions status to COMPLETED.
  const batchSetComplete = writeBatch(db('alice'));
  batchSetComplete.set(opRef('alice', 'op-set-complete'), baseOp('op-set-complete', { type: 'SET', amountMinor: 500000, createdAt: 50 }));
  batchSetComplete.update(goalRef('alice'), {
    savedMinor: 500000,
    status: 'COMPLETED',
    lastOperationId: 'op-set-complete',
    updatedAt: 50
  });
  await assertSucceeds(batchSetComplete.commit());

  // Valid SET operation downward reactivates goal to ACTIVE.
  const batchReactivate = writeBatch(db('alice'));
  batchReactivate.set(opRef('alice', 'op-set-reactivate'), baseOp('op-set-reactivate', { type: 'SET', amountMinor: 0, createdAt: 60 }));
  batchReactivate.update(goalRef('alice'), {
    savedMinor: 0,
    status: 'ACTIVE',
    lastOperationId: 'op-set-reactivate',
    updatedAt: 60
  });
  await assertSucceeds(batchReactivate.commit());
});

test('progress operations with sourceAccountId enforce ADD-only, owned account existence, currency match, and valid account ID', async () => {
  await assertSucceeds(setDoc(goalRef('alice'), baseGoal()));

  // Seed Alice's LKR account, Alice's USD account, and Bob's LKR account bypassing rules.
  await env.withSecurityRulesDisabled(async context => {
    const adminDb = context.firestore();
    await setDoc(doc(adminDb, 'users/alice/accounts/acc-lkr'), baseAccount('acc-lkr', { currency: 'LKR', isDefault: true }));
    await setDoc(doc(adminDb, 'users/alice/accounts/acc-usd'), baseAccount('acc-usd', { currency: 'USD', isDefault: false }));
    await setDoc(doc(adminDb, 'users/bob/accounts/acc-lkr'), baseAccount('acc-lkr', { userId: 'bob', currency: 'LKR', isDefault: true }));
  });

  // 1. Valid ADD with owned same-currency account succeeds.
  const validBatch = writeBatch(db('alice'));
  validBatch.set(opRef('alice', 'op-acc-ok'), baseOp('op-acc-ok', {
    type: 'ADD',
    amountMinor: 25000,
    sourceAccountId: 'acc-lkr',
    createdAt: 31
  }));
  validBatch.update(goalRef('alice'), {
    savedMinor: 125000,
    lastOperationId: 'op-acc-ok',
    updatedAt: 31
  });
  await assertSucceeds(validBatch.commit());

  // 2. Missing account is denied.
  const missingAccBatch = writeBatch(db('alice'));
  missingAccBatch.set(opRef('alice', 'op-acc-missing'), baseOp('op-acc-missing', {
    type: 'ADD',
    amountMinor: 25000,
    sourceAccountId: 'nonexistent-acc',
    createdAt: 32
  }));
  missingAccBatch.update(goalRef('alice'), {
    savedMinor: 150000,
    lastOperationId: 'op-acc-missing',
    updatedAt: 32
  });
  await assertFails(missingAccBatch.commit());

  // 3. Currency mismatch (USD account for LKR goal) is denied.
  const mismatchCurrencyBatch = writeBatch(db('alice'));
  mismatchCurrencyBatch.set(opRef('alice', 'op-acc-usd'), baseOp('op-acc-usd', {
    type: 'ADD',
    amountMinor: 25000,
    sourceAccountId: 'acc-usd',
    createdAt: 33
  }));
  mismatchCurrencyBatch.update(goalRef('alice'), {
    savedMinor: 150000,
    lastOperationId: 'op-acc-usd',
    updatedAt: 33
  });
  await assertFails(mismatchCurrencyBatch.commit());

  // 4. SET operation carrying sourceAccountId is denied.
  const setWithAccountBatch = writeBatch(db('alice'));
  setWithAccountBatch.set(opRef('alice', 'op-set-acc'), baseOp('op-set-acc', {
    type: 'SET',
    amountMinor: 150000,
    sourceAccountId: 'acc-lkr',
    createdAt: 34
  }));
  setWithAccountBatch.update(goalRef('alice'), {
    savedMinor: 150000,
    lastOperationId: 'op-set-acc',
    updatedAt: 34
  });
  await assertFails(setWithAccountBatch.commit());

  // 5. Malformed sourceAccountId (empty, null, >128 chars) is denied.
  for (const badAccountId of ['', null, 'x'.repeat(129)]) {
    const badIdBatch = writeBatch(db('alice'));
    badIdBatch.set(opRef('alice', 'op-bad-id'), baseOp('op-bad-id', {
      type: 'ADD',
      amountMinor: 10000,
      sourceAccountId: badAccountId,
      createdAt: 35
    }));
    badIdBatch.update(goalRef('alice'), {
      savedMinor: 135000,
      lastOperationId: 'op-bad-id',
      updatedAt: 35
    });
    await assertFails(badIdBatch.commit());
  }

  // 6. Account doc in Alice's path whose userId field is tampered to 'bob' is denied.
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'users/alice/accounts/acc-foreign'), baseAccount('acc-foreign', {
      userId: 'bob',
      currency: 'LKR'
    }));
  });
  const foreignOwnerBatch = writeBatch(db('alice'));
  foreignOwnerBatch.set(opRef('alice', 'op-foreign-owner'), baseOp('op-foreign-owner', {
    type: 'ADD',
    amountMinor: 10000,
    sourceAccountId: 'acc-foreign',
    createdAt: 36
  }));
  foreignOwnerBatch.update(goalRef('alice'), {
    savedMinor: 135000,
    lastOperationId: 'op-foreign-owner',
    updatedAt: 36
  });
  await assertFails(foreignOwnerBatch.commit());
});
