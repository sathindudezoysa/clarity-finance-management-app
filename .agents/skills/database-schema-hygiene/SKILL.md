---
name: database-schema-hygiene
description: Guardrails for local (Room) and remote (Firestore) database schema changes in Clarity. Prevents unnecessary development-time Room migrations, cross-branch schema workarounds, and temporary defensive patches in favor of clean schema definitions and database resets during pre-production development.
---

# Database Schema & Migration Hygiene (Clarity Android App)

This skill defines strict rules for managing local Room (`clarity_db`) and remote Firebase Cloud Firestore schemas during active development of the Clarity app.

## 1. Core Principle: Clean Schema Over Development-Time Migrations

Clarity is in active pre-production feature development across multiple branches. Schema mismatches on a developer's emulator or test device (e.g., `SQLiteException: no such table`, `Room cannot verify the data integrity`, or version number collisions between parallel feature branches) are **development environment artifacts**, NOT production upgrade paths.

### Strict Rules for Agents
1. **Do NOT Write Defensive or Cross-Branch Migrations:**
   - Never add defensive SQL checks (`hasColumn`, `PRAGMA table_info`, `CREATE TABLE IF NOT EXISTS` inside unrelated version steps) or chained migration workarounds to recover from an out-of-sync local emulator database or parallel branch schema collision.
   - Do not add new `Migration(X, Y)` classes for routine development schema changes unless explicitly requested by the user for a production release.
2. **Use `fallbackToDestructiveMigration()` in `DatabaseModule`:**
   - Keep `Room.databaseBuilder(...).fallbackToDestructiveMigration()` enabled in `app/src/main/java/com/example/clarity/di/DatabaseModule.kt` so Room recreates tables cleanly when the schema version changes.
   - When modifying entities (`@Entity`) or adding/removing tables in `ClarityDatabase.kt`, update the `@Database` entities list and bump `version` cleanly without cluttering the codebase with one-off migration objects or backfill scripts.
3. **Avoid Temporary Workarounds & Legacy Compatibility Code:**
   - Do not add fallback fields, nullable-by-accident columns, dual-read/dual-write adapters, or temporary mapping shims in `data/local/` or `data/remote/` just to tolerate stale development data in Room or Firestore.
   - Keep entity definitions (`data/local/entity/`), DAOs (`data/local/dao/`), and Firestore mappers (`data/mapper/`, `data/remote/`) strictly aligned with the current domain model.
4. **No Unnecessary Relational Coupling:**
   - Do not introduce rigid SQLite foreign-key cascades or complex cross-table SQL backfill queries inside migrations that couple independent features or break when tables are recreated or synced in different orders.

---

## 2. Diagnosing Launch Crashes Caused by Schema Changes

When the user reports a launch crash involving SQLite/Room (such as `no such table`, `no such column`, or `IllegalStateException: Room cannot verify the data integrity`) or malformed remote documents after switching branches or pulling new code:

1. **Identify whether the cause is stale local/remote development data** (e.g., the device has an older or parallel branch's `clarity_db` at the same or lower Room `version`).
2. **Do NOT patch the code with migration hacks.**
3. **Verify `ClarityDatabase` and `DatabaseModule` are clean** and use `.fallbackToDestructiveMigration()`.
4. **Instruct (or run) a clean reset of both the local and remote development databases** so tables and collections are recreated from scratch.

---

## 3. Clean Reset Runbook (Local & Remote Databases)

To avoid stale outbox items in the local Room database re-uploading to Firestore (or stale Firestore documents re-populating Room on launch), always clean **Remote first, then Local**.

### Step 1: Clean Remote Firestore Data (`users/{userId}`)
Clarity stores remote user data under `users/{userId}` subcollections (`transactions`, `accounts`, `account_settings`, etc.).

- **Via Firebase CLI (Recommended):**
  ```bash
  # Wipe all user documents and subcollections recursively
  npx firebase-tools@latest firestore:delete users --recursive --force

  # Or wipe a specific test user's data
  npx firebase-tools@latest firestore:delete "users/<USER_UID>" --recursive --force
  ```
- **Via Firebase Console:**
  1. Open **Firestore Database** -> `users` collection.
  2. Delete the `users` collection (or target `{userId}` document) with recursive deletion enabled so all nested subcollections are removed.

### Step 2: Clean Local Room Database (`clarity_db`) & App Storage

- **Option A — Full App Data Reset via ADB (Recommended; clears Room DB, Firestore offline cache, and Auth state):**
  ```bash
  adb shell pm clear com.example.clarity
  ```
- **Option B — Delete Only Room SQLite Files via ADB (preserves Auth session):**
  ```bash
  adb exec-out run-as com.example.clarity rm -f databases/clarity_db databases/clarity_db-shm databases/clarity_db-wal
  ```
- **Option C — Via Device/Emulator Settings:**
  - **Settings** -> **Apps** -> **Clarity** -> **Storage & cache** -> **Clear storage**.
