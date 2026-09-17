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
- [x] **STEP 3b-2 DONE** — pure lines + stored kind + annotations (Rust 004/006):
  - **Pure lines**: `TransactionLineEntity` = (id, transactionId, accountId,
    amountPaise) — categoryId/bucketId/counterpartyId/balanceAfterPaise all
    dropped. SMS-stated balance → `TransactionProvenanceEntity
    .statedBalancePaise` (`LineSpec.balanceAfterPaise` moved to provenance in
    the API; ReconciliationEngine carries it on its request struct, never
    stored on lines).
  - **Stored kind**: `kind: String` on `TransactionEntity` (Rust 9-value
    vocabulary). Computed ONCE at write time in createTransaction/ingest;
    `describeTransaction` no longer re-derives. Kind→shape validation matrix
    in createTransaction. ALLOCATION enum value died (→ transfer), OTHER
    dropped.
  - **Equity merge**: the two equity pots (OPEN_EQUITY/RECON_EQUITY subtypes)
    merged into ONE `equity` subtype pot — unblocked by stored kind.
  - **@DatabaseView**: `AccountBalanceRow` mirrors `v_account_balances`
    (balance + liability sign flip); `accountBalance` rewired to it; ad-hoc
    `sumConfirmedForAccount` deleted (one balance read path).
  - **Tags → join tables** (006 port, added scope at user's direction):
    `TagEntity(id, name UNIQUE)` + `TransactionTagEntity(transactionId, tagId,
    PK both, CASCADE, tag_id index)` + `TagDao` (insert/idByName/all/
    usageCount). Room v17 destructive. Tag picker/suggestions query the join
    tables; `TagCodec` deleted (zero remaining references).
    **DEVIATION**: no data backfill — existing `tags` JSON strings on
    transactions are dropped on upgrade (pre-release, destructive policy,
    dev data only).
  - **Groups** (004 port): `GroupEntity(groups)` + `GroupMemberEntity
    (group_members, PK (group_id, counterparty_id), CASCADE both ways)` +
    `GroupDao`. Splits can reference a group; member shares post onto each
    counterparty's receivable child account.
  - **Budgets**: monthly `period` TEXT 'YYYY-MM' keyed, unique
    (account_id, period); `effective_from/effective_to` dropped (BudgetDao +
    BudgetViewModel adapted).
  - **Receivable posting**: loan/split legs post onto
    `counterparties.receivable_account_id` child accounts (the ledger bridge
    3b-1 added as a bare column now used).
  - Tests rewritten (stored kind, view-based liability balance, group/tag
    seams): **91 tests, 0 failed**. `:app:assembleDebug` green.
- [x] **Step 5 DONE (UI pass)** — done ahead of the 3b wave at the user's
  request; 3b-3 has since landed (see its DONE block below).
  - **Typography**: `res/font/outfit_{regular,medium,semibold,bold,extrabold}.ttf`
    (static Outfit instances, downloaded from Fontsource; chosen because Outfit
    ships **true tabular figures** — verified `tnum` present in GSUB).
    `ui/theme/Type.kt` rewritten to the Outfit family with tight, oversized
    headlines.
  - **`MoneyText`** (`ui/components/MoneyText.kt`): the single money renderer.
    `MoneySemantic` (INCOME/EXPENSE/TRANSFER/NEUTRAL/WARNING) pulls from
    `SpendwiseTheme.colors`; `MoneySize` (HERO/BALANCE/TITLE/HEADING/BODY/LABEL)
    maps onto the type scale; `tnum` applied; `animateFloatAsState` count-up on
    value change; Long (paise) and String overloads.
    GOTCHA FOUND + FIXED: `DecimalFormat("##,##,##0")` does **not** produce
    Indian grouping on the Android runtime (returns `153250` with no
    separators). Replaced with a hand-rolled `groupIndian()` (last 3, then
    pairs) — deterministic, locale-free. Pinned by `MoneyFormatTest`.
  - **`EmptyState`** (`ui/components/EmptyState.kt`): shared medallion +
    title + one-liner + optional action, with a spring-in on first composition.
    Wired into **Transactions, Inbox, Friends, Budget** (each keeps its own copy
    and action).
  - **Motion**: `Modifier.animateItem()` on the Transactions, Inbox, Friends and
    Budget lists; `animateContentSize()` on `BudgetCategoryCard`; the Inbox
    approve tick now pops (`spring`) for `APPROVE_ANIMATION_MILLIS` before the
    import fires, then the row animates away via `animateItem`.
    NavHost slide+fade transitions were **already present** in `AppNavHost`
    (directional slide) and `MainNavHost` (tab crossfade) — verified, no change
    needed.
  - **Charts** (hand-rolled Canvas, no new dependency):
    `ui/components/SpendBarChart.kt` (30-day spend bars, today accented,
    grow-in) shown in a new `SpendSummaryHeader` at the top of
    TransactionsScreen (fed by the new numeric `TransactionUi.amountPaise`);
    `ui/components/CategoryDonut.kt` (animated donut + legend via
    `donutPalette()`) shown in a new `CategoryBreakdownCard` on Budget.
  - **Renames**: `ui/screens/transaction/` → `ui/screens/transactiondetail/`
    with `TransactionScreen` → `TransactionDetailScreen` (matches the screen's
    own "Transaction Details" title); package + all 6 call sites updated.
    **DEVIATION**: the checklist said `update/`→TransactionEdit — WRONG:
    `ui/screens/update/UpdateScreen.kt` is the in-app **APK updater**
    (`Screen.Update`, `UpdateViewModel`), not a transaction editor, so it was
    deliberately left alone. `ui/screens/transactions/` keeps its name (plural
    = the list).
  - **ScannerScreen split**: 1,164 → **723 lines**. Extracted, verbatim:
    `UpiQr.kt` (UpiTarget/parseUpiQr/buildUpiUri — pure, now unit-tested),
    `ScannerOverlays.kt` (QrScanOverlay, reticle/bracket drawing,
    FocusPulseRing, ScannerStatusPill), `PaymentOverlay.kt` (payment form,
    OptionPickerSheet, SavedConfirmationSheet). ScannerScreen keeps the screen +
    CameraX/ML Kit pipeline. Moved composables that the screen still calls went
    `private`→`internal`.
  - **Money formatting migration**: every `"%,.0f"/"%,.2f"` money string is gone
    from `main/` (Accounts, Transactions, Inbox, Friends, Budget, Categories,
    Counterparties, Budget* components, FriendCard, TransactionListItem).
    Composites ("₹x of ₹y") use the shared `formatRupees()`.
    AccountsScreen's hardcoded `Color(0xFF15803D)` for assets now uses
    `MoneySemantic.INCOME`.
  - **Tests added**: `MoneyFormatTest` (5) + `UpiQrTest` (7, Robolectric, incl. a
    build→parse round-trip). Full `:app:testDebugUnitTest` +
    `:app:assembleDebug` green.

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

**3b-3: errors + tests — ✅ DONE** (see Status; sealed hierarchy on the
LedgerApi seam, message-based `ApiException` gone):
- `ApiException` is now **sealed** with `UnbalancedLines`,
  `DuplicateDedupeHash`, `EquityGuardViolation`, `InvalidLifecycleTransition`,
  plus `NotFound(what, id)` and `InvalidRequest` for the sites the four named
  types can't honestly cover (bad intent/amount, "account X not found").
  `message` stays human-readable; callers can finally branch on cause.
- All ~40 throw sites across LedgerService / IngestionService /
  ContactsService / CounterpartyService retyped via explicit mapping script
  (nothing was left as a bare constructor). The 4 VM-side constructions
  (AccountEditViewModel, FriendsViewModel) moved to NotFound/InvalidRequest.
- **Two invariant upgrades that fell out of the typing**:
  1. `createTransaction`/`ingestTransfer` now enforce dedupe at the write
     path (`requireFreshDedupeHash` → `DuplicateDedupeHash`) — previously
     only `IngestionService` pre-checked, so a direct `ingestTransfer` with a
     seen hash silently wrote a duplicate.
  2. `confirmTransaction` refuses to resurrect a **voided** transaction
     (`InvalidLifecycleTransition`) — previously it silently re-confirmed it.
- `ReconciliationEngine`'s `require(...)` stays (parse-layer input shape,
  not an API invariant). No production caller branched on `ApiException`
  type (verified: all catches are `catch (e: Exception)`), so sealing is
  non-breaking; VMs surface `e.message` as before.
