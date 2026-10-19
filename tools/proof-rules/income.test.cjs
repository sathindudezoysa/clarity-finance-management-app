const {before, after, beforeEach, test} = require('node:test');
const fs = require('node:fs');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
const {doc, collection, setDoc, updateDoc, getDoc, getDocs, deleteDoc} = require('firebase/firestore');
let env;
const source = (patch = {}) => ({id:'salary',userId:'owner',name:'Salary',amountMinor:125000,accountId:'bank',
  recurring:true,active:true,createdAt:1,updatedAt:1,lastConfirmedMonth:null,...patch});
const path = 'users/owner/income_sources/salary';
before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-clarity-income',firestore:{rules:fs.readFileSync('../../firestore.rules','utf8')}});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async c => {
    await setDoc(doc(c.firestore(),'users/owner/accounts/bank'),{id:'bank',userId:'owner',name:'Daily',bankName:'Bank',currency:'LKR',isDefault:true,createdAt:1,updatedAt:1});
    await setDoc(doc(c.firestore(),'users/other/accounts/private'),{userId:'other',currency:'LKR'});
    await setDoc(doc(c.firestore(),'users/owner/accounts/foreign'),{userId:'owner',currency:'USD'});
  });
});
test('owner can create, list and confirm a source; pending flags stay local', async () => {
  const db=env.authenticatedContext('owner').firestore();
  await assertSucceeds(setDoc(doc(db,path),source()));
  await assertSucceeds(getDoc(doc(db,path)));
  await assertSucceeds(getDocs(collection(db,'users/owner/income_sources')));
  await assertSucceeds(updateDoc(doc(db,path),{lastConfirmedMonth:'2026-10',updatedAt:2}));
  await assertFails(updateDoc(doc(db,path),{pendingSync:true}));
});
test('anonymous and other owners cannot create, read, list or update income sources', async () => {
  const owner=env.authenticatedContext('owner').firestore();
  await setDoc(doc(owner,path),source());
  for(const db of [env.unauthenticatedContext().firestore(),env.authenticatedContext('other').firestore()]) {
    await assertFails(getDoc(doc(db,path)));
    await assertFails(getDocs(collection(db,'users/owner/income_sources')));
    await assertFails(updateDoc(doc(db,path),{name:'Stolen'}));
    await assertFails(setDoc(doc(db,'users/owner/income_sources/new'),source({id:'new'})));
  }
});
test('invalid amounts, months, owner spoofing, unknown fields and foreign accounts are denied', async () => {
  const db=env.authenticatedContext('owner').firestore();
  const bad=[{amountMinor:0},{amountMinor:-1},{amountMinor:1.25},{userId:'other'},{id:'different'},
    {name:''},{name:'   '},{name:'x'.repeat(101)},{lastConfirmedMonth:'2026-13'},{lastConfirmedMonth:'0000-01'},
    {role:'admin'},{accountId:'private'},{accountId:'missing'},{accountId:'foreign'}, {recurring:'yes'}];
  for(const patch of bad) await assertFails(setDoc(doc(db,path),source(patch)));
  const missing=source();delete missing.recurring;
  await assertFails(setDoc(doc(db,path),missing));
});
test('updates preserve creation identity and cannot remove required fields or bypass validation', async () => {
  const db=env.authenticatedContext('owner').firestore();
  await setDoc(doc(db,path),source());
  await assertFails(updateDoc(doc(db,path),{createdAt:2,updatedAt:2}));
  await assertFails(updateDoc(doc(db,path),{userId:'other'}));
  await assertFails(updateDoc(doc(db,path),{amountMinor:0}));
  await assertFails(setDoc(doc(db,path),{id:'salary',userId:'owner'}));
  await assertFails(deleteDoc(doc(db,path)));
  await assertSucceeds(updateDoc(doc(db,path),{active:false,updatedAt:2}));
});
