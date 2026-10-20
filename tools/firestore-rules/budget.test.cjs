const { before, after, test } = require('node:test');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc } = require('firebase/firestore');
let env;
const key = category => createHash('sha256').update(category.trim().toLowerCase()).digest('hex');
const id = '2026-10_' + key('Food');
const value = () => ({ id, userId: 'alice', month: '2026-10', category: 'Food', categoryKey: key('Food'),
  limitMinor: 1000000, currency: 'LKR', active: true, createdAt: 1, updatedAt: 2 });
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-clarity-budgets', firestore: {
    host: '127.0.0.1', port: 8086, rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8')
  }});
});
after(async () => { if (env) await env.cleanup(); });
const db = uid => env.authenticatedContext(uid).firestore();
const ref = uid => doc(db(uid), 'users/alice/budgets/' + id);

test('owner-private budgets validate create and update, immutable identity, limits, and device-only fields', async () => {
  await assertSucceeds(setDoc(ref('alice'), value()));
  await assertSucceeds(getDocs(collection(db('alice'), 'users/alice/budgets')));
  await assertSucceeds(updateDoc(ref('alice'), { limitMinor: 900000, active: false, updatedAt: 3 }));
  await assertFails(getDoc(ref('bob')));
  await assertFails(setDoc(ref('bob'), value()));
  await assertFails(updateDoc(ref('bob'), { active: true, updatedAt: 4 }));
  await assertFails(deleteDoc(ref('bob')));
  await assertFails(deleteDoc(ref('alice')));
  await assertFails(getDocs(collection(env.unauthenticatedContext().firestore(), 'users/alice/budgets')));
  const invalid = [ { userId: 'bob' }, { id: 'other' }, { createdAt: 2 }, { month: '2026-13' },
    { month: '0000-01' }, { category: 'Travel' }, { category: 'x'.repeat(101) },
    { category: ' Food ' }, { categoryKey: 'f'.repeat(64) }, { currency: 'ZZZ' }, { currency: 'USD' },
    { limitMinor: 0 }, { limitMinor: -1 }, { limitMinor: 1.5 }, { limitMinor: '5' },
    { active: 'false' }, { updatedAt: 0 }, { updatedAt: '5' }, { pendingSync: true }, { deliveredLevel: 2 } ];
  for (const extra of invalid) {
    await assertFails(updateDoc(ref('alice'), { ...extra, ...(extra.updatedAt === undefined ? { updatedAt: 4 } : {}) }));
  }
  // Create checks use a new isolated owner document, so update-only immutability does not mask validation bugs.
  const charlie = db('charlie');
  for (const extra of invalid.filter(x => !('createdAt' in x) && !('currency' in x && x.currency === 'USD'))) {
    await assertFails(setDoc(doc(charlie, 'users/charlie/budgets/' + id), { ...value(), userId: 'charlie', ...extra }));
  }
  for (const field of Object.keys(value())) {
    const missing = { ...value(), userId: 'charlie' }; delete missing[field];
    await assertFails(setDoc(doc(charlie, 'users/charlie/budgets/' + id), missing));
  }
  await assertFails(setDoc(doc(db('alice'), 'users/alice/budgets/duplicate'), value()));
  await assertSucceeds(updateDoc(ref('alice'), { category: 'food', updatedAt: 4 }));
});
