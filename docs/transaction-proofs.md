# Transaction proofs (Issue #9)

Implemented on `9-attach-proofs-to-transactions`, based on `main` at `28ed0d7`.

## User flow

Open a saved transaction, select **Attach photo or PDF**, and choose a file. Import and upload start automatically. No storage permission dialog is required. Photos (JPEG, PNG, WebP, GIF, HEIC/HEIF) and PDFs are supported, up to 10 MB each and ten attachments per transaction.

**Open proof** opens the private local copy using an installed image/PDF viewer. After an uploaded proof is restored on another device, opening it downloads and caches a private copy. A failed upload leaves the local copy and outbox record available. Uploads retry while the application is running, after sign-in/restart, or using **Refresh / retry upload**.

After reviewing a receipt, the owner can explicitly mark the displayed transaction amount as verified, or clear verification. The accessible verification icon appears only when a proof's recorded verified amount exactly matches the current positive transaction amount. Changing the transaction amount hides the icon. This is a record of the owner's review; it is not OCR, fraud detection, or an independent bank attestation.

## Architecture and data safety

- Domain model, repository interface and one-action use cases remain Android-independent.
- A separate Hilt ViewModel manages the proof section without changing existing transaction ViewModel constructor contracts.
- Room `transaction_proofs` holds owner/transaction associations, display metadata, exact verified minor-unit amounts, and a durable upload outbox. Upload acknowledgements use revision checks; stale uploads cannot clear later edits.
- File contents are copied into the application's private, excluded-from-backup storage. Files are bounded while streaming and their headers are checked against the declared MIME type. Temporary viewer copies are exposed through a non-exported FileProvider restricted to its preview directory, with read-only grants.
- Firebase Storage holds each file and its metadata at `users/{uid}/transactions/{transactionId}/proofs/{proofId}`. UUID paths remain stable across retries. No public download URLs are persisted. Refresh preserves unsynced local verification edits.
- Attachment writes never update transaction amounts, account associations, categories, descriptions or transaction sync flags. Existing transaction create/edit/delete workflows retain their implementation.
- Room version 8 adds only the proof table. Existing migrations 1–6 remain registered. A 7→8 path adds the same table for compatibility with the pending wallet schema; wallet tables are retained. Transaction deletion hides its evidence through owner-scoped observation; it does not cascade-delete financial records or remote objects.

## Firebase setup required for cloud uploads

The implementation uses the existing project's Firebase configuration. Provision its default Cloud Storage bucket and ensure `google-services.json` includes the corresponding `storage_bucket`. Follow Firebase's current project billing requirements for Storage. No bucket creation, billing change or deployment is performed by this change.

Review `storage.rules` and deploy through your normal Firebase deployment process:

```sh
firebase deploy --only storage --project clarity-finance-management-app
```

Rules permit owner-only reads/lists/writes and validate MIME type, actual object size and bounded proof metadata for both creates and updates. Paths outside the proof area remain denied. Existing Firestore rules and transaction serialization are unchanged. The attachment metadata belongs to its Storage object, so no new Firestore database or composite index is needed.

## Verification

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

`TransactionProofTest` covers exact amount matching, unverified/cleared states, file bounds, MIME/header checks, identifier validation and mapping. `TransactionProofPersistenceTest` covers additive Room migration, photo/PDF reopening, source URI independence, failed upload retry, verification changes, owner isolation and conditional acknowledgements. `TransactionProofUiTest` checks icon presence and absence as the amount changes. Device tests require a connected emulator or Android device.

The Storage emulator suite covers owner upload/list/open/verification, anonymous and cross-owner denial, file size/type limits, metadata update bypass attempts, and denied unrelated paths. Use Node 24.12+ and Java 21+:

```sh
cd tools/proof-rules
npm install
npm test
```

This uses the isolated `demo-clarity-proofs` project and deploys nothing. In this environment, npm registry access fails with `UNABLE_TO_VERIFY_LEAF_SIGNATURE`, including with Node's system CA support; TLS verification has not been disabled. Storage rule runtime tests and a live Firebase upload therefore remain unverified. Local file selection, external viewer behavior and accessibility also require device verification.

## Local validation results

- Final Gradle run: `BUILD SUCCESSFUL`; app APK and instrumentation APK built.
- 64 JUnit tests passed, with zero failures, errors or skipped tests.
- Android lint: 0 errors, 23 warnings. Existing AGP compileSdk compatibility and unused sample-variable warnings remain.
- Host SQLite checks exercised the migration chains from main versions 1?6 to version 8, compared tables and indexes against Room-generated SQL, checked preserved financial fields, and confirmed that old upload acknowledgements cannot clear newer verification edits. These checks do not replace device-based Room schema validation.
- Storage rule test JavaScript passed `node --check`; emulator runtime tests are blocked by npm certificate trust.
- No connected Android device; instrumentation tests were compiled but not executed. No live Storage upload or deployment was performed.
- The existing Hilt, JUnit4, Compose and Android instrumentation stack is retained; no mocking framework or Android test framework dependency was added.
