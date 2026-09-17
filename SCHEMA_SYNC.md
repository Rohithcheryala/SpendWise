# SCHEMA_SYNC — SpendWise Room schema vs Rust reference migrations

Status: **deferred — none of these are breaking changes; take them up later.**

This file records the *diff* between the Kotlin/Room schema (the
implementation) and the Rust reference migrations at
`~/code/active/backend/migrations` (design reference only — see
`STEP_TRACKER.md` "Direction & settled decisions"). It exists so a future
session does not re-derive the diff, and so the accepted deviations are
distinguishable from the accidental gaps.

Reference set compared:

- Rust: `migrations/001..008` + `README.md` ("Invariants", "App
  responsibilities") + `DESIGN_DECISIONS.md` (Q1–Q14, kind→shape matrix).
- Kotlin: every `@Entity`/`@DatabaseView` in
  `app/src/main/java/com/example/spendwise/data/database/entity/`, verified
  against the KSP-generated
  `app/build/generated/ksp/debug/kotlin/com/example/spendwise/data/database/AppDatabase_Impl.kt`
  (Room `version = 17`, identity hash `e31895baab144e51dfa96695edc07053`).

Verdict at time of writing: **tables and columns are essentially fully
ported; the constraint/index layer of migrations 002/005 is not.** Bumping
Room for any item below is free (destructive migration policy — data is
disposable, see `DatabaseModule.kt`).

---

## 1. Non-deferred divergences (work later)

These are *not* explained by any deferral note. Most are expressible in Room
(one-line `@Entity` additions) and would be a single Room version bump.

### G1. `accounts` has no `UNIQUE(name)`

- **Rust:** `UNIQUE (user_id, name)` (`002_create_accounts.sql:53`).
- **Kotlin:** `AccountEntity` declares only non-unique indices — `parent_id`,
  `account_class`, `(is_system, subtype)`, `bank`
  (`entity/AccountEntity.kt:10-15`). Generated DDL confirms no unique index.
- **Impact:** duplicate account names are allowed; user-facing ambiguity.
- **Fix:** add `Index(value = ["name"], unique = true)` to `AccountEntity`
  (single-user, so drop `user_id` from the key as elsewhere).

### G2. `accounts.parent_id` has no self-referencing FK

- **Rust:** `parent_id INTEGER REFERENCES accounts(id)`
  (`002_create_accounts.sql:46`).
- **Kotlin:** `AccountEntity` declares no `foreignKeys`; `parent_id` is a bare
  `Long?` (`entity/AccountEntity.kt:43-44`).
- **Impact:** orphaned parent ids; no cascade semantics for subtree deletes.
- **Fix:** add a self-`ForeignKey` on `parent_id` (Room supports self-refs).

### G3. `account_identifiers` has no `UNIQUE(account_id, kind, value)`

- **Rust:** `UNIQUE (account_id, kind, value)`
  (`002_create_accounts.sql:110`).
- **Kotlin:** only `Index("account_id")` and `Index(["value","is_active"])`
  (`entity/AccountIdentifierEntity.kt:27-30`).
- **Impact:** the same last-4 can be attached to one account twice; SMS
  matching can return duplicate rows (`AccountIdentifierDao.getActiveByValue`).
- **Fix:** add `Index(value = ["account_id","kind","value"], unique = true)`.

### G4. Identifier `kind` vocabulary differs from Rust

- **Rust:** `CHECK (kind IN ('upi','card_last4','account_last4','phone'))`
  (`002_create_accounts.sql:106`).
- **Kotlin:** `"account"` / `"card"` (`IngestionService.kt:356-357`;
  `entity/AccountIdentifierEntity.kt:43-44`).
- **Impact:** none functionally today (no CHECK), but the two schemas use
  different identifier vocabularies; `'upi'` / `'phone'` have no home.
- **Fix / decide:** either adopt Rust's 4-value vocabulary or write down that
  Android intentionally uses a 2-value channel vocabulary. (This is a
  *decision*, not a mechanical fix.)

### G5. `counterparty_aliases`: unique scope flipped, FK not CASCADE

- **Rust:** `UNIQUE (counterparty_id, alias_norm)`, FK `ON DELETE CASCADE`,
  index on `alias_norm` (`003_create_counterparties.sql:26-35`).
- **Kotlin:** `Index(value = ["alias_norm"], unique = true)` — **global**
  uniqueness — and the counterparty FK has no `onDelete`
  (`entity/CounterpartyAliasEntity.kt:9-22`).
- **Note:** global uniqueness is deliberate (comment at
  `CounterpartyService.kt:56`), but it is a different invariant than the
  reference and should be recorded as such.
- **Fix:** add `onDelete = ForeignKey.CASCADE`; decide + document the unique
  scope (global is defensible for single-user).

### G6. `transactions` lost both hot-path indexes

- **Rust:** `idx_transactions_user_date(user_id, occurred_on)` and
  `idx_entries_status(status)` (`005_create_transactions.sql:61-64`).
- **Kotlin:** indices only on `counterparty_id`, `group_id`,
  `linked_transaction_id` (`entity/TransactionEntity.kt:28-32`).
- **Impact:** `getAll()`, `getBetweenDates()`, `getByStatus()`
  (`TransactionDao.kt:15-59`) and `listSuspend()` full-scan.
- **Fix:** add `Index(value = ["occurred_on"])` and `Index(value = ["status"])`
  (single-user, so drop `user_id`).

### G7. `transaction_provenance` lost `idx_provenance_bank_ref`

- **Rust:** `CREATE INDEX idx_provenance_bank_ref ON
  transaction_provenance(bank_ref)` (`005_create_transactions.sql:86`).
- **Kotlin:** indices only on `transaction_id` and unique `dedupe_hash`
  (`entity/TransactionProvenanceEntity.kt:19-22`).
- **Impact:** `TransactionProvenanceDao.getByBankRef()` (`:49-55`) full-scans.
- **Fix:** add `Index(value = ["bank_ref"])`.

### G8. `v_unbalanced_transactions` view is missing

- **Rust:** the integrity probe view — "MUST always return zero rows"
  (`007_create_guards_and_views.sql:45-51`; `README.md` invariant).
- **Kotlin:** absent (`grep` → 0 hits). Unlike triggers, this **can** be
  expressed as a Room `@DatabaseView`.
- **Impact:** the sum-zero invariant has an app-side assertion
  (`LedgerService.kt:243-245`) but no schema-side probe artifact.
- **Fix:** add a `@DatabaseView` mirroring the Rust SQL and a health/test
  query asserting zero rows.

### G9. A 4th system pot (`investment`) the Rust model rejects

- **Rust:** exactly **three** reference pots; CHECK
  `is_system = 1 AND subtype IN ('receivable','unmatched','equity')`
  (`002_create_accounts.sql:57-63`). `'investment'` is a *user-account*
  subtype, not a pot.
- **Kotlin:** `SystemRole.INVESTMENT("investment", "Investments
  (unallocated)", CLASS_ASSET)` (`LedgerService.kt:751`), used at
  `LedgerService.kt:229` and `:450`.
- **Impact:** this row would violate the reference schema's two-way
  `is_system ⟺ pot-subtype` CHECK. Intentional Android addition — should be
  documented as an accepted extension, or folded into an existing pot.
- **Fix / decide:** document as extension, or remap unallocated investments.

### G10. `v_account_balances` is a collapsed shape

- **Rust:** exposes `account_id, user_id, class, name, balance_paise` (raw
  sign) **and** `signed_balance_paise` (liability flip)
  (`007_create_guards_and_views.sql:64-85`).
- **Kotlin:** exposes only `account_id, balance_paise` — and its
  `balance_paise` is actually Rust's `signed_balance_paise`
  (`entity/AccountBalanceRow.kt:13-37`).
- **Impact:** internally consistent (one sign convention), but not the same
  view contract; the raw balance is unreachable.
- **Fix / decide:** keep the simplification and document it, or restore both
  columns.

### G11. `bank_account_details` reshaped (unrecorded deviation)

- **Rust:** `bank`, `account_last4`, `credit_limit_paise`, `statement_day`,
  `due_day`, `reconciled_through` (`002_create_accounts.sql:84-94`).
- **Kotlin:** `bank` moved onto `accounts` (Android extension) and
  `account_last4` dropped (folded into `account_identifiers`)
  (`entity/BankAccountDetailsEntity.kt`; `entity/AccountEntity.kt:54-55`).
- **Impact:** none — but it is a schema difference worth writing down.

---

## 2. Accepted deviations / deferrals (context — not work)

Correctly out of sync by explicit decision; listed so they are not mistaken
for gaps.

| Item | Why | Recorded at |
|---|---|---|
| No `users` table / no `user_id` columns | Single-user app | `STEP_TRACKER.md:328` |
| `008` global-contacts shape not ported; `user_contacts` folds into the `phone_last10` cache | Rust starter cleanup, superseded by Android cache | `PROJECT_REPORT.md:212-216` |
| All CHECK constraints absent | Room can't express; moved to `LedgerService` sum-zero + kind→shape + equity guard | `LedgerService.kt:243-245, 443, 490, 494`; `migrations/README.md` "App responsibilities" |
| Equity-guard triggers absent | Room has no trigger support; replaced by `ApiException.EquityGuardViolation` | `STEP_TRACKER.md:23-25` |
| `uq_system_pot` partial unique index absent | Room doesn't support partial indexes; find-then-insert get-or-create. **Not race-safe** | `LedgerService.kt:764-772` |
| `linked_transaction_id` kept (Rust Q12 deleted it) | Android split/QR bookkeeping; decide at real split implementation | `PROJECT_REPORT.md:173-177` |
| `bucket` subtype + `target_paise` on accounts | Deliberate Android extension (buckets = child accounts) | `STEP_TRACKER.md:17-22` |
| `default_tags` / `default_intent` / `default_account_id` on counterparties | Report said "defer"; already present on Android, kept | `PROJECT_REPORT.md:178-182` |
| `parsed_facts`, `stated_balance_paise` on provenance | Android SMS fields intentionally added | `PROJECT_REPORT.md:165-172` |
| `source` on `counterparty_aliases` | Android records alias origin | `PROJECT_REPORT.md:183-184` |
| Dates as `Long` epoch-millis vs Rust ISO `TEXT` | Adapter concern only | `PROJECT_REPORT.md:235-243` |
| `app_metadata` table | Android-only, no Rust counterpart | — |
| `v_account_balances` sign simplification | See G10 — the simplification is fine; only the missing probe (G8) is arguably work | this file |

---

## 3. How to re-verify the diff

```bash
# 1. Regenerate the Room DDL from the current entities
./gradlew :app:kspDebugKotlin
sed -n '110,190p' \
  app/build/generated/ksp/debug/kotlin/com/example/spendwise/data/database/AppDatabase_Impl.kt

# 2. Compare against the reference
ls ~/code/active/backend/migrations/00*.sql
cat ~/code/active/backend/migrations/README.md
cat ~/code/active/backend/migrations/DESIGN_DECISIONS.md
```

When an item above is fixed, mark its entry **RESOLVED** (with the Room
version that carried it) rather than deleting it, so the history of the diff
stays readable.
