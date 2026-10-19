# Issue #12 implementation handoff

The feature adds per-UID reporting currency settings, exact conversion snapshots,
daily exchange-rate retrieval and persistent Room caching, offline entry, and
network-constrained deferred conversion using the existing transaction outbox.
Original amounts retain their units throughout storage, display and wallet SQL.
History keeps its captured reporting currency after preference changes. Pending
entries are clearly identified and excluded from totals grouped by target code.

See [the money and offline contract](foreign-currency.md) for provider details,
supported currencies, rounding, cache age limits, race checks and schema policy.

## Configuration and release

No provider credential is required. The UI links the required provider attribution.
Review and deploy `firestore.rules` to the existing Firebase project before
releasing the changed client. No deployment was performed by this implementation.
I've set up prototype Security Rules to keep the data in Firestore safe. They are
designed to be secure for owner-private access and validated currency metadata.
However, you should review and verify them before broadly sharing your app. If
you'd like, I can help you harden these rules.
The CLI could not inspect the production database edition because it is not
authenticated; the local rule harness explicitly uses Standard/native Firestore.

The actual checkout started on schema 7 (rather than the supplied snapshot's 10),
so this change uses schema 8. It restores destructive recreation in the database
provider, as repository instructions require. Schema-changing installs may lose
unsynced local data. No production cloud data or device storage reset is required
or performed by the feature's tooling. Tests manage only isolated test databases.
Restart durability applies within the installed schema.

This checkout has no receipt proof repository or verification screen, and lacked
WorkManager. Added dependencies are WorkManager 2.9.0, its instrumented test helper,
and Gson 2.10.1 for exact JSON number-token parsing. No existing stack is upgraded.

## Test tooling

The existing stack is JUnit 4, AndroidX instrumentation, Compose UI tests, Hilt,
Room and coroutine fakes. There is no mocking framework or Robolectric dependency.
New tests use exact deterministic quotes, Room files that are unique to tests,
WorkManager's constraint test driver, and isolated demo Firebase emulator clients.
They do not use live exchange-rate responses. Debug network configuration permits
HTTP only to emulator/loopback hosts; release networking is unchanged.

Run the four required checks under JDK 17:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
```

Install rules test dependencies with `npm ci` in `tools/firestore-rules`.
With JDK 21 available for the current Firebase emulator CLI, run from repo root:

```powershell
npx firebase-tools@latest emulators:exec --only firestore,auth --project demo-clarity-currency --config firebase.currency-tests.json 'powershell.exe -NoProfile -File tools/firestore-rules/run-connected.ps1'
```

The runner accepts `-Jdk17 <path>` and `-Adb <path>` for other installations.
The old AGP/UTP host runner can abort with `GeneratedMessageV3` /
`CodedInputStream.shouldDiscardUnknownFields` `IllegalAccessError` before executing
tests. Use `-DirectAdb` on the inner script to install the built test APKs with
`adb install -r -t` and run instrumentation directly, preserving installed data.
The integration test expects the demo Auth and Firestore emulators above.

## Suggested commit

`feat(transactions): support durable foreign-currency conversion`

## Review-ready PR

Title: **Support foreign-currency transactions with durable offline conversion**

Body:

Transactions can now retain an original currency amount and a fixed equivalent in
the user's selected reporting currency. Settings persist that preference per UID
and synchronize it to an owner-private Firestore document. Existing records have
an explicit LKR interpretation; cash wallet entries stay LKR.

Daily keyless ExchangeRate-API quotes are cached in Room. Entries save immediately
offline using a usable cached quote or explicit pending conversion. WorkManager
resolves pending entries after reconnect/restart with version and ownership
checks, then uses the existing account-ordered transaction outbox to synchronize
the complete snapshot. Completed snapshots are never repriced on reconnect or
note-only edits. Totals group recorded target currencies and expose exclusions.

Firestore serialization/rules validate complete conversion shapes and private
preferences. Schema 8 follows the mandated development recreation policy, which
can discard unsynced data on a schema-changing upgrade. Review/deploy rules before
releasing the client. No production deployment is included.

Validation results are recorded below.

Closes #12

## Validation results

- JDK 17: `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`,
  and `:app:assembleDebugAndroidTest` passed on the final implementation.
- Unit tests: 84 passed, 0 failures. Lint completed with warnings and no errors.
- Firestore emulator: all 5 allow/deny suites passed. These exercise actual
  Firestore rules, not Storage rules.
- Android persistence, cache reopening, preference isolation, conversion commit
  guards, cash reporting/ledger integrity, currency selection, cached quote UI,
  retry UI and saveable state restoration passed through ADB instrumentation.
- Gradle's connected runner aborts before executing tests with the existing
  AGP/UTP protobuf `IllegalAccessError`; direct ADB instrumentation is provided.
- The last full ADB run executed 78 tests: 75 passed and 3 failed. One failure was
  the new integration test's harness waiting for a pruned WorkManager job ID.
  Its readiness check also now accepts usable Room data with a sync warning.
  After these corrections, the final focused reconnect/race test passed (1/1,
  0 failures). It verifies offline saves with and without cache, resolution after
  Room restart, complete Firestore synchronization, stable note-only snapshots,
  currency edits, and suspended-response edit/delete/user-switch races. The full
  78-test suite was not rerun after this final test-harness correction.
- The other two failures are unchanged `WalletInteractionTest` cases:
  `reviewCancellationPropagatesWithoutBecomingAUserError` and
  `unexpectedReviewFailureClearsConfirmationAndShowsSafeFeedback`. Both failures
  were reproduced by building and running the unmodified HEAD snapshot in an
  isolated directory. They were not suppressed or changed.
- No production rules deployment was performed. Production database inspection
  was blocked by the CLI's missing Firebase authentication.
