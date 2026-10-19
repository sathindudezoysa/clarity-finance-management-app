# Budget threshold alerts — issue 15

The missing FR-4.2 prerequisite is implemented: authenticated users can create, view,
edit, disable and re-enable real monthly category budgets. The dashboard opens the
Budgets screen or a specific budget. There is no sample budget data.

## Policies

- Calendar months use the **device's current timezone**, start inclusive and next
  month exclusive, based on `dateEpochMillis`. The shared `TransactionCalendar`
  policy also handles transaction date picking and display. Its explicit `ZoneId`
  arguments and the injected `Clock` make calendar calculations testable. A timezone
  change can move stored instants into a different date/month, matching the dates
  shown in the transaction UI; historical timestamps and budget month identities
  are not rewritten. Devices in different timezones may consequently assign a
  boundary expense to different months. Evaluation runs on Room changes, startup,
  activity resume, and a 30-second process-local refresh for clock/month/timezone changes.
- Categories share transaction trimming, blank-to-General and case-insensitive keys.
  The document ID is `YYYY-MM_` plus SHA-256 of the normalized lowercase category.
  Room also enforces owner/month/category-key uniqueness; rules recompute the hash.
- Limits are positive exact `Long` minor units in a supported currency. New budgets
  use the preferred currency; existing budgets retain their recorded currency.
  Aggregation and comparisons use `BigInteger`. Warning levels are 80% and 100%;
  exactly 100% says reached (level 2), above 100% shows the exceeded amount (level 3).
  Nearing is level 1 and normal is level 0.
- Only the owner's non-deleted category expenses within that month count. Local
  unsynced transactions count immediately. Cash expenses use the same transaction
  stream once. Income, wallet top-ups, adjustments and transfers are not added.
- Original currency matches use the original amount. Otherwise completed saved
  conversions must target the budget currency. All other eligible expenses remain
  explicitly uncounted; a partial known total can still trigger a warning.
  **Resolve uncounted currencies** uses the existing exchange-rate repository and
  stores a separate device-local resolution keyed by transaction version and budget
  currency. It never rewrites the transaction's historical conversion. Editing the
  transaction invalidates the old resolution; offline failures can be retried.
- In-app status is live, independent of delivery history. Notifications apply only
  to active budgets in the current local month. Disabling, falling below the threshold
  or changing month cancels the corresponding outstanding notification.
- Device-local Room alert marks are high-water levels per owner and budget/month.
  A direct jump above the limit claims level 3 and consumes both lower levels.
  Reached and exceeded can each alert when crossed sequentially. Dropping and
  recrossing, reopening, repeated emissions and retries do not re-alert under an
  unchanged limit. An actual applied limit change atomically resets the mark to zero
  with the budget write, whether from a local save, accepted remote update or upload
  acknowledgement. Unchanged saves, acknowledgements and snapshots preserve it.
  Zero also tells evaluation to cancel content describing the old limit; the new
  limit may immediately produce its highest applicable warning. Budget version and
  limit checks prevent stale evaluations from claiming the new history.
  New threshold deliveries may alert according to channel settings. Content refreshes
  use `setSilent(true)` and `setOnlyAlertOnce(true)` and require an already visible
  owner/budget notification, so ordinary refreshes do not repost dismissed notices.
  The atomic mark is committed **before** notification publication. This favors at
  most one delivery attempt per threshold: a crash or permission revocation between
  claim and publication can lose that notification; the persistent in-app warning
  remains. Delivery is not globally exactly once across devices. Reinstalling or
  destructive schema recreation removes this device's history.
  The claim transaction also verifies that the budget is still active and has the
  version used for evaluation, rejecting stale evaluations after a budget edit.

## Offline storage and synchronization

Room is authoritative. Saving/editing/disabling returns after a local transaction;
network I/O never holds that transaction or a budget mutation lock. Application-started
auth-scoped collectors enqueue network-constrained WorkManager uploads, listen for
remote budgets, and cancel old work/collectors on account changes. Evaluation has no
network constraint. Pending edits survive restart within the installed schema.

