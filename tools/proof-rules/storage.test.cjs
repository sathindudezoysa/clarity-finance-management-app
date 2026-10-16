const { before, after, beforeEach, test } = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { ref, uploadBytes, getBytes, listAll, updateMetadata, deleteObject } = require('firebase/storage');

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-clarity-proofs',
    storage: { host: '127.0.0.1', port: 9199, rules: fs.readFileSync(path.join(__dirname, '../../storage.rules'), 'utf8') }
  });
});
beforeEach(async () => { await env.clearStorage(); });
after(async () => { if (env) await env.cleanup(); });
const object = 'users/owner/transactions/transaction/proofs/proof';
const bytes = new TextEncoder().encode('%PDF-1.4 receipt');
const metadata = () => ({ contentType: 'application/pdf', customMetadata: {
  displayName: 'Receipt.pdf', createdAt: '100', revision: '100', verifiedAmountMinor: ''
} });
const owner = () => env.authenticatedContext('owner').storage();

test('owner can attach, list, reopen and explicitly verify their proof', async () => {
  const storage = owner();
  await assertSucceeds(uploadBytes(ref(storage, object), bytes, metadata()));
  await assertSucceeds(listAll(ref(storage, 'users/owner/transactions/transaction/proofs')));
  await assertSucceeds(getBytes(ref(storage, object)));
  await assertSucceeds(updateMetadata(ref(storage, object), { customMetadata: { verifiedAmountMinor: '1250', revision: '101' } }));
  await assertSucceeds(updateMetadata(ref(storage, object), { customMetadata: { verifiedAmountMinor: '', revision: '102' } }));
});

test('anonymous and other owners cannot read, list, replace or delete private evidence', async () => {
  await assertSucceeds(uploadBytes(ref(owner(), object), bytes, metadata()));
  for (const storage of [env.unauthenticatedContext().storage(), env.authenticatedContext('other').storage()]) {
    await assertFails(getBytes(ref(storage, object)));
    await assertFails(listAll(ref(storage, 'users/owner/transactions/transaction/proofs')));
    await assertFails(uploadBytes(ref(storage, object), bytes, metadata()));
    await assertFails(deleteObject(ref(storage, object)));
  }
});

test('empty, oversized and unsupported files are rejected', async () => {
  await assertFails(uploadBytes(ref(owner(), object), new Uint8Array(0), metadata()));
  await assertFails(uploadBytes(ref(owner(), object), new Uint8Array(10 * 1024 * 1024 + 1), metadata()));
  for (const contentType of ['text/html', 'image/svg+xml', 'application/zip']) {
    await assertFails(uploadBytes(ref(owner(), object), bytes, { ...metadata(), contentType }));
  }
});

test('updates cannot bypass metadata validation or change a proof to executable content', async () => {
  await assertSucceeds(uploadBytes(ref(owner(), object), bytes, metadata()));
  await assertFails(updateMetadata(ref(owner(), object), { contentType: 'text/html' }));
  for (const verifiedAmountMinor of ['-1', '0', '12.50', 'verified']) {
    await assertFails(updateMetadata(ref(owner(), object), { customMetadata: { verifiedAmountMinor } }));
  }
  await assertFails(updateMetadata(ref(owner(), object), { customMetadata: { admin: 'true' } }));
  await assertFails(updateMetadata(ref(owner(), object), { customMetadata: { displayName: 'x'.repeat(121) } }));
});

test('unrelated bucket paths stay denied', async () => {
  await assertFails(uploadBytes(ref(owner(), 'public/receipt.pdf'), bytes, metadata()));
  await assertFails(uploadBytes(ref(owner(), 'users/owner/transactions/' + 'x'.repeat(129) + '/proofs/proof'), bytes, metadata()));
});
