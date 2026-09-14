# SpendWise — honest project report & change list

Read against both codebases: `spendwise` (Android/Kotlin, offline-first) and
`backend` (Rust + axum + sqlx + SQLite). This is a report, not a refactor —
nothing was changed in code.

---

## 0. TL;DR

| # | Your issue | Actual root cause |
|---|---|---|
| 1 | UI looks dead | The theme layer is genuinely good ("Emerald Ink" palette + semantic colors + dark mode). What's missing is typography, motion, charts, and money-rendering components — the screens are 700-line flat M3 lists. |
| 2 | Structure eats your brain | Your layer-based MVVM is fine and worth keeping. The confusion comes from **two parallel stacks living in the app at once**: the legacy flat `Transaction*` stack (`TransactionEntity → TransactionDao → TransactionRepository`, still in Room v14 and DI) and the new `Entry*` ledger stack, plus a package literally named `backend` inside the app, plus DI split across `core/di` and `backend/di`. Kill the legacy stack and rename things — don't restructure. |
| 3 | "entries" terminology | The Rust backend already speaks your language: `transactions` / `transaction_lines` / `transaction_provenance`. The Android side is the one using `entry*` everywhere. Rename the Android ledger API layer to match — one-time mechanical rename, UI unchanged. |
| 4 | types don't matter, associations do | Agreed. The good news: the Rust schema's *associations* are a strict improvement (pure lines, one target per line). But three Android concepts have **no home** in the Rust schema yet: buckets, `linked_entry_id`, and per-line `balance_after_paise` (see §3). |
| 5 | redefined/missed SMS fields | Correct — the Rust repo is a schema + CRUD skeleton. Direction confirmed: **Rust → Kotlin** (port the design, reshape Kotlin code). The flows that matter (ingest, matching, split, opening balance, friends) already live in Kotlin and get kept/reshaped — §3 is what to take from Rust and what to ignore. |

---

## 1. The UI ("dead, no vibe")

The infrastructure is better than you think — `ui/theme/Color.kt` is a real
brand palette (emerald on warm paper / green-black ink), `Theme.kt` already
exposes `SpendwiseTheme.colors.income/expense/transfer/warning` semantic
colors, and dynamic color is correctly disabled. Nothing is wrong with the
palette. What makes it feel dead is that none of it is being *used* with
intention, and there is zero motion. Changes required, in order of impact:

### 1.1 Typography (biggest single lever)
- `Type.kt` is stock Material defaults. Add a real font family
  (`res/font/`, e.g. a variable-weight geometric sans — Outfit / Manrope /
  Space Grotesk) and define a `Typography` with tight, oversized headlines.
- Money must render in **tabular figures** (`fontFeatureSettings = "tnum"`)
  or every list jiggles as values scroll.

### 1.2 A `MoneyText` component
- One composable used everywhere: `MoneyText(amountPaise, semantic =
  INCOME/EXPENSE/NEUTRAL)` that pulls from `SpendwiseTheme.colors`, formats
  `₹1,234.56` with an `Intl`-style formatter, supports size variants
  (hero/balance/line-item), and animates value changes
  (`animateFloatAsState` count-up). `ui/components/` has 13 components and
  none of them is this. This alone makes every screen feel designed.

### 1.3 Motion (all native Compose, no new dependency)
- `NavHost` currently has default (instant) transitions: add
  slide+fade enter/exit transitions.
- List insert/remove: `animateItem()` on LazyColumn items.
- Expanding cards / sheets: `animateContentSize()`.
- Buffer→confirmed confirm action in the Inbox should have a satisfying
  completion animation — it's your app's most-used gesture.