Server transactions keep the entire existing budget if concurrent creations use
different currencies, avoiding any relabeling of a minor-unit limit. Otherwise they
select the greater client `updatedAt`, prefer disabled status
on equal timestamps, and preserve server identity/creation/currency. Reconciliation
protects pending local edits; acknowledgements compare the full uploaded local row
before clearing its pending flag. Older remote active versions cannot overwrite a
newer local disable. As with other client-time conflict schemes, incorrect device
clocks can affect which edit wins. Budget documents are owner-private and omit
outbox and notification metadata. No new FCM service is introduced: this implements
**local threshold notifications**; server-driven FR-7.1 remains separate. Evaluation
does not promise execution while the process is stopped or the app is force-stopped.

Room schema **12 → 13** adds budgets, alert marks and separate currency resolutions.
The existing destructive recreation policy remains in force. A schema-changing
development upgrade **can discard unsynced local data**. No upgrade-safe migration
is claimed. No reset, cloud deletion, rule deployment, push or review-workflow change
is part of this implementation. Cloud budget writes require these rules to be
deployed separately by an authorized maintainer.

## Focused checks

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.clarity.BudgetPolicyTest' --tests 'com.example.clarity.BudgetAlertEvaluatorTest' :app:assembleDebug
```

The existing Firebase CLI requires Java 21 for the emulator (Android Gradle uses 17):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH
npx.cmd --yes firebase-tools@latest emulators:exec --only firestore --project demo-clarity-budgets --config firebase.budget-tests.json 'node tools/firestore-rules/budget.test.cjs'
```

If a device is already connected, run only
`BudgetPersistenceTest.offlineRestartDedupAcknowledgementAndUserIsolation` using
`connectedDebugAndroidTest` with the matching `android.testInstrumentationRunnerArguments.class`.
This test uses an isolated temporary database, fake auth, fake remote, fixed clock
and fake notification delivery; it never resets the application's database.

Original implementation verification: four focused budget policy tests passed, as did
`:app:assembleDebug` and `:app:lintDebug` on JDK 17. The focused Firestore budget
validation test passed with the installed Java 21 runtime. `adb devices -l` listed
no connected device/emulator, so the targeted Room persistence/integration test
was added but not executed. No broad test suite was run.

Follow-up alert-fix verification: **8 focused unit tests passed** (5 budget policy
and 3 evaluator tests), and `:app:assembleDebug` completed on JDK 17. The connected
emulator's Gradle UTP host runner aborted with `UTP_CORE_FAILED` /
`NullPointerException` in `SingleDeviceExecutor.kt:101`, recording zero executed
tests. Installing the built application and test APKs with `adb install -r -t`
and directly invoking only
`BudgetPersistenceTest#offlineRestartDedupAcknowledgementAndUserIsolation` passed:
**OK (1 test)**. Installed app data was preserved. The existing schema remains
**13**; this fix adds no entity fields, migration or reset. Lint, Firestore rules
tests and unrelated test suites were not rerun for these targeted fixes.

## Manual review

1. Offline, create Food / current local month / LKR 10,000. Add expenses totaling
   LKR 7,999.99: no warning. Reach LKR 8,000: nearing warning and optional notification.
2. Reach LKR 10,000: budget reached. Reach LKR 10,500: exceeded by LKR 500.
   From a fresh category/month budget, jump straight above 100%: only the high warning.
3. Reopen/restart, drop spending and recross: live status updates, no repeated
   notification for consumed thresholds. Edit, delete, recategorize and move an
   expense across month boundaries; inspect the updated total.
4. Add foreign spending with missing or incompatible conversion: incomplete is
   explicit. Resolve currencies after reconnecting; historical snapshots stay intact.
   Change preferred currency: the existing budget still says LKR.
