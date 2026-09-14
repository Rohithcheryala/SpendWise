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
- [x] Commit `656477d` — STEP_TRACKER.md added.
- [x] Commit `2c44de9` — **Step 1 DONE**: legacy stack deleted, Room v15,
  destructive-only migrations, compile green.
- [x] **Step 2 DONE** — package `backend`→`ledger` (main + test dirs),
  API renames Entry*→Transaction* (types + 8 methods). Collisions resolved:
  dead duplicate enums in `data/mapper/EntryWithDetails.kt` deleted; UI
  filter enum in TransactionsScreen renamed to `TransactionFilterStatus`
  (VM `setStatusFilter` takes it). DI merge (BackendModule → top-level
  `di/`) DEFERRED to step 3 — `ledger/di` for now. Compile + unit tests green.
- [x] **Step 3a DONE** — Room vocabulary renamed: `EntryEntity→TransactionEntity`
  (table `entries`→`transactions`), `EntryLineEntity→TransactionLineEntity`
  (table `transaction_lines`), `EntryProvenanceEntity→TransactionProvenanceEntity`
  (table `transaction_provenance`), DAOs `TransactionDao/TransactionLineDao/
  TransactionProvenanceDao` (fixed the old "Provance" typo), mapper
  `TransactionWithDetails`, columns `entry_id→transaction_id`,
  `linked_entry_id→linked_transaction_id` (+ property `linkedTransactionId`),
  DAO methods `getByTransaction/getByTransactionList/deleteByTransaction/
  transactionIdsForAccount/bucketAllocationTransactionIds`. Room v15 still
  (destructive policy). Compile + unit tests green.
  GOTCHA encountered: `\bentries\b` rename also hit Kotlin enum companion
  `.entries` in 7 UI/ledger files (AccountType/PartyFilter/ThemeMode/
  OtherSide/SystemRole/Screen/Destination) — reverted those.
