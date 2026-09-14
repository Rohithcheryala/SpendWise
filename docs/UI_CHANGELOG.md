# SpendWise UI Changelog

> Companion doc to `TASK & STATUS.md`. Every frontend change made in this session,
> **what** changed and **why**. Review and comment here before we build on it.
> Nothing here is irreversible — each entry lists the file so we can revert selectively.

---

## 1. Design system foundation

### 1.1 Brand palette — "Emerald Ink" (`ui/theme/Color.kt`)
**What:** Replaced the washed-out teal palette with a deeper emerald primary (`#0B6E5F` light / `#80D5C4` dark), warm paper-white background (`#F8FAF7`) in light mode and green-black "ink" (`#0D1311`) in dark mode. Added `surfaceContainerLowest` tokens.

**Why:** You said stock M3 looks dead. Deep emerald reads as "money" without being neon-fintech; warm neutrals give the screen a paper-like calm instead of clinical grey-white. Dark theme was near-black grey before — now it's tinted so the brand survives dark mode.

### 1.2 Semantic color system (`ui/theme/Theme.kt`)
**What:** Added `SemanticColors` (income / expense / transfer / warning + containers) provided via `CompositionLocal`, accessed as `SpendwiseTheme.colors.income` etc. Migrated every hardcoded `Color(0xFF16A34A)` / `0xFFDCFCE7` / `0xFFF59E0B` found across:
- `TransactionListItem.kt`, `AmountSection.kt`, `TransactionSummaryCard.kt`, `BudgetSummary.kt`, `BudgetCategoryCard.kt`, `BudgetSubCategoryItem.kt`, `FriendCard.kt`, `InboxScreen.kt`

**Why:** Income green was hardcoded in 8 files and broke in dark theme (dark-green text on dark container). Now income/expense/transfer have proper light+dark variants defined in exactly one place. When you make brand color calls later, you edit one file.

### 1.3 Dynamic color OFF by default (`ui/theme/Theme.kt`)
**What:** `SpendwiseTheme(dynamicColor = false)` now default; removed Material You wallpaper-theming override.

**Why:** Dynamic color silently replaced our palette on Android 12+, which is a big reason the app looked generic/dead. Since you haven't made brand decisions yet, my call is: brand identity > wallpaper theming for now. Flip one boolean if you disagree.

### 1.4 Typography
**What:** Untouched (`Type.kt`). Deliberately deferred — font choice is a brand decision that belongs to you.

---

## 2. Density fixes (the "too much height per row" problem)

### 2.1 Transaction list rows (`ui/components/TransactionListItem.kt`) — rewritten
**What:**
- Per-row `Card` → flat row on screen background. Row height went ~86dp → ~56dp (~35% denser).
- Direction icon 40dp circle → 36dp rounded-square (squircle reads more modern).
- Title demoted from `titleMedium/SemiBold` to `bodyLarge/Medium`; amount from `titleMedium/Bold` to `titleSmall/SemiBold`.
- Account + time merged into ONE subtitle line ("Account • Time").
- **Tags/pills removed from list rows entirely.** New optional `showDivider` param for row separators inside a group.
- Colors via semantic tokens (see 1.2).

**Why:** Your exact complaint. Tags are secondary metadata — they still exist on `TransactionUi` and belong in the detail view, not forcing every row taller in the scan list. Cards-in-a-list double padding and gap cost; flat grouped rows are the standard pattern (see any banking app).

### 2.2 Transactions list screen (`ui/screens/transactions/TransactionsScreen.kt`)
**What:**
- Rows grouped under **day headers** ("Today", "Yesterday", "01 Aug") via new `TransactionUi.dayLabel`. Dividers only within a group.
- Search box rebuilt as `CompactSearchField`: Surface + `BasicTextField`, ~46dp vs OutlinedTextField's fixed 56dp minimum.
- Removed 10dp inter-row spacing (rows are self-contained now).