- Tests: `expectApiError` gained an optional `KClass` narrowing param
  (old call sites unchanged) + `expectApiErrorOf<T>()` sugar. 5 new tests in
  LedgerValidationTest: duplicate dedupe hash, confirm-after-void,
  equity-leg-without-kind, transfer-with-terminal-leg, NotFound on confirm.
  **96 tests, 0 failed**; `:app:assembleDebug` green.
- Rust cleanup NOT to port: 008 global contacts table; `user_id` columns
  (single-user, documented deviation).

## STEP 5 checklist (UI) — ✅ DONE (see Status above)

`MoneyText` composable (semantic colors, tnum, count-up) → typography with
res/font (Outfit/Manrope/Space Grotesk) → NavHost slide+fade transitions →
`animateItem()`/`animateContentSize()` → hand-rolled Canvas charts (30-day
bars, category donut) → empty states on Transactions/Inbox/Friends/Budget →
rename screens transaction/→TransactionDetail, update/→TransactionEdit;
split ScannerScreen (1,164 lines).

Corrections to this checklist, confirmed against the code:
- `update/` is the **in-app APK updater**, not a transaction editor — NOT renamed.
- NavHost slide+fade was already implemented — nothing to do.
- Remaining UI polish not in this pass (candidates for a follow-up): the
  Transactions filter sheet's dropdowns are still non-functional stubs
  (`onClick = {}`), and `ui/components/AmountSection.kt` (used by
  `TransactionDetailScreen`) still takes a pre-formatted amount string rather
  than paise.
- DEAD CODE found, left in place (not deleted without asking):
  `ui/components/BudgetProgress.kt` has **zero references** anywhere in
  `app/src` — the new `CategoryDonut` is the budget visualisation now, so this
  file is a deletion candidate.

