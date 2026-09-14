# STEP_TRACKER — session handoff (now/next only)

Why/context lives in `PROJECT_REPORT.md`. This file is the working handoff:
**update it at every step boundary, in the same commit as the step.** A new
session starts with: *"Read STEP_TRACKER.md, continue at the marked step."*

## Direction & settled decisions (do not re-litigate)

- Direction: **Rust repo (`~/code/active/backend`) = design reference only**;
  Kotlin is the implementation. Rust's internal debates (error-handling
  A/B/C, HTTP plumbing) are NOT this project's concern.
- Terminology: Android `entry*` → `transaction*` (matches Rust).
- Structure: keep layer-based MVVM (user rejects feature-based). Package
  `backend` → `ledger`; merge DI into one `di/`.
- **Nuking is fine** (early stage): use `fallbackToDestructiveMigration()`,
  delete migration history — no SQL migrations to write.
- **Buckets: DECIDED Option A** — a bucket becomes an `accounts` row with
  `parent_id` = funding account; allocation = plain `transfer` transaction;
  bucket value = child account's balance. Delete `BucketEntity` +
  `bucket_id` on lines. Add nullable `target_paise` to accounts (deliberate
  extension beyond Rust schema). Buckets hidden from account pickers (UI
  filter, like `is_system` pots).
- Invariants Room can't express (sum-zero, kind→shape matrix, idempotent
  ingest) stay enforced in `LedgerService` — see backend repo
  `migrations/README.md` "App responsibilities".

## Status

- [x] Commit `c922212` — PROJECT_REPORT.md added (TASK & STATUS.md removed).
- [ ] **STEP 1 — IN PROGRESS (execute edits below, validate, commit)**
- [ ] Step 2 — rename backend→ledger, entry→transaction
- [ ] Step 3 — rebuild schema the Rust way (big; see checklist)
- [ ] Step 5 — UI pass (MoneyText, typography, motion, charts, empty states)

## STEP 1 checklist (exact edits)

Delete files:
- `app/src/main/java/com/example/spendwise/data/database/entity/TransactionEntity.kt`
- `app/src/main/java/com/example/spendwise/data/database/dao/TransactionDao.kt`
- `app/src/main/java/com/example/spendwise/data/repository/TransactionRepository.kt`
- `app/src/main/java/com/example/spendwise/data/database/entity/MarkerEntity.kt`

Edit `data/database/AppDatabase.kt`:
- remove `TransactionEntity`, `MarkerEntity` from `@Database(entities=...)`
- remove `abstract fun transactionDao()`
- delete `MIGRATION_11_12`, `MIGRATION_12_13`, `MIGRATION_13_14`
- bump `version = 14` → `15`

Edit `core/di/DatabaseModule.kt`:
- remove `provideTransactionDao` (and any MarkerDao provider — none exists
  as a file, but verify what else in this module matched the grep:
  `MIGRATION_1*` wiring likely)
- remove migration wiring; add `.fallbackToDestructiveMigration()` to the
  Room builder

Verify nothing else injects `TransactionRepository` (grep found no
references outside the files above; re-grep before committing).

Validate: `./gradlew :app:compileDebugKotlin` (one command per call — the
terminal glitches when many commands are chained with echo separators).
Commit: `refactor: remove legacy transaction stack, destructive migrations`

## STEP 2 checklist

1. `git mv app/src/main/java/com/example/spendwise/backend app/src/main/java/com/example/spendwise/ledger`
   (report prefers a top-level `di/` — merge `backend/di` into it here or
   defer; either is fine, note which was done).
2. Update all `package com.example.spendwise.backend.*` → `...ledger.*` and
   imports (viewmodels, repositories, MainActivity/nav, Hilt modules, and
   tests under `app/src/test/java/com/example/spendwise/backend/` — move
   that dir too).
3. API renames in `ledger/api/Models.kt` + `LedgerApi.kt` (UI untouched):
   - `EntryKind`→`TransactionKind`, `EntryStatus`→`TransactionStatus`,
     `EntrySource`→`TransactionSource`
   - `CreateEntryRequest`→`CreateTransactionRequest`,
     `EntryView`→`TransactionView`
   - methods: `createEntry`→`createTransaction`, `getEntry`→`getTransaction`,
     `listEntries`→`listTransactions`, `deleteEntry`→`deleteTransaction`,
     `confirmEntry`→`confirmTransaction`, `voidEntry`→`voidTransaction`,
     `splitEntry`→`splitTransaction`,
     `findEntryByDedupeHash`→`findTransactionByDedupeHash`
   - stay: `ingest`, `ingestTransfer`, `assignAccount`, `accountBalance`,
     `bucketValue`, `recordOpeningBalance`, `recordBucketAllocation`,
     `friendsOutstanding`, `SplitRequest`, `IngestRequest`, `FriendBalance`
   - Room *table* names (`entries`, `entry_lines`, `entry_provenance`) and
     `Entry*Entity`/DAO names: leave for step 3's rebuild — code names only
     in this step.
Validate: compile + `./gradlew :app:testDebugUnitTest`.
Commit: `refactor: rename ledger package backend->ledger, entry->transaction API`

## STEP 3 checklist (the big reshape — sub-commits advised)

- Entities: `accounts` absorbs categories (class income/expense, parent_id),
  buckets (child accounts + `target_paise`), reference pots (equity,
  receivable, unmatched via `is_system`/`subtype`); `bank_account_details`
  side table (credit limit, statement/due day, reconciled_through);
  `account_identifiers` (kind: upi/card_last4/account_last4/phone);
  counterparties gain `first_seen`/`last_seen`; aliases gain `source`;
  provenance gains `parsed_facts` + `stated_balance_paise`;
  transactions header gains stored `kind` (9 values) — drop line-derived
  kind derivation; lines become pure `(transaction_id, account_id,
  amount_paise)` — drop category/bucket/counterparty/balance_after columns.
- Rename Room tables: `entries`→`transactions` (legacy table gone after
  step 1, name is free), `entry_lines`→`transaction_lines`,
  `entry_provenance`→`transaction_provenance`.
- Room `@DatabaseView` mirroring `v_account_balances` (single balance read
  path; liability sign flip lives only there).
- Add `groups`/`group_members` + DAO; budgets → monthly `period` rows keyed
  (account, period) — drop effective_from/to.
- Services: `LedgerService` enforces kind→shape matrix + sum-zero with the
  stored kind; ingest matching reads `account_identifiers` (kind-aware);
  `recordBucketAllocation` → idempotent transfer to child account; bucket
  value = child balance from the view.
- Keep the `phone_last10` contacts cache; do NOT port the Rust 008
  global-contacts shape.
- Error cases → sealed types: unbalanced lines, duplicate dedupe_hash,
  equity-guard rejections, buffer→confirm/void conflicts.
Validate: compile + unit tests (all backend service tests must pass).

## STEP 5 checklist (UI)

`MoneyText` composable (semantic colors, tnum, count-up) → typography with
res/font (Outfit/Manrope/Space Grotesk) → NavHost slide+fade transitions →
`animateItem()`/`animateContentSize()` → hand-rolled Canvas charts (30-day
bars, category donut) → empty states on Transactions/Inbox/Friends/Budget →
rename screens transaction/→TransactionDetail, update/→TransactionEdit;
split ScannerScreen (1,164 lines).

