# Transaction proofs (Issue #9)

Implemented on `9-attach-proofs-to-transactions`, based on `main` at `28ed0d7`.

**Current database policy:** the application follows the skills' development-cache policy. It registers no manual migrations and enables destructive fallback for upgrades and downgrades. A database version change can discard unsynced local transactions, wallet records and pending proofs; synced records are rebuilt from remote data. Existing migration reports below describe earlier revisions, not the current production builder.

## User flow

Open a saved transaction, select **Attach photo or PDF**, and choose a file. Import and upload start automatically. No storage permission dialog is required. Photos (JPEG, PNG, WebP, GIF, HEIC/HEIF) and PDFs are supported, up to 10 MB each and ten attachments per transaction.

**Open proof** opens the private local copy using an installed image/PDF viewer. After an uploaded proof is restored on another device, opening it downloads and caches a private copy. A failed upload leaves the local copy and outbox record available. Pending edits schedule network-constrained WorkManager uploads. Failed work uses exponential backoff starting at one minute and can continue after the app leaves the foreground. Sign-in/restart reschedules pending evidence; **Refresh / retry upload** also remains available.

After reviewing a receipt, the owner can explicitly mark the displayed transaction amount as verified, or clear verification. The accessible verification icon appears only when a proof's recorded verified amount exactly matches the current positive transaction amount. Changing the transaction amount hides the icon. This is a record of the owner's review; it is not OCR, fraud detection, or an independent bank attestation.

## Architecture and data safety

- Domain model, repository interface and one-action use cases remain Android-independent.
- A separate Hilt ViewModel manages the proof section without changing existing transaction ViewModel constructor contracts.
- Room `transaction_proofs` holds owner/transaction associations, display metadata, exact verified minor-unit amounts, and a durable upload outbox. Upload acknowledgements use revision checks; stale uploads cannot clear later edits.
- `ProofSyncManager` observes actual pending proof revisions and schedules unique work. It does not poll every 30 seconds, and acknowledgements alone do not create additional jobs. Sign-out cancels work for the previous owner; workers recheck authentication before accessing media. A dedicated `ProofSyncProcessor` serializes foreground refresh and background uploads.
- File contents are copied into the application's private, excluded-from-backup storage. Files are bounded while streaming and their headers are checked against the declared MIME type. Temporary viewer copies are exposed through a non-exported FileProvider restricted to its preview directory, with read-only grants.
- Preview copies are reused and bounded to 50 files / 50 MB. Startup and each open remove previews older than 24 hours and evict oldest copies above the budget, preserving the copy currently being opened. Original private evidence is outside this cleanup directory.
- Transaction routes and detail/edit/proof ViewModels share `TRANSACTION_ID_ARGUMENT` and safe SavedStateHandle extraction; malformed restored values still fail safely.
- Firebase Storage holds each file and its metadata at `users/{uid}/transactions/{transactionId}/proofs/{proofId}`. UUID paths remain stable across retries. No public download URLs are persisted. Refresh preserves unsynced local verification edits.
- Attachment writes never update transaction amounts, account associations, categories, descriptions or transaction sync flags. Existing transaction create/edit/delete workflows retain their implementation.
- Room version 8 adds only the proof table; version 9 adds a non-null `unavailable` flag with a default of false. The additive 8-to-9 migration preserves existing proof metadata and upload state. Existing migrations 1â€“6 remain registered. A 7â†’8 path adds the same table for compatibility with the pending wallet schema; wallet tables are retained. Transaction deletion hides its evidence through owner-scoped observation; it does not cascade-delete financial records or remote objects.

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

## Previous validation results (commit 2bdf3ef)

- Final Gradle run: `BUILD SUCCESSFUL`; app APK and instrumentation APK built.
- 69 JUnit tests passed, with zero failures, errors or skipped tests.
- Android lint: 0 errors, 24 warnings. Existing AGP compileSdk compatibility and unused sample-variable warnings remain.
- Host SQLite checks exercised the migration chains from main versions 1?6 to version 8, compared tables and indexes against Room-generated SQL, checked preserved financial fields, and confirmed that old upload acknowledgements cannot clear newer verification edits. These checks do not replace device-based Room schema validation.
- Storage rule test JavaScript passed `node --check`; emulator runtime tests are blocked by npm certificate trust.
- No connected Android device; instrumentation tests were compiled but not executed. No live Storage upload or deployment was performed.
- The existing Hilt, JUnit4, Compose and Android instrumentation stack is retained; no mocking framework or Android test framework dependency was added.

## PR #39 review follow-up

The proof repository no longer starts a polling coroutine or needs an application scope. The Application starts a dedicated sync manager; WorkManager constructs the upload worker normally and the worker resolves its scoped dependencies through a Hilt entry point. The only added dependency is the compatible WorkManager runtime. Existing financial sync initialization is retained.