5. Disable a budget: dashboard warning and notification disappear. Deny notification
   permission: budgeting still works. Sign out/switch users: prior data and budget
   notifications are not shown under the new account. Reconnect pending budget edits
   and confirm they upload without changing identity.
6. With spending at LKR 10,500, edit the limit to LKR 12,000: the new nearing warning
   replaces the old exceeded message. Save the same limit again: no repeated alert.
   Dismiss the notification: subsequent content-only refreshes leave it dismissed.
7. In Asia/Colombo select 1 October in the transaction editor: it displays and counts
   in October, even though the stored instant is 30 September UTC. The first instant
   of November in the same timezone is excluded from October.

## Best Practices and Security Alignment Update

Area: immutable explicit PendingIntents, private financial notifications, runtime
permission handling, authenticated navigation and owner-only Firestore validation.
Priority: high for financial data isolation; medium for notification delivery.

Scope: `MainActivity`, `AndroidManifest.xml`, `AndroidBudgetNotifications`,
`BudgetsScreen`, budget evaluation/sync classes, and `firestore.rules`. No dependencies
or unrelated component permissions change. Notification routes select only data from
the current owner's Room stream; an incoming extra is never used to choose a data owner.

Representative exact additions:

```diff
--- a/app/src/main/AndroidManifest.xml
+++ b/app/src/main/AndroidManifest.xml
@@ -7,0 +8 @@
+    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
--- /dev/null
+++ b/app/src/main/java/com/example/clarity/data/notifications/AndroidBudgetNotifications.kt
@@ -0,0 +1 @@
+        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
@@ -0,0 +1 @@
+            .setAutoCancel(true).setOnlyAlertOnce(silent).setSilent(silent).setVisibility(NotificationCompat.VISIBILITY_SECRET)
```

Permission is requested from an explicit budget-screen action, checks current grants,
shows rationale when appropriate, and links to settings after permanent denial.
Delivery catches revocation. Notifications use stable owner/budget tags and private
lock-screen visibility. Sign-out cancels the old owner's notifications.
New-alert and silent-refresh flags follow the
[NotificationCompat builder documentation](https://developer.android.com/reference/androidx/core/app/NotificationCompat.Builder).
The [Android notification permission guide](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
describes the API 33+ permission requirement. Firestore metadata handling follows
the [official listener documentation](https://firebase.google.com/docs/firestore/query-data/listen),
and document identity validation uses the [Rules hashing API](https://firebase.google.com/docs/reference/rules/rules.hashing).

Rules review: unauthenticated lists, foreign-owner reads/writes, owner spoofing,
immutable-field changes, malformed types, missing/extra fields, oversized categories,
invalid months/currencies, negative/fractional limits, stale update timestamps and
device-only fields are denied. No public profile, role, counter or external path
is added. No parent user document is required, consistent with existing owner-private
collections. The owner's budget list query is allowed. Creates and updates both
call the full validator; delete is denied in favor of disabling. Existing collections'
rules remain unchanged. The emulator test validates syntax and these attack cases.

## Suggested commit and PR

Commit: `feat(budgets): add offline threshold alerts and feedback`

PR title: `Add monthly budgets and offline threshold alerts`

PR body:

> Add monthly category budget CRUD, exact 80%/100% spending feedback, dashboard links,
> and optional local notifications with durable per-device deduplication. Room saves
> work offline; auth-scoped synchronization preserves pending edits and validates
> private budget documents. Currency mismatches remain explicit and can be resolved
> without rewriting historical transaction conversions. Device-local calendar months
> match the transaction date picker and displayed dates.
>
> Schema 13 retains destructive development recreation; unsynced local data may be
> discarded on upgrade. This implements local notifications, with FCM left separate.
>
> Validation: focused budget unit tests, assembleDebug, lintDebug, and the focused
> Firestore emulator budget test. Device persistence execution requires a connected
> device/emulator; see verification handoff for actual results.
>
> Closes #15
