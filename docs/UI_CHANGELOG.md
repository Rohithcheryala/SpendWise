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
3. **Typography/font**: intentionally untouched pending your brand call.
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