**Why:** Day headers replace the "time inside every row" repetition and let the eye skip to a date instantly — more density AND more information. Real data integration only needs to fill `dayLabel` (e.g. `"Today"` / `"12 Aug"`).

### 2.3 Inbox cards (`ui/screens/inbox/InboxScreen.kt`)
**What:** Raw bank SMS collapsed to a single ellipsized line behind a tap-to-expand surface ("Show more"/"Show less"). Spacing tightened (16→12, 12→8). Semantic colors applied.

**Why:** Full SMS text made every pending-review card enormous; you review sender/amount/date at a glance and expand only when a parse looks suspicious.

---

## 3. Navigation personality (`navigation/MainScaffold.kt`)
**What:** Bottom bar redesigned:
- Selected item gets a **pill container** behind its icon (primaryContainer), label always visible below.
- Labels no longer appear/disappear on selection (old `AnimatedVisibility(selected)` caused the whole bar's items to shift vertically on every tap).
- Scan action kept as elevated primary circle but slightly smaller (56→54dp) with proper Surface elevation instead of raw background hack.

**Why:** The old bar was stock-M3 bland AND had layout jank. Pill indicator + persistent labels = modern without leaving M3 tokens.

---

## 4. Known limitations / follow-ups (for our next session)
1. **VM wiring**: `TransactionsScreen` still renders `defaultTransactions`; when you wire the real repository, populate `dayLabel` and drop tags into detail view only.
2. **Filter sheet** dropdowns are non-functional stubs (`onClick = {}`) — needs real pickers.
3. **Typography/font**: ~~intentionally untouched pending your brand call~~ → **done in §5.1** (Outfit, `res/font/`).
4. `MainActivity.kt` still has the `|| true` onboarding bypass (dev velocity, pre-existing).
5. Onboarding/Settings/Accounts screens reviewed but left alone this pass — they were already reasonable and the highest-impact fixes were density + identity.

## Files touched
| File | Change |
|---|---|
| `ui/theme/Color.kt` | Rewritten palette |
| `ui/theme/Theme.kt` | SemanticColors + dynamicColor off |
| `ui/components/TransactionListItem.kt` | Rewritten compact row |
| `ui/screens/transactions/TransactionsScreen.kt` | Day groups, compact search |
| `navigation/MainScaffold.kt` | Bottom nav redesign |
| `ui/components/{AmountSection,BudgetSummary,BudgetCategoryCard,BudgetSubCategoryItem,FriendCard,TransactionSummaryCard}.kt` | Semantic color migration |
| `ui/screens/inbox/InboxScreen.kt` | Collapsible SMS, colors, spacing |

Build status after changes: ✅ `compileDebugKotlin` clean.

---

## 5. UI pass v2 — identity, money, motion, charts (this session)

The earlier pass (1–3) fixed *colour* and *density*. This pass fixes
*typography, money rendering, motion and charts* — exactly what §1 of
`PROJECT_REPORT.md` called out as "the app looks dead".

### 5.1 Typography — Outfit (`ui/theme/Type.kt`, `res/font/`)
**What:** Added 5 static Outfit cuts (400/500/600/700/800) to `res/font/` and rebuilt `Typography` on the Outfit family with tight, oversized headlines (negative tracking, reduced line height). *This supersedes item 3 under "Known limitations" below — the brand call has now been made.*

**Why:** Stock Material type was the single biggest reason screens read as generic. Outfit was chosen over Manrope/Space Grotesk because it ships **true tabular figures** (verified `tnum` present in its GSUB table) — money rendering needs that.

### 5.2 `MoneyText` (`ui/components/MoneyText.kt`) — one way to render money
**What:** A single composable replacing every hand-rolled `"₹" + "%,.0f".format(x)`:
- `MoneySemantic` (INCOME/EXPENSE/TRANSFER/NEUTRAL/WARNING) resolved from `SpendwiseTheme.colors`, so light **and** dark are correct.
- `MoneySize` (HERO/BALANCE/TITLE/HEADING/BODY/LABEL) mapped onto the new type scale.
- `fontFeatureSettings = "tnum"` on every call — columns stop jiggling as values scroll.
- Count-up animation (`animateFloatAsState`) when a value changes.
- Paise (`Long`) and pre-formatted (`String`) overloads, plus shared `formatPaise()` / `formatRupees()`.

**Bug found while testing:** `DecimalFormat("##,##,##0")` silently produces **no grouping at all** on the Android runtime (renders `153250`), i.e. the obvious-looking "Indian grouping" pattern does not work. Replaced with a hand-rolled `groupIndian()` (last three digits, then pairs) that is deterministic and locale-free, pinned by `MoneyFormatTest`.

**Migration:** every raw money format string is gone from `main/` — Accounts, Transactions, Inbox, Friends, Budget, Categories, Counterparties and the shared Budget/Friend/Transaction components. Composite strings ("₹x of ₹y") now use the shared `formatRupees()`. The last hardcoded green (assets, `Color(0xFF15803D)`) now uses `MoneySemantic.INCOME`.

### 5.3 Empty states (`ui/components/EmptyState.kt`)
**What:** Shared medallion + title + one-liner + optional action (springs in on first composition). Wired into **Transactions** (with a "Clear filters" action when a search/filter is what emptied it), **Inbox**, **Friends** (with "Add friend") and **Budget** (with "Set up budget").

**Why:** An empty list with no guidance reads as a broken screen.

### 5.4 Motion
**What:** `Modifier.animateItem()` on the Transactions / Inbox / Friends / Budget lists; `animateContentSize()` on `BudgetCategoryCard`; the Inbox approve tick now pops (`spring`) before the import fires, then the row animates away.

**Why:** Insert/remove and expand/collapse previously snapped. Approve is the app's most-used gesture, so it now has a visible payoff.
(NavHost slide+fade transitions were already in place from an earlier pass — verified, left untouched.)

### 5.5 Charts (`ui/components/SpendBarChart.kt`, `ui/components/CategoryDonut.kt`) — no new dependency
**What:** Hand-rolled Canvas charts rather than pulling in Vico. A 30-day spend bar chart in a new `SpendSummaryHeader` at the top of the transactions list (fed by a new numeric `TransactionUi.amountPaise`, so nothing has to parse display strings), and an animated category donut + legend in a new `CategoryBreakdownCard` on Budget.

**Why:** The report's §1.4 ask, done inside `ui.graphics` which is already on the classpath.

### 5.6 Structure — renames + `ScannerScreen` split
**What:** `ui/screens/transaction/` → `ui/screens/transactiondetail/` with `TransactionScreen` → `TransactionDetailScreen` (matches the screen's own "Transaction Details" title). `ScannerScreen.kt` went **1,164 → 723 lines**, split into `UpiQr.kt` (pure QR decode/encode — now unit-tested), `ScannerOverlays.kt` (brackets/reticle/focus ring/status pill) and `PaymentOverlay.kt` (payment form + picker + confirmation sheets), leaving the screen with the screen state + CameraX/ML Kit pipeline.

**Not renamed:** `ui/screens/update/` — it is the **in-app APK updater**, not a transaction editor (the report's "update → TransactionEdit" guess was wrong).

### 5.7 Tests
`MoneyFormatTest` (formatter: Indian grouping, paise handling, negatives, rounding) and `UpiQrTest` (decode, rejections, malformed amount, `buildUpiUri` → `parseUpiQr` round-trip). Full unit suite + `assembleDebug` green.

### 5.8 Follow-ups left open
- Transactions filter-sheet dropdowns are still non-functional stubs (`onClick = {}`).
- `ui/components/AmountSection.kt` still takes a pre-formatted amount string rather than paise (used by `TransactionDetailScreen`).
- `ui/components/BudgetProgress.kt` now has **zero references** — dead code, deletion candidate (the donut replaces it).