- [x] **Step 3b-1 DONE** — account node unification (Rust schema):
  `AccountEntity` rewritten to class/subtype/parentId/isSystem/targetPaise/
  isArchived (+`bank` Android extension); `slug/kind/last4/platform/
  openingBalancePaise` dropped; `creditLimitPaise/reconciledThrough` → new
  `BankAccountDetailsEntity` side table (Room v16, destructive).
  `CategoryEntity`/`BucketEntity` + DAOs + CategoryRepository/mapper DELETED —
  categories are accounts (class income/expense), buckets are child accounts
  (subtype "bucket", `AccountDao.getChildren`). Lines dropped
  categoryId/bucketId (pure-ish; counterpartyId/balanceAfterPaise remain for
  3b-2). `LedgerService` rewritten: SystemRole pots via is_system+subtype
  (no slugs), system categories = system accounts, PL classification by
  account class, kind→shape validation kept, `bucketValue`/
  `recordBucketAllocation` REMOVED from the API (bucket = child balance),
  `recordOpeningBalance(accountId, amountPaise, onDate)` explicit amount.
  `IngestionService`: KIND_BRIDGE is class-level + card-SMS gate (card SMS
  rides an asset account only via a CARD-kind identifier — restores the old
  "credit-card sms never attaches to savings" rule); orphan-claim leg fix
  (parked leg = unmatched pot line). Counterparties gained
  `receivable_account_id` + `default_account_id` (column only; loan posting
  rewires in 3b-2). All VMs/repos/screens updated ( Accounts/Categories/
  Budget/Scanner/Transaction/Transactions/Inbox/Onboarding/AccountEdit).
  Compile + unit tests green.
  Notes for 3b-2: equity pots stay TWO subtypes until stored kind lands
  (buildView can't tell opening from reconciliation on one shared pot);
  bucket allocation is a plain transfer DOWN to the child (funding account
  balance drops, net worth unchanged — accepted semantics, tested).
- [ ] **STEP 3b-2 — NEXT** (see checklist below; equity merge + stored kind
  + pure lines + @DatabaseView + groups + budgets period + receivable posting)
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

## STEP 3b checklist (the structural reshape — NEXT SESSION, do in order)

State at handoff: steps 1, 2, 3a committed (HEAD `587a0b3`), compile + tests
green, Room v15 destructive. Everything below was scoped against the real
code on 3a's HEAD.

**3b-1: Account node unification** (blocks everything else)
- Rewrite `AccountEntity` to the Rust shape: `name`, `class`
  (asset/liability/equity/income/expense), `subtype` (savings/current/cash/
  wallet/credit_card/investment + receivable/unmatched/equity for
  `is_system=1`), `parentId`, `isSystem`, `sortOrder`, `icon`,
  `isArchived`, `targetPaise` (bucket extension), `createdAt`.
- DROP from AccountEntity: `slug`, `kind`, `platform`, `openingBalancePaise`,
  `last4`, `closedAt`, `reconciledThrough` (→ side table
  `BankAccountDetailsEntity` keyed by accountId: bank, creditLimitPaise,
  statementDay, dueDay, reconciledThrough). No `userId` — single-user app,
  documented deviation from Rust.
- Replace `SystemRole` slug-prefix parsing (LedgerService.kt:546 area,
  slugs `sys-openeq-/sys-reconeq-/sys-loans-/sys-unmatched-/sys-invest-`)
  with `is_system`+`subtype` lookup. NOTE: Kotlin has TWO equity pots
  (OPEN_EQUITY, RECON_EQUITY) — Rust merged them into ONE `equity` pot;
  adopt the merge, `kind` on the transaction already distinguishes
  opening/reconciliation.
- `IngestionService.KIND_BRIDGE` maps parser kinds → account.class/subtype:
  "credit_card"→class liability OR asset (identifier-kind check decides),
  "savings"→subtype savings/cash (+drop legacy "available").
- `recordOpeningBalance`: opening balance is a `kind='opening'` transaction
  vs the equity pot (delete `openingBalancePaise` from entity; keep the
  value on the account-edit UI as a one-time action).
- Categories are accounts: DELETE `CategoryEntity`+`CategoryDao` storage.
  Provide a thin façade DAO (`CategoryDao` name kept, querying accounts
  WHERE class IN ('income','expense')) ONLY if screen churn gets too big —
  preferred: update the 5 call sites (CategoriesViewModel, BudgetViewModel,
  TransactionViewModel/OtherSideSelector, BudgetDao queries, LedgerService
  system categories). `is_excluded` has NO Rust equivalent — decide: drop
  (transfers are already kind=transfer) — recommended drop.
- Buckets = child accounts (DECIDED): DELETE `BucketEntity`+`BucketDao`+
  `bucketId` on lines; bucket = accounts row (parentId=funding account,
  targetPaise set); `recordBucketAllocation` → idempotent `kind='transfer'`;
  bucket value = account balance. Delete
  `TransactionLineDao.{sumConfirmedForBucket,detachBucket,getByBucket}` and
  `TransactionDao.bucketAllocationTransactionIds`. UI: hide bucket children
  from account pickers.
- Counterparties: add `receivable_account_id` (the one ledger bridge —
  Android lacks it; per-person child accounts under the receivable pot).
  `first_seen/last_seen` and alias `source` ALREADY exist on Android.

**3b-2: Pure lines + stored kind** (after 3b-1)
- `TransactionLineEntity` → pure (id, transactionId, accountId, amountPaise).
  Drop categoryId/bucketId/counterpartyId/balanceAfterPaise columns. A
  category leg becomes a line whose accountId is a class=income/expense
  account. Move SMS-stated balance to
  `TransactionProvenanceEntity.statedBalancePaise` (drop
  `balanceAfterPaise` from lines; `LineSpec.balanceAfterPaise` moves to
  provenance in the API).
- `TransactionEntity` gains stored `kind` (Rust 9-value vocabulary:
  expense, income, transfer, loan, loan_repayment, split, investment,
  opening, reconciliation). Current Kotlin enum has ALLOCATION+OTHER —
  ALLOCATION dies with buckets (becomes transfer); drop OTHER or map to
  expense. Kind is computed ONCE at write time in the
  createTransaction/ingest paths (move the read-time derivation out of
  `describeTransaction`); `kind` is never re-derived on read. Add
  kind→shape validation (sum-zero + the matrix from the Rust
  DESIGN_DECISIONS.md) inside createTransaction.
- Add `@DatabaseView` mirroring `v_account_balances` (balance + liability
  sign flip); rewire `accountBalance`/`bucketValue` to it; delete
  `sumConfirmedForAccount` ad-hoc sums (invariant: one balance read path).
- Add `GroupsEntity`+`GroupMemberEntity`+DAO (group splits can't persist
  today; Rust migration 004 is the reference). Budgets → monthly `period`
  TEXT ('YYYY-MM') keyed (accountId, period); drop effective_from/to
  (BudgetViewModel + BudgetDao adapt).

**3b-3: errors + tests**
- Sealed error types on the LedgerApi seam: UnbalancedLines,
  DuplicateDedupeHash, EquityGuardViolation, InvalidLifecycleTransition
  (replaces bare `ApiException` strings in ledger/).
- Tests to rewrite in `app/src/test/java/com/example/spendwise/ledger/`:
  BackendTestBase (seed data now: accounts w/ class/subtype + pots),
  BalanceTest (liability sign via view), BucketTest (bucket as child
  account), IngestTest/IngestionServiceTest (KIND_BRIDGE, orphan pot),
  DescribeEntryTest (stored kind; ALLOCATION/OTHER expectations gone),
  LedgerValidationTest (kind→shape matrix), SplitLifecycleTest,
  CounterpartyServiceTest, ContactsServiceTest.
- Rust cleanup NOT to port: 008 global contacts table; `user_id` columns
  (single-user, documented deviation).

## STEP 5 checklist (UI)

`MoneyText` composable (semantic colors, tnum, count-up) → typography with
res/font (Outfit/Manrope/Space Grotesk) → NavHost slide+fade transitions →
`animateItem()`/`animateContentSize()` → hand-rolled Canvas charts (30-day
bars, category donut) → empty states on Transactions/Inbox/Friends/Budget →
rename screens transaction/→TransactionDetail, update/→TransactionEdit;
split ScannerScreen (1,164 lines).

