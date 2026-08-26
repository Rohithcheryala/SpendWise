# SpendWise Backend — native Kotlin + Room

Port of the old FastAPI/Postgres server (`naa-accounting/backend`) into the app,
offline-first, with a REST-shaped seam for a future sync server.

## Architecture

```
backend/
├── api/
│   ├── Models.kt        DTOs + enums (EntryView, LineSpec, IngestRequest…)
│   └── LedgerApi.kt     The contract — methods map 1:1 to REST endpoints
├── service/
│   ├── LedgerService.kt        Port of services/ledger.py (all business rules)
│   ├── CounterpartyService.kt  Port of services/counterparties.py
│   └── Text.kt                 normalize / VPA regex / sms-hash / tag codec
└── di/BackendModule.kt  Hilt binding: LedgerApi -> Room-backed impl
```

**The sync seam:** callers depend on `LedgerApi`, never on Room. When a server
arrives, it implements the same interface over HTTP and this Hilt binding swaps
(or wraps behind a network policy). No caller changes.

## Business rules replicated from the old server

| Rule | Where |
|---|---|
| Money is integer paise everywhere, no floats | `*_paise` columns + `Long` in all DTOs |
| Entry lines sum to exactly zero; ≥ 2 lines | `LedgerService.validateLines` |
| Each line targets account XOR category; bucket needs an account | `validateLines` |
| Balances **computed at read**, never stored | `sumConfirmedForAccount` query |
| Liability balances inverted (= amount owed) | `accountBalance` |
| Buffer vs confirmed: only confirmed counts anywhere | status filters in every sum/list |
| Void = soft delete, leaves balances untouched | `voidEntry` |
| Ingested entry: account leg ± intent-driven contra line | `ingest` + `contraLine` |
| Loans → sys-loans receivable pot; recon → sys-reconeq; investment → sys-invest | `contraLine`, `SystemRole` |
| Orphan SMS parks on sys-unmatched pot, accountId reads null | `ingest`, `buildView` |
| Confirm refused while money is only on the unmatched pot | `confirmEntry` |
| Dedupe hash on provenance (unique) = idempotent ingestion | `findEntryByDedupeHash` + unique index |
| Counterparty resolve-or-create by normalized alias; aliases teach the system | `CounterpartyService` |
| VPA extraction (`name@oksbi`), hyphen-safe local part | `Text.extractVpa` |
| On-behalf split: one entry, per-line counterparty receivables, stays balanced; forces party_type=person | `splitEntry` |
| Opening balance = idempotent entry against open equity, dated at reconcile anchor | `recordOpeningBalance` |
| Bucket baseline = idempotent net-zero self-transfer tagged to the bucket | `recordBucketAllocation` |
| System nodes get-or-create via fixed slugs (`sys-loans-{user}` …), never duplicated | `systemAccount/systemCategory` |
| SMS account matching: bank contains + kind bridge + active last-4 identifier on the SMS's channel; unknown parser kinds stay orphans | `IngestionService.findAccount` / `KIND_BRIDGE` |
| Per-kind identifiers (`account` vs `card`) so a card number never hijacks an account-number match | `account_identifiers` table (v13) |
| Orphan reclaim: re-run matching against parse facts frozen at ingest; attach-only | `claimOrphansForAccount` |
| QR↔SMS merge: one recent scan within 30 min ±1% = same payment; intent fills blanks; auto-confirm; QR entry deleted | `tryMergeQrScanBuffer` |
| Reconciliation: watermark window, locked lines, ref→exact→fuzzy matching, gap & continuity flags, per-kind sign conventions | `ReconciliationEngine.kt` (pure) |

## Schema change
- v11→v12: `categories.kind` ("income" | "expense", default "expense") — needed by
  describe-entry classification and contra-line rules. `MIGRATION_11_12`.
- v12→v13: `account_identifiers` table (per-kind last-4 ownership, seeded from
  existing `accounts.last4`) + `entry_provenance.parsed_facts` (frozen
  "bank|last4|kind" for orphan reclaim without re-parsing). `MIGRATION_12_13`.

## Test suite (Robolectric + in-memory Room, JVM only)
`app/src/test/java/com/example/spendwise/backend/` — 63 tests, all passing:
- `LedgerValidationTest` — the four ledger invariants + balanced multi-line writes
- `BalanceTest` — computed balances: confirmed-only, buffer gating, void exclusion, liability inversion, `through` bound, opening-balance contribution & idempotent replace
- `DescribeEntryTest` — kind classification precedence (expense/income/transfer/allocation/loan/orphan/reconciliation)
- `IngestTest` — contra categories by intent+direction, buffer default, dedupe idempotency, provenance, ATM transfer legs
- `CounterpartyServiceTest` — alias resolution/normalization, priority lookup, party-type flips, VPA/sms-hash rules
- `SplitLifecycleTest` — balanced splits with per-line counterparties, over-share rejection, confirm-requires-account, void/delete semantics
- `BucketTest` — allocation self-transfer, bucket value math, zero-allocation no-op, credit-card opening as money owed
- `IngestionServiceTest` — account matching incl. kind bridge and channel constraints, orphan parking/claim (attach-only), dedupe, QR↔SMS merge semantics
- `ReconciliationEngineTest` — ref/exact/fuzzy matching, locked window, gap & continuity flags, liability sign convention, missing-net-delta

Run: `./gradlew :app:testDebugUnitTest`

## Deliberately deferred (next sessions)
1. **Notification ingestion path** (UPI-app notifications): dedupe + the 3-minute notification↔SMS merge — the merge helpers exist in `IngestionService` constants; the package allowlist/parser bridge is native-side work.
2. **Groups** — entity commented out in schema today; splits already work groupless (ad-hoc on-behalf).
3. **Statement PDF parsing** — replaced by on-device parsers (`core/parser_pw`); the reconciliation engine consumes whatever statement model you feed it.
4. **Reports/cashflow/agenda services** — read-model queries over the ledger.
