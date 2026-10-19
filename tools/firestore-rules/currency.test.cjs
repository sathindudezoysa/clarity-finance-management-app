const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc } = require('firebase/firestore');
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-clarity-currency', firestore: {
    host: '127.0.0.1', port: 8086,
    rules: fs.readFileSync(path.resolve(__dirname, '../../firestore.rules'), 'utf8')
  }});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });
const db = uid => env.authenticatedContext(uid).firestore();
const transaction = (extra = {}) => ({ id: 'entry', userId: 'alice', type: 'EXPENSE', amountMinor: 1250,
  category: 'Food', note: '', dateEpochMillis: 1, createdAt: 1, updatedAt: 10, accountId: null,
  currency: 'LKR', targetCurrency: 'LKR', pendingConversion: false,
  conversion: { amountMinor: 1250, rate: '1', provider: 'IDENTITY', rateAt: 0, fetchedAt: 0, cached: false }, ...extra });
const entry = uid => doc(db(uid), 'users/alice/transactions/entry');
test('identity, pending, and resolved snapshots sync with delayed client timestamps', async () => {
  await assertSucceeds(setDoc(entry('alice'), transaction()));
  await assertSucceeds(setDoc(entry('alice'), transaction({ currency: 'USD', pendingConversion: true, conversion: null })));
  await assertSucceeds(setDoc(entry('alice'), transaction({ currency: 'USD', conversion: {
    amountMinor: 375000, rate: '300', provider: 'ExchangeRate-API', rateAt: 2, fetchedAt: 3, cached: true
  }})));
  await assertSucceeds(getDocs(collection(db('alice'), 'users/alice/transactions')));
});
test('partial and contradictory metadata, invalid rates, and bookkeeping are denied', async () => {
  const valid = transaction();
  for (const key of ['currency', 'targetCurrency', 'pendingConversion', 'conversion']) {
    const invalid = { ...valid }; delete invalid[key];
    await assertFails(setDoc(entry('alice'), invalid));
  }
  for (const extra of [
    { pendingConversion: true }, { conversion: null }, { currency: 'ZZZ' }, { pendingSync: true }, { isDeleted: false },
    { conversion: { ...valid.conversion, amountMinor: 0 } },
    { conversion: { ...valid.conversion, amountMinor: 1 } },
    { conversion: { ...valid.conversion, rate: '0' } },
    { conversion: { ...valid.conversion, rate: '-1' } },
    { conversion: { ...valid.conversion, rate: '1e3' } },
    { conversion: { ...valid.conversion, provider: 'x'.repeat(100) } },
    { conversion: { ...valid.conversion, extra: true } },
    { currency: 'USD', conversion: { ...valid.conversion, provider: 'ExchangeRate-API', rateAt: 20, fetchedAt: 30 } }
  ]) await assertFails(setDoc(entry('alice'), transaction(extra)));
});
test('updates validate full shape and retain immutable identity and ownership', async () => {
  await assertSucceeds(setDoc(entry('alice'), transaction()));
  for (const extra of [{ userId: 'bob' }, { id: 'different' }, { createdAt: 2 }, { note: 'x'.repeat(2001) },
    { currency: 'USD', pendingConversion: false, conversion: null }, { currency: 3 }]) {
    await assertFails(updateDoc(entry('alice'), extra));
  }
  await assertFails(getDoc(entry('bob')));
  await assertFails(setDoc(entry('bob'), transaction()));
  await assertFails(deleteDoc(entry('bob')));
  const anonymous = env.unauthenticatedContext().firestore();
  await assertFails(getDocs(collection(anonymous, 'users/alice/transactions')));
});
test('preferences are private, strict, and support offline timestamps on create and update', async () => {
  const pref = uid => doc(db(uid), 'users/alice/preferences/currency');
  await assertSucceeds(setDoc(pref('alice'), { currency: 'JPY', updatedAt: 1 }));
  await assertSucceeds(updateDoc(pref('alice'), { currency: 'KWD', updatedAt: 2 }));
  await assertFails(getDoc(pref('bob')));
  await assertFails(setDoc(pref('bob'), { currency: 'USD', updatedAt: 3 }));
  for (const value of [{ currency: 'ZZZ', updatedAt: 2 }, { currency: 'USD' },
    { currency: 'USD', updatedAt: '2' }, { currency: 'USD', updatedAt: 2, pendingSync: true },
    { currency: 'x'.repeat(1000), updatedAt: 2 }]) {
    await assertFails(setDoc(pref('alice'), value));
  }
});
test('foreign and missing bank accounts remain denied', async () => {
  await assertFails(setDoc(entry('alice'), transaction({ accountId: 'missing' })));
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), 'users/alice/accounts/foreign'), { userId: 'bob' });
  });
  await assertFails(setDoc(entry('alice'), transaction({ accountId: 'foreign' })));
});