Proof integration points use ordinary Kotlin imports. `LocalLifecycleOwner` stays imported from `androidx.compose.ui.platform`: inspection of the pinned Lifecycle 2.7.0 AAR confirms it does not export `androidx.lifecycle.compose.LocalLifecycleOwner`. Moving that import would require a separate Lifecycle upgrade. Both imports provide the lifecycle owner used for lifecycle-aware collection in their supported versions.

`ProofSyncReviewTest` covers queue change detection, no extra work on acknowledgements, owner separation, cache age/byte/count bounds, active-preview retention and original-file preservation. Device tests add background upload retry, deleted-parent handling, provider URI MIME checks and shared navigation argument validation. Physical-device battery behavior and external-viewer traversal remain manual checks.

Review validation: both APK builds, 69 unit tests, lint (zero errors), and release manifest generation passed. Debug and release manifests both resolve the private provider to `com.example.clarity.proofs`. No device is connected, so background execution/battery behavior and viewer UI tests are compiled checks only, pending physical-device verification.


## PR #39 follow-up to commit 2bdf3ef

- The upload worker retries when authentication is still restoring (null), including auth loss while uploading. A known different signed-in owner retires the old work without uploading. Cancellation still propagates.
- Firebase downloads use `getFile()` into a temporary file rather than allocating the entire attachment on the heap. Metadata size/type checks precede transfer, transfer progress enforces the size bound, and file length/header checks precede the final rename. Cancellation cancels the transfer; temporary files are removed on failure.
- Pending verification changes use Storage `updateMetadata()` for an existing object. Media is uploaded only after a confirmed object-not-found response and a locally available original. Transient network, permission and service errors keep the upload pending.
- A confirmed cloud 404 plus a missing local original records an unavailable proof and removes that revision from the retry queue. The detail view explains recovery and offers **Remove missing proof**, after which the user can attach the original again. Unavailable proofs cannot show a verification icon. Conditional revision updates protect newer edits; the removal action never modifies the financial transaction.
- Parent directories are checked explicitly. Preview cleanup runs before recreating the destination directory, and content-provider display-name lookup checks the returned column index.
- New regression coverage includes worker auth decisions, metadata-only verification when the local copy is absent, missing-file recovery, stale terminal updates, the version 8-to-9 migration, and recovery UI controls. The review's exception-parameter suggestion did not identify a reproducible syntax error; the existing typed Kotlin catch parameters are retained and checked by compilation.

The implementation follows Firebase's documented [file download API](https://firebase.google.com/docs/storage/android/download-files) and [metadata update API](https://firebase.google.com/docs/storage/android/file-metadata).

Validation for this follow-up: `BUILD SUCCESSFUL` for the debug APK, instrumentation APK, unit tests and lint. All 75 JVM tests passed (zero failures, errors or skips). Lint reports zero errors and 24 warnings. Host SQLite migration checks passed for versions 1 through 6 and for existing version 8 proofs, using Room-generated schema SQL. No Android device is connected, so Room instrumentation, recovery UI and external-viewer tests were compiled but not executed. Live Firebase uploads and Storage rule runtime tests remain unverified; the previously documented npm certificate-trust blocker remains.


## Merge with main at dcaccdd (PR #39)

The merge retains main's transaction-tag reconciliation, Firestore cache configuration, shared schema-version constant, repository instructions and ignore rules. `ClarityDatabase.SCHEMA_VERSION` is 9, matching the existing proof schema; the entities and proof DAO are preserved. No new schema change or migration is introduced by this merge.

Main's new pre-production schema policy removes migrations and enables destructive cache resets. For this merge, the user's existing instruction to preserve financial records and valid migrations takes precedence: the existing additive migration chain and migration persistence tests are retained. Production database construction does not enable destructive migration or downgrade fallback. In particular, pending proof uploads and offline verification records must not be erased on upgrade from main's version 6.

Conflict decisions:

- `AccountDaoTest.kt`: retain migration/persistence tests and their non-destructive builder.
- `TransactionAccountMigrationTest.kt`: retain the file and its account-association/backfill tests instead of main's deletion.
- `TransactionTagsPersistenceTest.kt`: retain persistence and migration coverage; add a reconciliation test for main's new tag-catalog rebuilding behavior.
- `ClarityDatabase.kt`: retain all five entities and the proof DAO; use the shared schema constant required by main's Firebase configuration.
- `DatabaseModule.kt`: retain existing registrations for versions 1 through 9 and the existing version 7 compatibility path. Restore the four migration source files removed in main so every registered migration resolves.

A backup branch, `backup/proofs-before-main-20261019-386a654`, preserves the pre-merge feature commit. The merge is staged for review, without an automatic merge commit or push. Local IDE files are retained and excluded by main's ignore rules.