### 1.4 Charts (hand-rolled Canvas; no new dependency needed)
- A 30-day spend bar chart on the transactions header.
- A category donut on the budget screen (`BudgetProgress` exists but is
  linear). You already depend on `material3` + `ui.graphics`; ~150 lines of
  Canvas each. (If you'd rather not hand-roll, Vico is the fitting chart lib
  — but it's a new dependency, flagged as optional.)

### 1.5 Surface rhythm & empty states
- Use the `surfaceContainerLowest..Highest` steps you already defined for
  card hierarchy instead of flat `surface` everywhere.
- Every screen needs a designed empty state (icon + one-liner + action) —
  Transactions, Inbox, Friends, Budget. Empty list = dead screen.

### 1.6 Dead screens to consolidate
- `ui/screens/transaction/` vs `ui/screens/transactions/` (two similarly
  named screens, 716 + 767 lines), and `update/` — rename to what they do
  (`TransactionDetailScreen`, `TransactionsScreen`, `TransactionEditScreen`).
- `ScannerScreen.kt` is 1,164 lines — split the preview/compose overlay out
  of the analysis logic.

## 2. Structure ("which goes where")

You're right to reject feature-based packages — agreed, not advising it.
Your layer-based MVVM is the correct shape **and it is not actually broken**.
The confusion is coming from four concrete, fixable things:

### 2.1 Two parallel stacks (the real brain-eater)
Room v14 registers **both** the legacy flat model and the new ledger:

- Legacy: `TransactionEntity` (Double amount! `merchantName`, comma-string
  tags, `sourceSmsBody`, `isDeleted`) → `TransactionDao` →
  `TransactionRepository` → wired in `core/di/DatabaseModule.kt`.
- New: `EntryEntity`/`EntryLineEntity`/`EntryProvenanceEntity` →
  `EntryDao`/… → `backend/service/LedgerService` (the real ledger, tests
  live under `backend/`).
- Plus `MarkerEntity` and `TransactionViewModel` vs `TransactionsViewModel`
  vs `UpdateViewModel` whose ownership is unclear.

**Change required:** delete the legacy stack
(`TransactionEntity`, `TransactionDao`, `TransactionRepository`,
`MarkerEntity`) — bump Room version with a drop-tables migration. One ledger,
one vocabulary. Verify nothing in `ui/` still binds `TransactionRepository`
before deleting.

### 2.2 A package named `backend` inside an Android app
`com.example.spendwise.backend.{api,service,di}` is not a backend — it's your
**domain/ledger layer** (LedgerService, IngestionService,
ReconciliationEngine, CounterpartyService). The name is why it "keeps
slipping away": the same word points at two different repos.

**Change required:** rename the package `backend` → `ledger`
(`ledger/api`, `ledger/service`). Merge `backend/di/BackendModule.kt` and
`core/di/{AndroidModule,DatabaseModule}.kt` into a single top-level `di/`
package — two DI roots is two places to look for "who provides what".

### 2.3 Target tree (layer-based, same as today, just honest names)

```
com.example.spendwise/
├── MainActivity.kt
├── navigation/
├── di/                 # ONE module set (merged from core/di + backend/di)
├── core/               # platform adapters, no business rules
│   ├── sms/            # SmsBroadcastReceiver, SmsSyncWorker
│   ├── contacts/       # ContactReader
│   ├── messages/ notifications/ extensions/
├── parser/             # bank SMS parsers (rename of core/parser_pw)
├── ledger/             # THE domain core (rename of backend/)
│   ├── api/            # LedgerApi + models — the REST-shaped contract
│   └── service/        # LedgerService, IngestionService, ReconciliationEngine, ...
├── data/               # Room only: entity/ dao/ (+ the 3 small mappers)
├── viewmodel/          # 13 VMs, flat is fine at this size
└── ui/                 # screens/ components/ theme/
```

Dependency rule (write it in the README): `ui → viewmodel → ledger → data`,
and `core`/`parser` feed the ledger only through `ledger/api`. Nothing in
`ui` ever touches a DAO directly. That's the whole rule.

### 2.4 ViewModel naming
- `TransactionsViewModel` (list) vs `TransactionViewModel` (detail) vs
  `UpdateViewModel` (edit?) — rename to role, not noun-plural:
  `TransactionsListViewModel`, `TransactionDetailViewModel`,
  `TransactionEditViewModel`. Cheap, kills a daily ambiguity.

## 3. Backend parity — what you redefined / missed (field-by-field diff)

The Rust backend is **not** a "new version of the backend" yet — it's the
redesigned *schema* plus CRUD services over it. Everything the Android app
does after "I have a parsed SMS" is missing. Itemized:

### 3.1 Flows with no Rust counterpart (Rust repo's gap list — not this project's)
Under the confirmed direction (Rust = design reference, Kotlin = the
implementation), this table records the Rust repo's gaps so its state is
readable at a glance. Every row already exists, tested, in Kotlin, and gets
kept/reshaped — nothing here is work for this project unless the Rust server
ever becomes the runtime.

| Kotlin (`backend/service`) | Rust status |
|---|---|
| `IngestionService.ingestSms` — dedupe → account match (bank + last4, kind-aware, channel-aware) → counterparty resolve (VPA first) → default intent → buffer + provenance → QR merge | ❌ nothing. No `/ingest` route. `account_identifiers` table exists, **0 references in `src/`** |
| Orphan parking on the `unmatched` pot + retroactive claiming (`assignAccount`) | ❌ (the pot itself is modeled in the schema — good) |
| QR-scan merge (±1% symmetric + 2% increase tolerance, 30-min window, notification 3-min window) | ❌ |
| `splitEntry` — carve receivable shares, `ensurePartyType(person)` | ❌ no split route (the `split` *kind* exists in the CHECK; the flow isn't implemented) |
| `recordOpeningBalance` — idempotent `kind='opening'` vs equity pot | ❌ |
| `friendsOutstanding()` — net per person from the receivable subtree | ❌ |
| `Reconciliation` engine — statement diff, `reconciled_through` watermark, continuity check | ❌ (anchor column exists in `bank_account_details`, unused) |
| `account_identifiers` / `bank_account_details` CRUD | ❌ tables only, no service/routes |

### 3.2 SMS-specific fields you dropped — decide each explicitly
These exist on Android and have **no Rust equivalent**:

1. **`parsed_facts` on provenance** — frozen parse facts
   (`bank|last4|accountKind`) so orphan reclaim re-runs account matching
   without re-parsing the raw SMS. *Add `parsed_facts TEXT` to
   `transaction_provenance`.*
2. **`balance_after_paise` on lines** — the bank-stated "avail bal" from
   the SMS. *Move to provenance (`stated_balance_paise`), not the line —
   it's a fact about the message, not a posting. Also the cheapest
   reconciliation drift detector you have.*
3. **`linked_entry_id`** (self-FK on entries, used by split lifecycle /
   merged-QR bookkeeping) — nothing in Rust. If the Rust split flow is
   implemented as lines on *one* transaction (which the schema prefers),
   this becomes unnecessary — decide at split-implementation time, don't
   port blindly.
4. **Counterparty learning fields**: Android has `default_tags`,
   `default_intent`, `default_category_id`, `first_seen`, `last_seen`;
   Rust has none of them. *Add `first_seen`/`last_seen` (genuinely useful
   for SMS-driven counterparty ranking); defer the `default_*` trio (UX
   convenience).*
5. **`counterparty_aliases.source`** — Android records where an alias came
   from (`sms`/`qr`/`manual`); Rust's alias table lacks it. One column, add.
6. **`categories.is_excluded`** — categories are now `accounts` of class
   `income/expense`; there is no "exclude from spend reports" flag.
   Transfers-to-self are `kind='transfer'` so the main case is covered;
   decide whether you need exclusion for the rest (e.g. investments).
7. **Buckets — the biggest semantic gap. DECIDED: buckets become child
   accounts.** Android buckets are sub-pots inside an account (target,
   manual allocation, lines tagged `bucket_id`). Rust deliberately has no
   buckets; migration 006's escape hatch is a child account under the
   funding account. **Option A is adopted**: a bucket is an ordinary
   `accounts` row with `parent_id` = the funding account; allocation is a
   self-transfer transaction (funding account leg −, child-account leg +);
   a bucket's value is simply the child account's balance. No `bucket_id`
   on lines — that's the polymorphism the redesign killed. Budgets stay
   period caps per category (monthly `period` rows).
8. **Tags**: Android stores comma-strings on the entry; Rust has the
   `transaction_tags` join table. Rust is right — drop the string column
   during the rename.
9. **Kinds**: Android derives `EntryKind` from lines (11 values incl.
   `ALLOCATION`, `OTHER`); Rust *stores* `kind` (9 values), written once,
   never re-derived. Rust's is the better contract — port Android code to
   *read* `kind` instead of re-deriving.

### 3.3 Rust-side cleanup (found while diffing)
- **`008_create_contacts.sql` contradicts the design.** It creates a global
  `contacts(id, name, phone, email)` with a global phone unique index,
  while `003` already has the per-user `user_contacts(user_id,
  phone_last10)` — and `main.rs` mounts *both* `/contacts` and
  `/user-contacts`. The Android contact cache is
  `(phone_last10, display_name, photo_uri)`. **Fix: drop migration 008 and
  the `contacts` routes/service; keep `user_contacts`, add `photo_uri` +
  `created_at` to it.** (Your own DESIGN_DECISIONS flags the contacts
  starter as cleanup.)
- **Groups**: Rust has `groups`/`group_members` — the Android schema has
  *no* group table (`group_id` FK is commented out in `EntryEntity`). The
  app-side gap is real: group splits can't persist today. Port direction:
  Rust schema → Android.
- **Budgets**: Android uses `effective_from/effective_to` date ranges; Rust
  uses one row per month (`period 'YYYY-MM'`, unique per account). Rust is
  cleaner — adopt its shape on Android.
- `Cargo.toml` declares `utoipa` + `utoipa-swagger-ui` but `main.rs` never
  mounts Swagger — dead weight or unfinished TODO; pick one.
- `docs/error-handling-plan.md` (the A/B/C decision) is **internal to the
  Rust repo and not this project's concern**: Kotlin has no HTTP error-
  mapping problem — the ledger is in-process, with `ApiException`/sealed
  results at the `LedgerApi` seam. The only thing worth lifting from that
  discussion is the *inventory of error cases* (unbalanced lines, duplicate
  `dedupe_hash`, equity-guard rejections, buffer→confirm/void lifecycle
  conflicts) — encode those as Kotlin sealed types when the ingest path is
  reshaped, and ignore the Rust plumbing.

### 3.4 Dates (explicitly a non-issue, one paragraph as agreed)
Android `Long` epoch-millis vs Rust ISO `TEXT` — irrelevant, it's an adapter
concern at the seam. The only rule worth keeping: business date
(`occurred_on`) and wall-clock moment (`happened_at`) stay *two distinct
fields* on both sides, as they are today. Where it actually matters, the
associations are unchanged and correct: header → lines (1:N, cascade),
header → provenance (1:1), header → counterparty/group (N:1), counterparty →
receivable child account (the one ledger bridge), lines → accounts (N:1,
the only line target).

---

## 4. Recommended order of attack

1. **Android: delete the legacy stack** (`TransactionEntity`,
   `TransactionDao`, `TransactionRepository`, `MarkerEntity`). Early-stage
   nuking makes this trivial: delete the classes, bump the Room version
   with `fallbackToDestructiveMigration()` — no drop-table SQL, no
   migration history to keep.
2. **Android: rename `entry*` → `transaction*` in the ledger API layer**
   and package `backend` → `ledger`. Mechanical; viewmodels/UI follow the
   API names.
3. **Kotlin: rebuild the schema the Rust way (nuking is fine this early)**
   — since data is disposable, don't write migrations: bump the Room
   version, enable `fallbackToDestructiveMigration()`, delete
   `MIGRATION_11_12/12_13/13_14`, and reshape entities fresh: stored `kind`
   instead of line-derived `EntryKind`; pure lines — accounts become the
   only line target, with categories unified into `accounts` of class
   `income`/`expense` (the big reshape; model `v_account_balances` as a
   Room `@DatabaseView` so balances keep a single read path); provenance
   gains `parsed_facts` + `stated_balance_paise`; counterparties gain
   `first_seen`/`last_seen`, aliases gain `source`; add
   `groups`/`group_members` (group splits can't persist today); adopt
   monthly `period` budgets; keep the `phone_last10` contacts cache — do
   NOT port the 008 global-contacts shape.
4. **Buckets = child accounts (DECIDED, Option A)** — folded into the
   step-3 rebuild: drop the `buckets` table and `bucket_id` on lines; a
   bucket is an `accounts` row with `parent_id` = funding account (add
   `target_paise` to accounts for the envelope target); `recordBucketAllocation`
   becomes an idempotent self-transfer to the child account; bucket value =
   the child's balance via the same `@DatabaseView`. Decide-before-rebuild
   is now satisfied.
5. **Android UI pass** per §1 — `MoneyText` + typography + nav transitions
   first (they touch every screen during the rename anyway), charts and
   empty states last.

One sentence to keep in view: **the Rust repo's deliverable to this project
is its schema plus the settled questions in `DESIGN_DECISIONS.md` (Q1–Q14) —
copy those two docs next to the Kotlin ledger; they're what stops you
re-litigating design. The remaining work is reshaping Kotlin to that
schema, not redesigning again.** The invariants Room can't express
(sum-zero, kind→shape matrix, idempotent ingest) are listed in
`migrations/README.md` under "App responsibilities" — that checklist becomes
your `LedgerService` contract, in Kotlin, on your terms.



