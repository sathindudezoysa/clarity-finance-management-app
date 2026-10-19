# Multiple and recurring income sources (Issue #13 / FR-4.1)

Open **Income sources** from the dashboard. Add a source with a name, expected LKR amount, receiving bank account and optional monthly recurrence. The default receiving account is selected when available. Sources do not affect the ledger until receipt is explicitly confirmed.

Each active recurring source prompts in the dashboard and source list for the current local calendar month. Confirming receipt adds an INCOME transaction to the selected account and marks that month confirmed. A review dialog shows the source, amount, account and month. A new month prompts again; month boundaries and screen resume refresh the period. Prompts are in-app, including when the app is next opened; this implementation does not request notification permission or send background notifications.

Non-recurring sources support recording individual receipts. Editing the expected amount applies to future receipts. **Stop tracking** disables future prompts without deleting existing financial transactions. Sources currently use LKR accounts, matching the existing transaction currency behavior; no currency conversion is inferred.

## Architecture and synchronization

- Layer-first Clean Architecture: domain models/repository and individual use-case files; data entities/DAO/mapper/repository and remote source; Compose state and ViewModel under presentation/income.
- Room is the source of UI records and a durable outbox. Source creation and edits work offline when an owned LKR account is cached. Confirmation writes the financial transaction and source confirmation marker in one Room transaction.
- Recurring records use a deterministic owner/source/month transaction ID. Concurrent local confirmations cannot create duplicate rows, and devices converge to a single monthly transaction document. The existing transaction outbox immediately observes committed financial records. Its server writes preserve an existing document's immutable creation timestamp when the same monthly identity is confirmed on multiple devices; current updatedAt conflict resolution is retained.
- Source metadata lives at users/{uid}/income_sources/{sourceId}. A dedicated IncomeSyncManager starts from Application, observes authentication and actual outbox changes, and schedules owner-scoped, network-constrained WorkManager jobs with exponential retries. It subscribes to remote source snapshots and rebuilds Room after cache recreation. Account uploads precede dependent source uploads.
- Sync uses immutable owner/id/createdAt fields, version-aware acknowledgements and monotonic latest-confirmed-month reconciliation. Caller cancellation propagates. Unknown auth restoration retries; a known different owner retires the old worker. Switching owners clears source forms and review state.
- A stale amount/account/source version or review month cannot silently create a different receipt. Archive and source confirmation never edit existing transaction amounts. Financial records can still be managed using existing transaction details, editing, deletion and proof attachment flows.

## Development database policy

SCHEMA_VERSION is 11 for the income_sources entity. The application retains the skills' destructive upgrade/downgrade fallback and Firestore cache policy; no production migration is added. Version changes can discard unsynced local records. Same-version reopening preserves sources, confirmation flags, financial outboxes and proof/wallet records. Do not interpret the earlier historical migration reports as the current upgrade contract.

Obsolete migration-only test fixtures are retired because they asserted preservation across version changes. Current DatabaseCachePolicyTest covers recreating all older schema versions and a future-version downgrade, plus same-version persistence. Existing wallet, transaction and proof behavior tests remain.

## Firebase integration

The existing configured default project and native Android Firestore SDK are retained (resolved Firestore 24.11.0 / Firebase BOM 32.8.0). There are no new Android dependencies or database instances. The read-only database edition lookup requires Firebase credentials that are unavailable in this environment; live database edition and backend behavior are unverified.

The new prototype rules restrict source reads/writes to the authenticated owner, validate required/allowed fields on both creates and updates, preserve immutable identities, and require an owned LKR receiving account for active sources. Archive is supported; hard deletion is denied. Rules do not independently attest that a payment occurred. Existing account and transaction rule paths are retained. Deploy the reviewed firestore.rules before relying on remote income-source synchronization; no deployment or data reset is performed here.

## Verification

- IncomePolicyTest: monthly/year boundaries, recurring/one-off/stopped behavior, stable owner-scoped monthly identity, exact minor-unit amounts, malformed source documents and month reconciliation.
- IncomeSourcePersistenceTest: multiple sources, atomic duplicate-confirmation prevention, month rollover, same-version reopening, one-off receipts, owner isolation, missing accounts, stale review, archive, offline retry and version-safe sync acknowledgements.
- IncomeSourceUiTest: multiple sources, recurring confirmation state across months, one-off action and correct review identity.
- Firebase rules harness: npm run test:income from tools/proof-rules uses the isolated demo-clarity-income Firestore emulator. No production project is deployed. It checks owner read/list/create/update, anonymous/cross-owner denial, immutable metadata, malformed amounts/months, field allowlists, account ownership/currency and archive behavior.

Run ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug. Device execution requires a connected Android device/emulator. Rules runtime validation requires the harness dependencies and emulator; failures are reported separately from syntax checks.

Local verification (2026-10-19): debug application and instrumentation APK builds pass; all 110 JVM tests pass, including 11 income policy tests. Lint reports zero errors, 25 warnings and one informational finding, with no income-source findings. The host SQLite check exercised the generated income schema and DAO queries for owner isolation, pending selection and exact Long minor-unit amounts. No Android device is connected, so Room/Compose instrumentation tests cannot be executed here. JavaScript syntax checks pass for both new rules-harness scripts. Rules execution is unverified: the emulator runtime download stalled, and a bounded direct-download retry timed out. Live Firebase uploads and rules deployment were not performed.