Merge validation: the debug APK and instrumentation APK built successfully; all 75 unit tests passed with zero failures, errors or skips. Lint completed with 0 errors and 24 warnings. Host SQLite checks preserved financial records and proofs across the existing migration chains. No device is connected, so instrumentation tests were compiled but not executed. Git has no unmerged paths or conflict markers in application sources. An existing extra blank line at EOF in main's imported Feature Implementation skill is the only staged whitespace warning; its instructions were left unchanged.


## Combined wallet/proof merge with main at 7fe5430

Main now contains the cash-wallet implementation from PR #37. PR #39 therefore keeps all wallet entities and the proof entity together, using `ClarityDatabase.SCHEMA_VERSION = 10`. The existing user's requirement to preserve financial records and valid migrations takes precedence over the development cache-reset policy. No local or remote database reset is performed.

`IntegratedMigrations.all` is the shared production/test registration. It retains the published migration steps, adds the existing proof 6/7-to-8 and 8-to-9 steps, and uses the existing wallet completion logic for the 9-to-10 upgrade. Wallet 7 upgrades through proof 8/9 without dropping wallet tables. Proof-only 8/9 databases gain the missing wallet tables on reaching version 10. Existing cash links keep null bank-account associations; existing accounts, amounts, categories, descriptions, top-ups, authorization flags, proof verification, unavailable flags and pending uploads remain intact.

The five conflicted files retain main's account/wallet persistence coverage and both feature DAOs. All Room migration tests use the same integrated registration. `WalletProofMigrationTest` exercises wallet 7, proof 8/9 and combined 10 fixtures, checks financial and proof outboxes, exact wallet balance, configured top-up authorization and reopening. Historical account/wallet versions remain covered by `IntegratedMigrationTest`.

Automatic navigation merges were checked: all 12 destinations occur once, wallet cash edits retain their bank-selection rules, and transaction details retain the proof section. Main's IDE cleanup is kept in Git while the old local configuration files are restored locally under the existing ignore rules. The pre-merge feature head is available at `backup/proofs-before-wallet-main-20261019-e828148`.

Host SQLite checks cover 16 schema fixtures against Room-generated version 10 SQL, preserving ledger records and proof metadata. Wallet SQL checks cover exact balances, bounded history, owner isolation and concurrent reads during transfers. These checks complement Android Room tests; no device is connected for instrumentation execution.

Combined-merge validation: `BUILD SUCCESSFUL` for app APK, instrumentation APK, unit tests and lint. All 92 JVM tests passed (zero failures, errors or skips). Lint: 0 errors and 25 warnings. All 16 host migration fixtures and wallet SQL checks passed. Instrumentation tests compiled but could not run without a device; live Firebase upload/viewer checks remain unverified. The resolved merge is staged for review without an automatic commit or push.


## Review follow-up to commit 91aee86

Changes are limited to the review findings; existing wallet, transaction, account and synchronization implementations are retained.

- `DatabaseModule` uses `fallbackToDestructiveMigration()` and `fallbackToDestructiveMigrationOnDowngrade()` with no `addMigrations` call. The old integrated registry is now under Android test sources for historical upgrade fixtures only. No migration helper implementation was rewritten. Production and new cache-policy tests use the same database factory. There is no entity/schema change, so `SCHEMA_VERSION` remains 10; reopening the same schema does not reset its records.
- `ProofFileSignature.readHeader` reads up to 64 actual bytes, handles partial and zero-length bulk reads, and returns only bytes received. Both local imports and streamed Firebase downloads use it. Seven new unit cases cover empty/small/large files, fragmented reads, zero-length reads, truncated signatures and read failures.
- The proof separator uses a one-dp line with `MaterialTheme.colorScheme.outlineVariant`. The pinned Compose BOM resolves Material3 1.1.1, whose cached `DividerKt` bytecode has no `HorizontalDivider` API. The replacement avoids `Divider()` while retaining its appearance and the existing dependency versions.
- `DatabaseCachePolicyTest` covers the actual production builder for stale versions 1/6/7/8/9 and a future-version downgrade, verifies empty rebuilt tables and transaction/tag reconciliation, and checks that current-schema reopening preserves cash balances, transactions and pending proof metadata.

Previously resolved behavior remains: network-constrained WorkManager sync, auth-restoration retries, streamed downloads, metadata-only verification updates, missing-proof recovery, bounded previews, checked parent directories and shared navigation arguments. Storage timeouts retain `withTimeoutOrNull` ownership semantics and propagate caller cancellation. No live data reset or Firebase deployment was performed.

Review validation: `BUILD SUCCESSFUL` for debug APK, instrumentation APK, unit tests and lint. All 99 unit tests passed with zero failures, errors or skips, including seven bounded header-read regressions. Lint completed with 0 errors and 25 warnings. The new production-cache policy tests compiled but could not execute because no Android device is connected. Live Storage, file-picker and external-viewer checks remain unverified. No commit or push was performed.
