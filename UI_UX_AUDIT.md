# SpendWise — UI/UX audit + re-theme plan

Audit of the Jetpack Compose UI as it stands after the Step 5 UI pass
(`STEP_TRACKER.md`), plus a decisive replacement colour scheme and an ordered
fix list. Every claim below is backed by a grep count or a computed WCAG
contrast ratio, and the numbers are reproducible with
`python3 scripts/contrast_check.py` (see Appendix B).

Scope: `app/src/main/java/com/example/spendwise/ui/`,
`.../navigation/`, `app/src/main/res/values/`, `MainActivity.kt`.

**No app code was changed.** The only file added is the validation script
`scripts/contrast_check.py`, so the contrast figures in this report can be
re-checked after the fix is applied.

---

## 0. TL;DR

| | |
|---|---|
| **Your complaint** | "The theme isn't resonating, and text blends into the background." |
| **Confirmed?** | Yes, both — and they are measurable, not a matter of taste. |
| **Text blending** | Not your body text (that measures 16.3:1). It's that **background and surface are the same colour**, so nothing has an edge. The container ramp that's left measures only **1.04–1.48:1** end-to-end, and the two steps that carry every card — `surfaceContainer` / `surfaceContainerHigh` — are just **1.07:1** and **1.12:1** in light, **1.12:1** and **1.27:1** in dark. Dividers measure **1.17:1**. Eight text sites are alpha-faded, one to **2.48:1** (a hard AA fail). The amber `warning` token measures **3.04:1** — also an AA fail. |
| **Why the theme doesn't resonate** | The brand colour is **green** and the income semantic is **green** — 28° apart in hue, so neither reads as distinct. Every neutral is also green-tinted (light neutrals span 62° of hue, dark neutrals 30°, all inside the green-cyan band), so the whole UI sits in one narrow, low-chroma family: that's the "muddy, clinical" feel. |
| **Recommendation** | Move the brand to **indigo** ("Indigo Ledger"), keep green strictly for income and red strictly for expense. Full validated token set in §4. |
| **Overall UI-UX score** | **5.1 / 10** — good foundations (type, motion, money rendering) sitting on a broken surface/elevation system. |
| **Effort to fix** | ~1 focused day for Steps 1–6 (the colour/surface work); Step 7–13 are smaller, independently shippable cleanups. |

---

## 1. Scorecard — every axis

| # | Axis | Grade | One-line verdict |
|---|---|---|---|
| 1 | Colour scheme / brand identity | **5/10** | Real palette, but brand hue collides with the income semantic and the neutrals are all green-tinted |
| 2 | Surface & elevation hierarchy | **3/10** | **Worst axis.** `background == surface`; container steps are 1.07–1.27:1; elevation used ad-hoc in 5 places |
| 3 | Text legibility (contrast) | **6/10** | Primary text is excellent; secondary text is fine statically but 8 sites alpha-fade it, and the `warning` token fails AA outright |
| 4 | Typography | **8/10** | **Best axis.** Outfit with verified `tnum`, tight tracking, full scale, one money renderer |
| 5 | Spacing system | **4/10** | Gutters are consistently 16dp (good) but 11 different dp values in use, incl. 3/5/10/14/18/22dp — no scale |
| 6 | Shape system | **4/10** | 13 distinct corner radii (2→28dp + a 50% pill). No `Shapes.kt` |
| 7 | Iconography | **5/10** | Seven icons appear in more than one family (same concept, different stroke weight); the Inbox screen has three different names; one nav label is `"MORE"` |
| 8 | Motion | **7/10** | Real work done: `animateItem`, springs, count-up money, nav transitions |
| 9 | Density & layout | **7/10** | 56dp rows, flat lists, `contentPadding` consistent. Good |
| 10 | Component consistency | **5/10** | Same semantic (amber) has two values; cards use 3 different corner radii for the same role |
| 11 | Empty / loading / error states | **7/10** | One shared `EmptyState` with a spring-in, wired into 4 screens |
| 12 | Accessibility | **4/10** | 2 AA text failures, no dynamic-type handling, income/expense are **isometrically identical in greyscale** (1.04:1) |
| 13 | System theme plumbing | **3/10** | `Theme.Spendwise` is `android:Theme.Material.Light.NoActionBar`, no `values-night/`, no `windowBackground`; status-bar icon contrast does not follow the in-app theme override |
| 14 | Copy & content | **5/10** | A production top bar reads `"Buffer Inbox !!!"`; all UI copy is hardcoded (no i18n) |
| | **Weighted overall** | **5.1/10** | |

---

## 2. "The text blends into the background" — measured root cause

This section is the important one, because the *symptom* you described does not
come from where you'd expect. Body text is not the problem:

| Where | Token | Measured | Verdict |
|---|---|---|---|
| Light — main text | `onSurface #181D1B` on `#F8FAF7` | **16.26:1** | Excellent |
| Dark — main text | `onSurface #DEE4E0` on `#0D1311` | **14.56:1** | Excellent |
| Light — secondary text | `onSurfaceVariant #3F4946` on background | **8.88:1** | Excellent |
| Dark — secondary text | `onSurfaceVariant #BFC9C5` on card | **9.86:1** | Excellent |

So nothing is actually *too light*. What's wrong is that **structure has no
contrast**, so text has nothing to sit on. Six separate causes:

### 2.1 `background` and `surface` are literally the same colour

`Color.kt` sets `md_theme_light_background == md_theme_light_surface == #F8FAF7`
and `md_theme_dark_background == md_theme_dark_surface == #0D1311`. Consequences:

- `MaterialTheme.colorScheme.surface` is what `TopAppBar` uses by default, and
  what `MainNavigationBar` sets explicitly (`MainScaffold.kt:236`). So the top
  bar, the bottom bar and the page body are all one continuous field — the
  screen has no chrome, and cards have nothing to float *on*.
- The only way to express layering left is the container ramp, and that ramp is
  nearly flat:

| Step | Light | vs background | Dark | vs background |
|---|---|---|---|---|
| `surfaceContainerLowest` | `#FDFEFD` | 1.04:1 | `#070D0C` | 1.04:1 |
| `surfaceContainer` | `#F0F3F0` | **1.07:1** | `#191F1D` | **1.12:1** |
| `surfaceContainerHigh` | `#EAEEEB` | **1.12:1** | `#232928` | **1.27:1** |
| `surfaceContainerHighest` | `#E4E8E5` | **1.18:1** | `#2E3433` | 1.48:1 |

At arm's length on a phone in daylight, a step below roughly **1.2:1** is not
reliably perceived. So `surfaceContainer` (35 call sites) and
`surfaceContainerHigh` (12 call sites) — i.e. **every card in the app** —
dissolve into the page. That is the "everything blends" feeling, and it is not
a text-contrast bug at all: it's a *surface-separation* bug.

### 2.2 Alpha-faded text tokens (8 sites)

Where text is faded with `.copy(alpha = …)` it is composited against whatever is
behind it, so the effective ratio collapses:

| Site | Composites to | Measured | Verdict |
|---|---|---|---|
| `AmountSection.kt:163` — amount placeholder on `surfaceContainerHigh` | `#9CA29E` | **2.48:1** | **FAIL** (needs 4.5) |
| `BudgetSummary.kt:84,101,123` — `onPrimaryContainer@0.65` (dark) | `#65B9A9` | **4.05:1** | **FAIL** for <18sp |
| `BudgetSummary.kt:84,101,123` — same, light | `#376960` | 4.83:1 | Passes by luck |
| `AccountsScreen.kt:239,258,271` — `onPrimaryContainer@0.8/0.7` | — | 4.9–6.6:1 | Passes by luck |

The light-theme passes are *accidental*: they depend on the mint
`primaryContainer` happening to be dark enough. Change the palette and they
break. That is exactly why §4 introduces named `TextColors` tokens instead.

### 2.3 Dividers are invisible (1.17:1)

`TransactionListItem.kt:101` draws its row divider as
`outlineVariant.copy(alpha = 0.4f)`:

- Light on card: `#DCE2DF` → **1.17:1**
- Dark on card: `#28302D` → **1.24:1**

`outlineVariant` already measures only 1.52:1 against a light card. Multiplying
it by 0.4 removes the last of it. There are exactly 2 uses of `outlineVariant`
in the whole UI, so the app has effectively **no separators** — which is a
second, independent contributor to "it all merges".

### 2.4 One token fails AA outright: `warning`

`SpendwiseTheme.colors.warning` = `#D97706`, **3.04:1** on the light background.
It is used as *text* (e.g. `BudgetCategoryCard.kt:124` renders the
"`82%`" label in it) — AA for normal-size text is 4.5:1. In the dark theme
`#FBBF24` is fine (11.25:1), so this is a light-theme-only failure.

### 2.5 Income and expense are the same colour in greyscale

`income #15803D` vs `expense #DC2626` measure **1.04:1 against each other** —
i.e. in greyscale (or for the ~8% of men with red-green CVD) they are the same
tone. Direction is currently carried by a `+`/`−` sign and an arrow icon, which
is the right mitigation and it is already implemented — but the palette should
not be *relying* on it. §4's palette raises this to **1.30:1** (dark) / 1.14:1
(light) and keeps sign+arrow mandatory.

### 2.6 Summary of the fix

You cannot fix this by choosing a prettier primary colour. The fix is three
things, in this order:

1. **Separate `background` from `surface`** so cards and bars have something to
   sit on (light `#EBEEF4` background + `#FFFFFF` cards = 1.16:1, plus a 1dp
   `outlineVariant` hairline at 1.53:1 for a crisp edge).
2. **Add real steps to the container ramp** (dark card 1.13:1 over background,
   container-high 1.22:1 over card).
3. **Delete every alpha-faded text token**; replace with named text tokens that
   are measured, not derived by multiplication.

---

## 3. Why the "Emerald Ink" theme isn't resonating

The palette isn't *bad* — it's coherent and someone thought about it. The
problem is that it makes three specific structural mistakes, and they explain
the "not resonating" feeling better than "I don't like green" does.

### 3.1 The brand hue and the income hue are the same hue

| Token | Hex | Hue (HSL) |
|---|---|---|
| `md_theme_light_primary` | `#0B6E5F` | **170.9°** |
| `md_theme_light_primaryContainer` | `#9CF2DF` | **166.7°** |
| `SpendwiseTeal` (accent) | `#0D9488` | **174.7°** |
| `md_theme_light_secondary` | `#4A635F` | **170.4°** |
| `colors.income` | `#15803D` | **142.4°** |

Your brand sits at 167–175°, your income semantic at 142° — **28° apart, both
green**. So: the primary button, the selected bottom-nav pill
(`primaryContainer`, `MainScaffold.kt:299`), the whole budget summary card
(`BudgetSummary.kt:54` fills a card with the mint), the scan FAB, and every
income amount are all *the same family*. The eye has no unique signal for
"this is SpendWise" and no unique signal for "money came in". When your brand
colour is also your success colour, neither one can do its job.

This is the single reason a green *finance* app reads as flat: green is doing
two jobs at once.

### 3.2 Every neutral is green-tinted, and they sit in a narrow band

Measured hue of the neutral ramp:

| Ramp | Hue range | Span |
|---|---|---|
| Light neutrals (9 tokens) | 100° → 162° | **62°** |
| Dark neutrals (9 tokens) | 140° → 170° | **30°** |

Every grey in the dark theme is at 140–170° — a green-cyan cast, sat 6–30%.
That is what produces the "muddy / clinical / swampy" reading. A neutral ramp
should be *near*-neutral (sat roughly 2–8%, one consistent hue) so that the
brand and semantic colours are the only things carrying colour. Here the greys
each carry their own green tint *at different saturations* (18.8% on
`#0D1311`, 30% on `#070D0C`), so they look accidental rather than designed.

Worth stating plainly: a tinted neutral is a legitimate technique (it's how
Spotify gets purple-greys). It works when the tint is *one* hue held at *one*
low saturation. Yours varies both, in the same family as the brand — so it
reads as haze, not intent.

### 3.3 There is no mid-tone in the primary ramp

`primary` is at **23.7% lightness** (very dark) and `primaryContainer` at
**78% lightness** (very pale mint). There is nothing in between. So the app
alternates between "near-black green" and "pale mint", with no saturated
mid-tone available for charts, gradients, selected states or illustration.
That's why the UI can't build much visual energy even though it has a
`tertiary` and a full container set.

### 3.4 The tertiary is nearly the secondary

`tertiary #456179` (hue 207.7°, sat 27.4%) vs `secondary #4A635F` (hue 170.4°,
sat 14.5%). Both are desaturated mid-dark slates. `tertiaryContainer` is used
**zero times** in the app and `secondaryContainer` **once**, so the two
"support" colours exist mostly on paper. Meanwhile `SpendBarChart` uses
`tertiary` as its "today" accent and `donutPalette()` slots `tertiary` as
slice #2 — so a near-duplicate of secondary shows up in charts.

### 3.5 The competitive read

Green-for-money is the most-used finance colour there is: bank brands, Cash
App, Robinhood, Groww, Zerodha, and — in an Indian UPI context — it collides
with Google Pay / WhatsApp-style success green. It is the *safe* choice, which
is the opposite of the "distinct financial identity" the header comment in
`Color.kt:8` claims. If the app's brand is meant to be memorable, green-as-brand
is the one lane where it won't be.

**Conclusion:** move the brand off green, and keep green exclusively for
income. That single decision fixes both the "doesn't resonate" problem and the
brand/semantic collision, and it costs nothing structurally — the theme layer
already has the right shape (§4.5).

---


## 4. Recommended scheme — "Indigo Ledger"

**Decision: move the brand to indigo/violet (`#4338CA`), keep green exclusively
for income, red for expense, amber for attention.**

### 4.1 Why indigo for this app specifically

1. **It vacates both semantic lanes.** Indigo sits 200°+ away from income-green
   (142°) and expense-red (0°), so the brand owns "action / navigation /
   identity" and the semantics own "money direction". Nothing collides.
2. **It's the heritage colour of trust-finance** — chequebooks, passbooks,
   banks, Stripe/Wise/N26-adjacent products. For a *personal finance* app it
   reads institutional-but-modern rather than "growth-hack green".
3. **It holds up at high chroma in dark UI**, which green does not: a light
   green on near-black goes neon/caustic (`#4ADE80` on `#0D1311` is 10.5:1 —
   it *glows*), whereas `#B4B9FF` on `#05080C` reads as calm illumination.
4. **It gives a clean four-lane system:** indigo = your product + actions;
   green = in; red = out; amber = attention. Every pixel then has an
   unambiguous meaning.
5. **The neutrals go properly neutral, cool.** With the brand no longer green,
   the greys stop being tinted green, which removes the "muddy" reading — and
   because the whole surface system is rebuilt (§4.2) the cards finally separate.

### 4.2 Light theme — complete token set (all validated)

| Role | Hex | Notes |
|---|---|---|
| `primary` | `#4338CA` | 6.80:1 on background, 7.90:1 on card |
| `onPrimary` | `#FFFFFF` | 7.90:1 on primary |
| `primaryContainer` | `#E0E7FF` | the "brand chip / selected pill" fill |
| `onPrimaryContainer` | `#1E1B4B` | 12.98:1 on container |
| `secondary` | `#4F5B76` | steel-blue support |
| `onSecondary` | `#FFFFFF` | |
| `secondaryContainer` | `#DCE3F0` | |
| `onSecondaryContainer` | `#131B2E` | 13.31:1 |
| `tertiary` | `#0F766E` | deep teal — the *accent*, deliberately far from income-green |
| `onTertiary` | `#FFFFFF` | |
| `tertiaryContainer` | `#CBFBF1` | (finally gets used — see §5.10) |
| `onTertiaryContainer` | `#042F2E` | |
| `error` | `#B42318` | 6.57:1 on card |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#FEE4E2` | |
| `onErrorContainer` | `#55160C` | |
| `background` | `#EBEEF4` | **cool grey — the key change** |
| `onBackground` | `#101828` | 15.27:1 |
| `surface` | `#FFFFFF` | **cards are white on grey** (1.16:1 vs background) |
| `onSurface` | `#101828` | 17.75:1 |
| `surfaceContainerLowest` | `#F7F8FB` | |
| `surfaceContainerLow` | `#F4F6FA` | |
| `surfaceContainer` | `#F1F3F8` | nested panels (1.11:1 vs card) |
| `surfaceContainerHigh` | `#EAEDF4` | 1.17:1 vs card |
| `surfaceContainerHighest` | `#E1E5EE` | |
| `onSurfaceVariant` | `#46505F` | 7.02:1 on background, 8.16:1 on card |
| `outline` | `#6B7484` | for real borders/focus rings |
| `outlineVariant` | `#CBD1DB` | **1.53:1 on card** — a *visible* hairline (was 1.52 multiplied by 0.4) |
| `inverseSurface` | `#2A3040` | |
| `inverseOnSurface` | `#EDEFF5` | 11.45:1 |
| `inversePrimary` | `#B4B9FF` | |
| `scrim` | `#000000` | |

### 4.3 Dark theme — complete token set (all validated)

| Role | Hex | Notes |
|---|---|---|
| `primary` | `#B4B9FF` | 10.82:1 on background |
| `onPrimary` | `#1E1B4B` | 8.62:1 |
| `primaryContainer` | `#3730A3` | |
| `onPrimaryContainer` | `#E0E7FF` | 8.06:1 |
| `secondary` | `#B9C3DA` | |
| `onSecondary` | `#222C42` | |
| `secondaryContainer` | `#37415A` | |
| `onSecondaryContainer` | `#DCE3F0` | 7.88:1 |
| `tertiary` | `#5EEAD4` | 12.04:1 on card |
| `onTertiary` | `#042F2E` | |
| `tertiaryContainer` | `#115E59` | |
| `onTertiaryContainer` | `#CBFBF1` | |
| `error` | `#FDA29B` | error / destructive text |
| `onError` | `#55160C` | |
| `errorContainer` | `#7A271A` | |
| `onErrorContainer` | `#FEE4E2` | |
| `background` | `#05080C` | **near-black, now darker than cards** |
| `onBackground` | `#E7EAF0` | 16.65:1 |
| `surface` | `#121822` | **raised card** (1.13:1 vs background) |
| `onSurface` | `#E7EAF0` | 14.77:1 |
| `surfaceContainerLowest` | `#0B0F16` | recessed wells |
| `surfaceContainerLow` | `#161D29` | |
| `surfaceContainer` | `#1A2130` | nested panels (1.11:1 vs card) |
| `surfaceContainerHigh` | `#202938` | **1.22:1 vs card** |
| `surfaceContainerHighest` | `#2A3341` | chart tracks, skeletons |
| `onSurfaceVariant` | `#AEB6C4` | 9.83:1 on background, 8.72:1 on card |
| `outline` | `#7C8797` | |
| `outlineVariant` | `#333C4C` | **1.60:1 vs card** — a real hairline |
| `inverseSurface` | `#E7EAF0` | |
| `inverseOnSurface` | `#121822` | 14.77:1 |
| `inversePrimary` | `#4338CA` | |
| `scrim` | `#000000` | |

Both ramps follow M3's direction (light: higher elevation → darker tint;
dark: higher elevation → lighter). The one deliberate twist is that light
`surface` is pure white while `background` is grey — that's what makes a card
read as *raised* rather than *recessed*, and it is the fix for §2.1.

### 4.4 Semantic colours — validated

| Semantic | Light | Container | Dark | Container |
|---|---|---|---|---|
| `income` | `#0F6B34` | `#DCFAE6` | `#4ADE80` | `#05432A` |
| `expense` | `#C81E1E` | `#FEE4E2` | `#FF8A8F` | `#4E1315` |
| `transfer` | `#1D4ED8` | `#DBEAFE` | `#93B4FF` | `#1B2A52` |
| `warning` | `#B54708` | `#FEF0C7` | `#FDB022` | `#4A2A05` |

Measured (all ≥ 4.5:1, so all are safe as *text*):

| Pair | Light | Dark |
|---|---|---|
| income on card | 6.62:1 | 10.22:1 |
| income on its container | 5.94:1 | 6.54:1 |
| expense on card | 5.74:1 | 7.87:1 |
| expense on its container | 4.76:1 | 6.50:1 |
| transfer on card | 6.70:1 | 8.66:1 |
| transfer on its container | 5.49:1 | 6.81:1 |
| warning on card | **5.43:1** (was 3.04:1) | 9.67:1 |
| warning on its container | 4.78:1 | 7.02:1 |
| **income vs expense (greyscale)** | **1.15:1** (was 1.04:1) | **1.30:1** |

### 4.5 What the theme layer needs to change structurally

The good news: your theme layer is already the right shape, so this is a
token swap plus two additions, not a re-architecture.

| Already there (keep) | Needs adding |
|---|---|
| `lightColorScheme`/`darkColorScheme` fully wired — all 30 roles | Named **`TextColors`** tokens (`textPrimary` / `textSecondary` / `textTertiary`) so nobody ever writes `.copy(alpha=…)` on text again |
| `SemanticColors` + `LocalSemanticColors` + `SpendwiseTheme.colors` accessor | A **`Shapes`** object passed to `MaterialTheme(shapes = …)` |
| `dynamicColor = false` (correctly disabled) | A **`Dimens`** object for spacing |
| `darkTheme` driven by the user's `ThemeMode` setting | An `Elevation` scale (or a documented "tonal + hairline" convention) |

### 4.6 If you don't want indigo

You have two other defensible directions. Both still keep green = income only.

- **Option B — "Midnight Teal"** (least visual change, keeps a teal brand):
  fix *only* the surface system, and re-hue the brand from green-teal (171°) to
  **cyan-teal `#0E7490`** so it's 30°+ clear of income-green. Cheapest option,
  but teal is still adjacent to green on a small screen and the brand/semantic
  distinction stays subtler than indigo.
- **Option C — "Graphite + Lime"** (statement app / CRED-like): neutral
  graphite surfaces with a high-chroma lime accent `#A3E635`. Highest
  personality, but lime is intrinsically a light colour, so it can only ever be
  a *fill*, never text or an icon on light — a real constraint, and it puts the
  accent back next to income-green.
- **Option D — Deep rose / magenta `#BE123C`**: distinct and memorable, but it
  now sits next to expense-red, which is a worse collision than the green one
  you have today.

**Recommendation stands: Option A (Indigo Ledger).** It is the only option that
resolves *both* collisions (brand↔income, brand↔expense) at once.


---

## 5. Axis-by-axis: what's good, what's wrong, what I'd do

### 5.1 Surface & elevation hierarchy — 3/10 (fix first)

**Wrong:** `background == surface`; the container ramp spans only 1.04–1.48:1
end-to-end and the two steps that matter are 1.07:1 / 1.12:1 (light) and
1.12:1 / 1.27:1 (dark); only **5** elevation values exist in the entire UI, used
ad-hoc: `tonalElevation 6.dp` (`TransactionBottomBar.kt:37`),
`shadowElevation 8.dp` (there and `MainScaffold.kt:236`), `cardElevation 2.dp`
(`TransactionSummaryCard.kt:76`), `shadowElevation 4.dp`
(`MainScaffold.kt:265`).

**Fix:**
1. Adopt the palette in §4 — this alone moves every card from **1.07:1 →
   1.16:1** (light) / **1.13:1** (dark), and container-high to 1.17:1 / 1.22:1.
2. Give **every card a 1dp `outlineVariant` hairline**. That is what makes an
   edge crisp; tonal difference alone is never enough in light mode. Add a
   `SpendwiseCard` to `ui/components/` and route the ~13 raw `Card` call sites
   through it.
3. Fix `TopAppBar` — pass
   `colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)`
   so bars read as chrome and white cards read as content. (Today the bar
   defaults to `surface`, which *is* the background → no separation.)
4. Dark mode: stop using `shadowElevation` for the bottom bar — shadows do
   nothing on near-black. Use `surfaceContainerHigh` plus a 1dp top hairline.
5. Freeze a scale and forbid others: `0 / 1 / 2 / 3 dp` **plus** "one tonal
   step", documented as: light = tonal + hairline, dark = tonal only.

### 5.2 Spacing — 4/10

**Good:** screen gutters are consistent — `contentPadding` is 16dp horizontal
across all 9 list screens, vertical 12dp in the three long lists. Real
discipline; keep it.

**Wrong:** the step *sizes* are not on a scale. Measured `N.dp` histogram in
`ui/`:

| Count | Sizes |
|---|---|
| High use | `16` (95), `8` (59), `12` (54), `24` (35), **`14` (28)**, `4` (27), `20` (24), **`10` (20)**, **`18` (18)**, `6` (17), `2` (17) |
| Occasional | `48`, `28`, `32`, `40`, **`3`**, `64`, `56`, `52`, **`5`**, `44` |
| One-offs | `96`, `36`, **`22`**, `88`, `84`, `76`, `60`, `38`, `156`, `120` |

11 values are off any 4pt grid (`2, 3, 5, 6, 10, 14, 18, 22`). The tell-tale
signs: `14.dp` 28×, `18.dp` 18×, `10.dp` 20× — these are the eyeballed values,
and they are why the UI feels *almost* aligned but never crisp.

**Fix:** a 4pt scale with a constrained vocabulary, as a `Dimens` object:

| Token | Value | Use |
|---|---|---|
| `xs` | 4.dp | icon↔label, tight pairs |
| `sm` | 8.dp | inside a chip, icon↔text |
| `md` | 12.dp | list-row inner vertical |
| `lg` | 16.dp | **screen gutter** (already correct) |
| `xl` | 20.dp | card inner padding |
| `xxl` | 24.dp | section gap, sheet padding |
| `xxxl` | 32.dp | empty-state outer |
| `huge` | 48.dp | hero spacing |

Then a mechanical pass: `14 → 12` or `16`; `18 → 16` or `20`; `10 → 8` or `12`;
`3/5/6 → 4`. Visible tightening, zero redesign.

### 5.3 Shapes — 4/10

**Wrong:** **13** distinct corner radii in use —
`2, 3, 4, 8, 10, 12, 14, 16, 18, 20, 24, 28` dp plus `RoundedCornerShape(50)`.
There is no `Shape.kt`/`Shapes.kt`. Worse, the *same role* uses different radii:

| Role | Radii in use today |
|---|---|
| A card | 16dp (`SettingsScreen.kt:174`), 18dp (`BudgetCategoryCard.kt:70`), 20dp (`BudgetSummary.kt:52`, `TransactionSummaryCard.kt:72`, `SettingsScreen.kt:122`) |
| A small colour tile | 10dp (`TransactionListItem.kt:155`), 12dp (`BudgetCategoryCard.kt:89`) |
| A field | 14dp (`AmountSection.kt:170`) vs M3's default elsewhere |

**Fix:** add `ui/theme/Shape.kt`, pass it via `MaterialTheme(shapes = Shapes)`:

| Token | Value | Use |
|---|---|---|
| `extraSmall` | 8.dp | chips, small tiles, tags |
| `small` | 12.dp | fields, segmented buttons |
| `medium` | 16.dp | **cards (default)** |
| `large` | 20.dp | hero cards, sheets, dialogs |
| `extraLarge` | 28.dp | bottom sheets (M3 default) |

Also replace `RoundedCornerShape(percent = 50)` with `CircleShape` — they are
not the same thing, and the nav pill (`MainScaffold.kt:297`) should be a circle.


### 5.4 Typography — 8/10 (keep, minor trims)

**Good:** Outfit bundled in `res/font` with real tabular figures, `tnum` applied
in `MoneyText`, tight negative tracking on headlines, one money renderer. Better
than most shipped apps.

**Wrong (minor):** the scale defines **5** display/headline levels from 22–52sp,
but usage is bottom-heavy:

| Style | Uses | |
|---|---|---|
| `displayLarge` (52sp) | **0** | dead |
| `displayMedium` (44sp) | 1 | |
| `displaySmall` (36sp) | 1 | |
| `headlineLarge` (30sp) | 2 | |
| `headlineMedium` (26sp) | 4 | |
| `headlineSmall` (22sp) | **14** | doing all the work |

Also `MoneySize.BODY` maps to `titleSmall` (14sp) — so "body-sized money" is
*larger in weight but smaller in size* than `bodyLarge` (16sp). And no
`lineHeight` is set on the money styles, so a two-line balance can collide.

**Fix:** delete `displayLarge`; retune `displaySmall` to 32sp; give
`MoneySize.BODY` an explicit `TextStyle` instead of borrowing `titleSmall`.
Add dynamic-type safety: nothing in the app reads `LocalDensity.fontScale`, so
at 200% font scale the fixed `.height(68.dp)` bottom bar and 56dp rows will
clip — use `heightIn(min = …)` on those two.

### 5.5 Iconography — 5/10

**Wrong (measured):** icons come from three families with no rule.

| Family | Examples found |
|---|---|
| `Icons.Rounded` | ArrowUpward (3), ArrowDownward (3), SwapHoriz (2), Sms (2), KeyboardArrowDown (2), Close (2), ArrowForward (2), ReceiptLong, QrCodeScanner, Person, NotificationsActive, FilterList, ExpandMore, ExpandLess, Check, CalendarMonth, Add, AccountBalanceWallet |
| `Icons.Outlined` | PersonAdd (4), Search (3), Close (3), Add (3), PersonSearch (2), SwapHoriz, Storefront, Settings, Sell, Restaurant, Refresh, PieChart, Home, Handshake, DirectionsCar, Category, AccountBalance |
| `Icons.Filled` | SystemUpdate (2), Storefront, Settings, ReceiptLong, Inbox, Group, FlashOn, FlashOff, + all 5 `Destination` icons |

So the **same concept** appears in more than one family, and the choice differs
screen-to-screen:

| Icon | Families actually used |
|---|---|
| `Settings` | `Icons.Outlined.Settings` (`BudgetScreen.kt:133`) **and** `Icons.Filled.Settings` (`SideNavDestination.kt:43`) |
| `Sms` | `Icons.Default.Sms` (`SettingsScreen.kt:181`, `ScanMessagesScreen.kt:74`) **and** `Icons.Rounded.Sms` (`PermissionsScreen.kt:66,98`) |
| `Sell` | `Icons.Default.Sell` (`InboxScreen.kt:443,482`) **and** `Icons.Outlined.Sell` (`OtherSideSelector.kt:126`) |
| `Storefront` | `Icons.Outlined.Storefront` (`CounterpartiesScreen.kt:180`) **and** `Icons.Filled.Storefront` (`SideNavDestination.kt:38`) |
| `ReceiptLong` | `Icons.Rounded.ReceiptLong` (`TransactionsScreen.kt:174`) **and** `Icons.Filled.ReceiptLong` (`SideNavDestination.kt:30`) |
| `Close` | `Icons.Outlined.Close` (3×), `Icons.Rounded.Close` (2×), `Icons.Default.Close` (1×) — **all three** |
| `Add` | `Icons.Default.Add` (5×), `Icons.Outlined.Add` (3×), `Icons.Rounded.Add` (2×) — **all three** |

So the same concept changes optical weight as you move between screens: the
inbox icon in the drawer is `Filled` while the bottom-nav one is the same
family, but `Settings` is Outlined on Budget and Filled in the drawer. Filled,
Rounded and Outlined have visibly different stroke weights at 24dp, which is a
quiet source of "this doesn't hang together".

**Fix:** one rule, applied mechanically —
- `Icons.Rounded` → **navigation, actions, money direction** (matches the
  rounded 16dp card language)
- `Icons.Filled` → **entity/category identity** (an account, a category)
- `Icons.Outlined` → **empty-state medallions only**

Plus two label fixes: `Destination.MORE` is `label = "MORE"` (all-caps, while
siblings are `"Budget"`/`"Inbox"`/`"Scan"`/`"Friends"`) → `"More"`. And the
`UPI` tab is labelled `"Scan"` with `contentDescription = "Scan"` while the
screen's own title is `"Scan & Pay (UPI)"` → align on `"Scan & Pay"`.

### 5.6 Motion — 7/10 (keep)

Already good: `animateItem()` on the 4 main lists, spring-in `EmptyState`,
count-up `MoneyText`, donut sweep, bar-chart grow, AppNavHost slide +
MainNavHost crossfade, the Inbox approve-tick pop.

**Minor:** the selected nav pill in `MainScaffold.kt:296-302` **snaps** between
`primaryContainer` and `Color.Transparent` with no animation, as does the icon
tint. With the new palette the pill is more prominent, so the snap will read
more clearly. Add `animateColorAsState(tween(200))` to both.

### 5.7 Density & layout — 7/10 (keep)

Flat ~56dp rows (deliberately documented in `TransactionListItem`), day-grouped
transactions, `animateItem`, consistent 16dp gutters, and the raised circular
Scan button in the bar is a nice deliberate touch.

**Minor:** `MainScaffold.kt` has two byte-identical `when` branches for
`Destination.UPI` and `else` — collapse them. And the bottom bar applies
`.navigationBarsPadding().height(68.dp)`, so it is 68dp of content *plus* the
inset; on gesture-nav devices that is a tall bar occupying ~110dp. Prefer
`.heightIn(min = 64.dp)` inside the inset padding.

### 5.8 Component consistency — 5/10

**Concrete inconsistencies found:**

- **Two different ambers for one meaning.** `CategoriesScreen.kt:180` uses
  hardcoded `Color(0xFFF59E0B)` for a >75%-used budget bar, while
  `BudgetCategoryCard.kt:60` uses `SpendwiseTheme.colors.warning` (`#D97706`).
  Same semantic, two values, side by side in the same feature.
- **20 hardcoded colours outside the theme layer** (`grep 'Color(0x' ui/`
  excluding `theme/`), including:
  - `AccountsScreen.kt:84-87` — `AccountType` colours
    (`#2563EB` / `#0D9488` / `#DC2626` / `#16A34A`) are **not theme-aware**, so
    they are identical in dark mode, and `#16A34A` is a *third* green;
  - `ProfileScreen.kt:58,61-66` — default avatar colour `Color(0xFF4CAF50)`,
    **the stock Material green, not in the palette at all**, plus 6 more;
  - `FriendAvatar.kt:32-39` — 8 avatar colours, also not theme-aware.
- **`donutPalette()` steals the semantics.** `CategoryDonut.kt:151-161` returns
  `[primary, tertiary, warning, transfer, income, secondary, expense]` as
  *categorical* slice colours. So a food category can be drawn in `expense` red
  and a salary category in `income` green — the two colours whose entire job is
  to mean *direction* become arbitrary decoration, and the legend teaches the
  user nothing.
- **Cards use 3 radii for one role** (§5.3) and only `TransactionSummaryCard`
  sets an elevation.

**Fix:**
- One amber: delete `Color(0xFFF59E0B)` → `SpendwiseTheme.colors.warning`.
- Add theme-aware `CategoricalColors` (8 tones, both themes) for accounts,
  avatars and the donut, built by hue-rotating around the brand **excluding**
  income/expense/transfer. Replace the three ad-hoc colour lists with it.
- `ProfileScreen`'s default must be `MaterialTheme.colorScheme.primary`, not
  `#4CAF50`.
- Route every card through `SpendwiseCard` (§5.1) so radius + border + elevation
  are decided once.

### 5.9 Accessibility — 4/10

| Issue | Evidence | Fix |
|---|---|---|
| `warning` as text fails AA (light) | **3.04:1** (`#D97706` on `#F8FAF7`) | `#B54708` → 5.43:1 |
| Placeholder fails AA | **2.48:1** (`AmountSection.kt:163`) | named `textTertiary` token ≥4.5:1 |
| Dark secondary-on-container fails | **4.05:1** (`BudgetSummary.kt:84/101/123`) | drop the `.copy(alpha=…)`, use `onPrimaryContainer` |
| Income/expense identical in greyscale | **1.04:1** | new palette → 1.15:1 / 1.30:1, **and keep sign+arrow mandatory** |
| No dynamic-type handling | zero `fontScale` references | `heightIn(min=…)`, sp-relative sizing |
| Status-bar icons ignore the in-app theme | `enableEdgeToEdge()` uses the *system* `uiMode`, not your `ThemeMode` (§5.11) | explicit `SystemBarStyle` |
| Touch targets | 56dp rows ✅, 54dp scan target ✅, 10dp legend dots are non-interactive ✅ | no change needed |

### 5.10 Token discipline — 5/10

- **Dead colour tokens:** `SpendwiseGreen`, `SpendwiseGreenContainer`,
  `SpendwiseTeal`, `SpendwiseRed`, `IncomeGreenDark` — **0 references** outside
  `Color.kt`. The file's own comment calls them migration aliases, but the
  migration (`STEP_TRACKER.md` Step 5) is finished.
- **Dead resources:** `res/values/colors.xml` still ships the
  `purple_200/500/700` + `teal_200/700` template palette — **0 uses**, none of
  them brand colours.
- **Half the palette is unused.** Usage counts across `ui/` + `navigation/`:
  `surfaceContainer` **35**, `primaryContainer` **19**, `surfaceContainerHigh`
  12, `surfaceContainerHighest` 6, `surfaceVariant` 5, `errorContainer` 4,
  `outline` 4, `outlineVariant` **2**, `secondaryContainer` **1**,
  `tertiaryContainer` **0**.

  So the app is effectively "primary + one card grey". That is a large part of
  why it reads flat: the palette exists, but the screens never spend it.

**Fix:** delete the dead tokens and resources, then deliberately spend the
unused roles — `tertiary`/`tertiaryContainer` for the accent lane (cashflow
chart today-bar, goal progress, insight cards), `secondaryContainer` for neutral
chips, `outlineVariant` for card hairlines and dividers.

### 5.11 System theme plumbing — 3/10

- `res/values/themes.xml` is one line:
  `<style name="Theme.Spendwise" parent="android:Theme.Material.Light.NoActionBar" />`
  — a **platform** theme, hard-locked to Light, with **no `values-night/`**
  (the directory does not exist) and no `android:windowBackground`. Result: a
  **white flash on cold start in dark mode**, and a window background that does
  not match the Compose background.
- `enableEdgeToEdge()` is called with **no arguments**, so both bars use
  `SystemBarStyle.auto(...)`, whose `detectDarkMode` reads
  `Configuration.uiMode` — the **system** setting, not your resolved
  `darkTheme`. So if the user forces **Light** while the phone is in dark mode,
  the system says "dark", and you get **light status-bar icons on a light
  background — an invisible status bar**. The reverse gives dark icons on dark.
  This is a real, reproducible bug in `MainActivity.kt`.
- No `androidx.core:core-splashscreen` dependency, so the launch experience is
  whatever the platform gives you.

**Fix:** add `values-night/themes.xml` (dark `windowBackground = #05080C`, light
`#EBEEF4`), and re-apply the bar appearance whenever the resolved theme changes:

```kotlin
SpendwiseTheme(darkTheme = darkTheme) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    …
}
```

### 5.12 Copy & content — 5/10

- `InboxScreen.kt:174` — `Text("Buffer Inbox !!!")`. Three exclamation marks in
  a production top bar. Worse, the **same screen has three different names**:

  | Where | Label |
  |---|---|
  | `Destination.kt:18` (bottom nav) | `"Inbox"` |
  | `SideNavDestination.kt:42` (drawer) | `"Buffer Inbox"` |
  | `InboxScreen.kt:174` (title) | `"Buffer Inbox !!!"` |

  Pick one — `"Inbox"` — and change all three. The `"N pending review"`
  subtitle already carries the state, so the exclamation marks add nothing.
- `Destination.MORE` → `"MORE"` (§5.5).
- `strings.xml` contains **only** `app_name`. Every user-facing string is
  hardcoded in Kotlin — no i18n path at all, in a country with 22 scheduled
  languages. At minimum move screen titles, empty states and Settings labels
  into resources.
- `ManageBudgetDialog` labels a field `"Budget Amount (₹)"` with a hardcoded
  rupee glyph, even though `MoneyText` exports `RUPEE_SIGN` and
  `settings.currency` already exists.

---

## 6. The fix plan — ordered, with code

Order matters: Steps 1–2 are the foundation, Steps 3–6 are the visible fix for
your complaint, Steps 7–13 are independent cleanups you can ship separately.

### Step 1 — Replace `ui/theme/Color.kt`

Keep the existing token **names** so `Theme.kt` needs no structural change;
only values change, plus the dead aliases are deleted.

```kotlin
package com.example.spendwise.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Brand palette — "Indigo Ledger"
//
// Indigo is the brand + action lane. It sits far in hue from BOTH income-green
// (142°) and expense-red (0°), so the brand can never be mistaken for a
// money-direction signal — the structural flaw of the old emerald palette,
// where brand and `income` were 28° apart.
//
// Neutrals are cool and near-neutral: structure carries the contrast, and the
// accent colours carry the meaning. See UI_UX_AUDIT.md §4.
//
// Rules pinned to these values (validated, see §5 / Appendix B):
//   • every text colour ≥ 4.5:1 on `background`, `surface` and its container
//   • card ≥ 1.15:1 (light) / 1.10:1 (dark) against `background`
//   • `outlineVariant` ≥ 1.5:1 on a card, so a 1dp hairline is actually visible
// ─────────────────────────────────────────────────────────────────────────────

// ── Light ────────────────────────────────────────────────────────────────────
val md_theme_light_primary = Color(0xFF4338CA)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE0E7FF)
val md_theme_light_onPrimaryContainer = Color(0xFF1E1B4B)
val md_theme_light_secondary = Color(0xFF4F5B76)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFDCE3F0)
val md_theme_light_onSecondaryContainer = Color(0xFF131B2E)
val md_theme_light_tertiary = Color(0xFF0F766E)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFCBFBF1)
val md_theme_light_onTertiaryContainer = Color(0xFF042F2E)
val md_theme_light_error = Color(0xFFB42318)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFEE4E2)
val md_theme_light_onErrorContainer = Color(0xFF55160C)

// The app chrome is a cool grey and cards are pure WHITE. This pair is the
// single most important change: it gives every card an edge to sit on, which
// `background == surface` could never do. 1.16:1 + a 1dp hairline reads crisp.
val md_theme_light_background = Color(0xFFEBEEF4)
val md_theme_light_onBackground = Color(0xFF101828)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF101828)
val md_theme_light_surfaceVariant = Color(0xFFE1E5EE)
val md_theme_light_onSurfaceVariant = Color(0xFF46505F)
val md_theme_light_outline = Color(0xFF6B7484)
val md_theme_light_outlineVariant = Color(0xFFCBD1DB)
val md_theme_light_scrim = Color(0xFF000000)
val md_theme_light_inverseSurface = Color(0xFF2A3040)
val md_theme_light_inverseOnSurface = Color(0xFFEDEFF5)
val md_theme_light_inversePrimary = Color(0xFFB4B9FF)
// Light ramp: white surface → progressively greyer containers (M3 direction).
val md_theme_light_surfaceContainerLowest = Color(0xFFF7F8FB)
val md_theme_light_surfaceContainerLow = Color(0xFFF4F6FA)
val md_theme_light_surfaceContainer = Color(0xFFF1F3F8)
val md_theme_light_surfaceContainerHigh = Color(0xFFEAEDF4)
val md_theme_light_surfaceContainerHighest = Color(0xFFE1E5EE)

// ── Dark ────────────────────────────────────────────────────────────────────
val md_theme_dark_primary = Color(0xFFB4B9FF)
val md_theme_dark_onPrimary = Color(0xFF1E1B4B)
val md_theme_dark_primaryContainer = Color(0xFF3730A3)
val md_theme_dark_onPrimaryContainer = Color(0xFFE0E7FF)
val md_theme_dark_secondary = Color(0xFFB9C3DA)
val md_theme_dark_onSecondary = Color(0xFF222C42)
val md_theme_dark_secondaryContainer = Color(0xFF37415A)
val md_theme_dark_onSecondaryContainer = Color(0xFFDCE3F0)
val md_theme_dark_tertiary = Color(0xFF5EEAD4)
val md_theme_dark_onTertiary = Color(0xFF042F2E)
val md_theme_dark_tertiaryContainer = Color(0xFF115E59)
val md_theme_dark_onTertiaryContainer = Color(0xFFCBFBF1)
val md_theme_dark_error = Color(0xFFFDA29B)
val md_theme_dark_onError = Color(0xFF55160C)
val md_theme_dark_errorContainer = Color(0xFF7A271A)
val md_theme_dark_onErrorContainer = Color(0xFFFEE4E2)

// Near-black chrome, raised navy cards (1.13:1). Shadows are useless on this
// background, so layering is carried by tone + hairline instead.
val md_theme_dark_background = Color(0xFF05080C)
val md_theme_dark_onBackground = Color(0xFFE7EAF0)
val md_theme_dark_surface = Color(0xFF121822)
val md_theme_dark_onSurface = Color(0xFFE7EAF0)
val md_theme_dark_surfaceVariant = Color(0xFF2A3341)
val md_theme_dark_onSurfaceVariant = Color(0xFFAEB6C4)
val md_theme_dark_outline = Color(0xFF7C8797)
val md_theme_dark_outlineVariant = Color(0xFF333C4C)
val md_theme_dark_scrim = Color(0xFF000000)
val md_theme_dark_inverseSurface = Color(0xFFE7EAF0)
val md_theme_dark_inverseOnSurface = Color(0xFF121822)
val md_theme_dark_inversePrimary = Color(0xFF4338CA)
// Dark ramp: near-black surface → progressively lighter containers (M3 direction).
val md_theme_dark_surfaceContainerLowest = Color(0xFF0B0F16)
val md_theme_dark_surfaceContainerLow = Color(0xFF161D29)
val md_theme_dark_surfaceContainer = Color(0xFF1A2130)
val md_theme_dark_surfaceContainerHigh = Color(0xFF202938)
val md_theme_dark_surfaceContainerHighest = Color(0xFF2A3341)
```

### Step 2 — Add `ui/theme/Shape.kt`, `ui/theme/Dimens.kt`, and extend `Theme.kt`

**New `ui/theme/Shape.kt`** (replaces 13 ad-hoc radii with 5 roles):

```kotlin
package com.example.spendwise.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Five corner roles and nothing else. Before this existed the UI used 13
 * different radii (2/3/4/8/10/12/14/16/18/20/24/28dp + a 50% pill), including
 * three different radii for "a card", which is why nothing looked related.
 */
val SpendwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips, small tiles, tags
    small = RoundedCornerShape(12.dp),       // text fields, segmented buttons
    medium = RoundedCornerShape(16.dp),      // cards (the default)
    large = RoundedCornerShape(20.dp),       // hero cards, dialogs
    extraLarge = RoundedCornerShape(28.dp),  // bottom sheets
)
```

**New `ui/theme/Dimens.kt`** (a 4pt scale; the UI currently uses 11 off-grid
values, incl. `14.dp` 28×, `18.dp` 18×, `10.dp` 20×):

```kotlin
package com.example.spendwise.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The only spacing values allowed. 4pt base, 8 steps. Everything else in the
 * codebase was eyeballed (2/3/5/6/10/14/18/22dp), which is what makes layouts
 * feel almost-aligned rather than crisp.
 */
object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp      // the screen gutter — already consistent, keep it
    val xl = 20.dp      // card inner padding
    val xxl = 24.dp     // section gap, sheet padding
    val xxxl = 32.dp    // empty-state outer
    val huge = 48.dp    // hero spacing

    /** Standard card inner padding + card corner, so cards stop diverging. */
    val cardPadding = xl
    val screenGutter = lg
}
```

**`Theme.kt` — add named text tokens.** This is what kills `.copy(alpha = …)`
on text (8 sites, one of which measures 2.48:1):

```kotlin
/**
 * Text colours are *measured*, never derived by multiplying one colour's alpha.
 * All three clear 4.5:1 on `background`, `surface` and `surfaceContainerHigh`
 * in both themes — worst case 4.96:1 (light tertiary on surfaceContainerHigh).
 * Contrast per token is listed in UI_UX_AUDIT.md §5.9.
 */
@Immutable
data class TextColors(
    val primary: Color,     // titles, money, values
    val secondary: Color,   // supporting copy, list subtitles
    val tertiary: Color,    // placeholders, captions, de-emphasised meta
)

private val LightTextColors = TextColors(
    primary = Color(0xFF101828),
    secondary = Color(0xFF46505F),
    tertiary = Color(0xFF5C6675),   // still ≥ 4.5:1 — a real placeholder colour
)

private val DarkTextColors = TextColors(
    primary = Color(0xFFE7EAF0),
    secondary = Color(0xFFAEB6C4),
    tertiary = Color(0xFF949CAB),
)

val LocalTextColors = staticCompositionLocalOf { LightTextColors }
```

Then extend the accessor and the provider:

```kotlin
object SpendwiseTheme {
    val colors: SemanticColors
        @Composable get() = LocalSemanticColors.current

    val text: TextColors
        @Composable get() = LocalTextColors.current
}

@Composable
fun SpendwiseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) darkSemanticColors() else lightSemanticColors()
    val textColors = if (darkTheme) DarkTextColors else LightTextColors

    CompositionLocalProvider(
        LocalSemanticColors provides semanticColors,
        LocalTextColors provides textColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = SpendwiseShapes,       // <-- new: real shape roles
            content = content,
        )
    }
}
```

Also update the two scheme builders to wire the token that was previously
unused, and swap the four semantic values:

```kotlin
// in lightColorScheme(...)
surfaceContainerLow = md_theme_light_surfaceContainerLow,
// in darkColorScheme(...)
surfaceContainerLow = md_theme_dark_surfaceContainerLow,

// semantic colours — the `warning` fix is the AA failure from §2.4
private fun lightSemanticColors() = SemanticColors(
    income = Color(0xFF0F6B34),   incomeContainer = Color(0xFFDCFAE6),
    expense = Color(0xFFC81E1E),  expenseContainer = Color(0xFFFEE4E2),
    transfer = Color(0xFF1D4ED8), transferContainer = Color(0xFFDBEAFE),
    warning = Color(0xFFB54708),  warningContainer = Color(0xFFFEF0C7),
)

private fun darkSemanticColors() = SemanticColors(
    income = Color(0xFF4ADE80),   incomeContainer = Color(0xFF05432A),
    expense = Color(0xFFFF8A8F),  expenseContainer = Color(0xFF4E1315),
    transfer = Color(0xFF93B4FF), transferContainer = Color(0xFF1B2A52),
    warning = Color(0xFFFDB022),  warningContainer = Color(0xFF4A2A05),
)
```

### Step 3 — Delete every alpha-faded **text** colour (8 sites)

`.copy(alpha = …)` on text is what makes copy "blend in", because the effective
ratio then depends on whatever is behind it. Replace each with a named token.

| File:line | Before | After |
|---|---|---|
| `AmountSection.kt:163` | `onSurfaceVariant.copy(alpha = 0.5f)` (**2.48:1**) | `SpendwiseTheme.text.tertiary` |
| `BudgetSummary.kt:65` | `onPrimaryContainer.copy(alpha = 0.75f)` | `colorScheme.onPrimaryContainer` |
| `BudgetSummary.kt:84` | `onPrimaryContainer.copy(alpha = 0.65f)` | `colorScheme.onPrimaryContainer` |
| `BudgetSummary.kt:101` | `onPrimaryContainer.copy(alpha = 0.65f)` | `colorScheme.onPrimaryContainer` |
| `BudgetSummary.kt:123` | `onPrimaryContainer.copy(alpha = 0.65f)` (dark **4.05:1**) | `colorScheme.onPrimaryContainer` |
| `AccountsScreen.kt:239` | `onPrimaryContainer.copy(alpha = 0.8f)` | `colorScheme.onPrimaryContainer` |
| `AccountsScreen.kt:258` | `onPrimaryContainer.copy(alpha = 0.7f)` | `colorScheme.onPrimaryContainer` |
| `AccountsScreen.kt:271` | `onPrimaryContainer.copy(alpha = 0.7f)` | `colorScheme.onPrimaryContainer` |

**Note on `BudgetSummary`:** dropping the alpha flattens the hierarchy inside
that card (every line becomes the same weight). Compensate with *hierarchy*, not
transparency — the big number is already `MoneySize.BALANCE`; make the
supporting labels `typography.labelSmall` in `onPrimaryContainer` instead of
dimming them. Changing size/weight/token is the legitimate way to de-emphasise;
opacity is not.

Non-text alpha stays: tint chips (`account.type.color.copy(alpha = 0.15f)`),
the camera overlay's `Color.White.copy(alpha = …)`, progress **tracks**
(`onPrimaryContainer.copy(alpha = 0.15f)`). Those are fills, not text.

### Step 4 — Make dividers visible again

`TransactionListItem.kt:99-103` currently draws at **1.17:1** (light) /
**1.24:1** (dark) — invisible.

```kotlin
// before
HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
)

// after — outlineVariant alone is 1.53:1 on a light card / 1.60:1 on a dark
// card: exactly the "barely there but present" a hairline should be.
HorizontalDivider(
    color = MaterialTheme.colorScheme.outlineVariant,
    thickness = 1.dp,
)
```

### Step 5 — Add `SpendwiseCard` and give every card a hairline

Create `ui/components/SpendwiseCard.kt`. A tonal step alone (1.16:1) reads soft;
the 1dp border is what makes the edge crisp.

```kotlin
package com.example.spendwise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The only card in the app.
 *
 * Why this exists: before it, cards used three different corner radii
 * (16/18/20dp), only one had an elevation, and none had a border — so on a
 * surface ramp that measured 1.07:1 they dissolved into the page.
 *
 * Layering rule (see UI_UX_AUDIT.md §5.1):
 *   light → tonal step (1.16:1) + 1dp hairline (1.53:1)
 *   dark  → tonal step (1.13:1) + 1dp hairline (1.60:1)
 * Shadows are deliberately NOT used: they do nothing on a near-black
 * background, so relying on them is what made dark mode look flat.
 */
@Composable
fun SpendwiseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
    )
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val shape = MaterialTheme.shapes.medium

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
        ) { content() }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = colors,
            border = border,
        ) { content() }
    }
}
```

Then route the ~13 raw `Card(...)` call sites through it:
`SettingsScreen` (×2), `InboxScreen`, `BudgetScreen`, `BudgetSummaryCard`,
`BudgetCategoryCard`, `CategoriesScreen`, `AccountsScreen`,
`CounterpartiesScreen`, `FriendsScreen`, `TransactionDetailScreen`,
`AccountEditScreen`.

**Important:** cards switch from `surfaceContainer*` to `surface` (white/raised).
Tinted containers are then reserved for *nested* panels — inner sections and
rows inside a card — which is what finally gives the app a real two-level
hierarchy instead of one flat plane.

### Step 6 — Fix the chrome (app bars + bottom bar)

`TopAppBar` defaults to `surface` and `MainNavigationBar` sets `surface`
explicitly. With the new palette `surface` is white while `background` is grey,
so unless you change this, bars and cards both become white and the page turns
back into one slab.

```kotlin
// every TopAppBar / CenterAlignedTopAppBar in the app
TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.background,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
    ),
    …
)
```

```kotlin
// MainScaffold.kt — MainNavigationBar
Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,  // bar stays raised
    shadowElevation = 0.dp,                     // dead on dark; use a hairline
) {
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
        Row( … )   // unchanged; also collapse the two identical UPI/else branches
    }
}
```

Net effect: grey page chrome, raised white bars and cards, hairline edges. The
screen finally has three readable planes instead of one.

### Step 7 — One amber for one meaning

```kotlin
// CategoriesScreen.kt:180 — before
progress >= 0.75f -> Color(0xFFF59E0B)

// after
progress >= 0.75f -> SpendwiseTheme.colors.warning
```

Then `grep -rn 'Color(0x' app/src/main/java/com/example/spendwise/ui` and confirm
the only remaining hits are under `theme/` plus the scanner overlay.

### Step 8 — Theme-aware categorical colours (accounts, avatars, donut)

`AccountsScreen.kt:84-87`, `ProfileScreen.kt:58,61-66` and
`FriendAvatar.kt:32-39` each carry their own hardcoded palette, so they are
identical in light and dark — and `#4CAF50` (stock Material green) is in the app
for no reason. Add one theme-aware set in `Theme.kt`:

```kotlin
/**
 * Categorical colours for things that are merely *different*, not meaningful:
 * account types, avatars, donut slices. Deliberately excludes income / expense
 * / transfer — those three encode direction, and reusing them as decoration
 * (which `donutPalette()` did) makes the legend teach the user nothing.
 */
@Immutable
data class CategoricalColors(val swatches: List<Color>)

private val LightCategorical = CategoricalColors(listOf(
    Color(0xFF4338CA), // indigo (brand)
    Color(0xFF0F766E), // teal
    Color(0xFFB54708), // amber
    Color(0xFF7E22CE), // purple
    Color(0xFF0369A1), // sky
    Color(0xFFBE185D), // magenta
    Color(0xFF854D0E), // bronze
    Color(0xFF334155), // slate
))

private val DarkCategorical = CategoricalColors(listOf(
    Color(0xFFB4B9FF), Color(0xFF5EEAD4), Color(0xFFFDB022), Color(0xFFD8B4FE),
    Color(0xFF7DD3FC), Color(0xFFF9A8D4), Color(0xFFFCD34D), Color(0xFF94A3B8),
))
```

Provide it via `CompositionLocalProvider` alongside the text tokens, expose
`SpendwiseTheme.categorical`, then:
- `AccountType` takes `SpendwiseTheme.categorical.swatches[i]` instead of a
  per-constant hardcoded value;
- `ProfileScreen`'s default becomes `MaterialTheme.colorScheme.primary` (not
  `#4CAF50`), and its 6 choices become categorical swatches;
- `FriendAvatar` indexes the same list;
- `donutPalette()` returns the categorical swatches, **not** the semantic colours.

### Step 9 — Theme plumbing (`values-night`, edge-to-edge, splash)

1. Add `app/src/main/res/values-night/themes.xml`:

```xml
<resources>
    <style name="Theme.Spendwise" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/spendwise_window_background</item>
    </style>
</resources>
```

2. `values/colors.xml` — delete the `purple_*`/`teal_*` template entries
   (0 uses) and replace with `<color name="spendwise_window_background">#EBEEF4</color>`;
   add `values-night/colors.xml` with `#05080C`.
3. Add the same `<item name="android:windowBackground">` to the **light**
   `themes.xml`. This is what kills the white flash on a dark-mode cold start.
4. `MainActivity` — keep `enableEdgeToEdge()`, and additionally force the icon
   appearance in a `SideEffect` keyed on the resolved `darkTheme` (§5.11). The
   explicit version is the reliable one, because `SystemBarStyle.auto` reads the
   **system** `uiMode` while your app supports a manual override.
5. Optional: add `androidx.core:core-splashscreen` and set
   `windowSplashScreenBackground` to the same window background.

### Step 10 — Normalise icons to one rule

Mechanical pass, file by file, applying §5.5:

| Use | Family |
|---|---|
| Navigation, actions, money direction | `Icons.Rounded` |
| Entity / category identity | `Icons.Filled` |
| Empty-state medallions | `Icons.Outlined` |

Fix the duplicates first, since they're the visible offenders — `Settings`,
`Sms`, `Sell`, `Storefront` and `ReceiptLong` each appear in two families (§5.5),
and `Close`/`Add` in all three. Note there are **two** navigation enums —
`navigation/Destination.kt` (bottom bar) and `navigation/SideNavDestination.kt`
(drawer) — so the rule has to be applied to both, and they currently disagree
(e.g. `Icons.Filled.Settings` in the drawer vs `Icons.Outlined.Settings` on the
Budget screen). Then the two labels: `Destination.MORE` → `"More"`, unify the
three names for the Inbox screen (§5.12), and align the UPI tab label with the
screen's own title (`"Scan & Pay"`).

### Step 11 — Spacing pass

1. Add `Dimens` (Step 2).
2. Repoint the common cases: card inner padding → `Dimens.xl` (20dp),
   screen gutter → `Dimens.screenGutter`, section gaps → `Dimens.xxl`.
3. Kill the off-grid values: `14 → 12|16`, `18 → 16|20`, `10 → 8|12`,
   `22 → 20|24`, `3/5/6 → 4`.
4. Re-check the three fixed sizes that will otherwise clip at large font scale:
   `MainScaffold`'s `.height(68.dp)` → `.heightIn(min = 64.dp)`,
   `TransactionListItem`'s row height, and `AmountSection`'s 40dp
   segmented buttons.

### Step 12 — Shape pass

1. Add `SpendwiseShapes` (Step 2) and pass it to `MaterialTheme`.
2. Replace every literal `RoundedCornerShape(n.dp)` on a card / field / tile with
   `MaterialTheme.shapes.medium` / `.small` / `.extraSmall`.
3. Replace `RoundedCornerShape(percent = 50)` with `CircleShape` (2 sites).
4. Target end state: **zero** raw radius literals outside `Shape.kt`.

### Step 13 — Copy

- `"Buffer Inbox !!!"` → `"Inbox"` (`InboxScreen.kt:174`).
- Move screen titles, empty-state copy and Settings labels out of Kotlin literals
  into `strings.xml` — this also makes the app translatable.
- `ManageBudgetDialog`: use `RUPEE_SIGN` / `settings.currency` instead of a
  hardcoded `₹` inside the label string.

### Step 14 — Verify

1. Re-run the contrast script — `python3 scripts/contrast_check.py` — and confirm
   it reports **TOTAL FAILURES: 0** and exits 0.
2. `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest`
   (the latter covers `MoneyFormatTest` + `UpiQrTest`, which must stay green).
3. Screenshot every screen in **both** themes at default *and* 200% font scale.
   Check: does any card edge disappear? Does any text sit on a same-coloured
   background? Are there exactly three planes (chrome / card / nested panel)?
4. Run a greyscale / colour-blindness preview on Transactions: income and
   expense rows must still be distinguishable by sign + arrow. Luminance
   separation improves to 1.15:1 (light) / 1.30:1 (dark), but that is still not
   enough on its own — the sign and arrow remain mandatory.

### Suggested commit sequence

| # | Commit | Content |
|---|---|---|
| 1 | `feat(ui): Indigo Ledger palette + shapes + dimens` | Steps 1, 2, 12 |
| 2 | `fix(ui): restore surface separation and kill alpha-faded text` | Steps 3, 4, 5, 6 |
| 3 | `fix(ui): one amber, theme-aware categorical colours` | Steps 7, 8 |
| 4 | `fix(ui): dark-mode window background + system bar appearance` | Step 9 |
| 5 | `chore(ui): icon family rule, spacing scale, copy` | Steps 10, 11, 13 |

Commits 1–2 are the ones that resolve your complaint; 3–5 are independent.

---

## 7. What's genuinely good — do not touch

The audit above is a long list of problems, so it's worth being explicit about
what is working, because most of it is not obvious and a future refactor could
easily destroy it:

1. **`MoneyText` is the single money renderer.** Semantic colour, `tnum`,
   count-up animation, size roles, and both `Long`/`String` overloads. Screens
   no longer hand-format money anywhere in `main/`.
2. **The `groupIndian()` gotcha was caught.** `DecimalFormat("##,##,##0")`
   silently returns ungrouped digits on the Android runtime; the hand-rolled
   implementation is deterministic and pinned by `MoneyFormatTest`. That kind of
   bug is normally found in production.
3. **Real tabular figures.** Outfit was chosen *because* `tnum` is present in
   its GSUB table — verified, not assumed.
4. **`dynamicColor = false`.** Correct: Material You would override the brand.
5. **Motion vocabulary exists and is consistent** — `animateItem()`, springs,
   the approve-tick pop before removal, count-up money, donut sweep, bar grow,
   directional nav transitions.
6. **`EmptyState` is shared** across Transactions, Inbox, Friends and Budget,
   with a spring-in — and it's a real component, not four copies.
7. **Charts with zero new dependencies.** `CategoryDonut` and `SpendBarChart`
   are hand-drawn on Canvas; `ScannerScreen` was split 1,164 → 723 lines.
8. **Consistent 16dp screen gutters** across all 9 list screens.
9. **`MoneySemantic` / `MoneySize` enums** — a good, screen-agnostic API.
10. **Deliberate density.** The flat 56dp row (vs an 86dp card+gap) is
    documented in `TransactionListItem` with the reasoning. Keep it.

---

## Appendix A — the current palette, measured ("before")

These figures come from the audit's measurement pass over the values currently
in `ui/theme/Color.kt`. `scripts/contrast_check.py` validates the *proposed*
palette; to re-derive the "before" column, paste the Emerald Ink values into its
`LIGHT` / `DARK` / `SEMANTICS_*` tables and re-run.

| Check | Measured | Verdict |
|---|---|---|
| Light `onSurface` on background | 16.26:1 | pass |
| Light `onSurfaceVariant` on background | 8.88:1 | pass |
| **Light `surfaceContainer` vs background** | **1.07:1** | **fail** (invisible card) |
| **Light `surfaceContainerHigh` vs background** | **1.12:1** | **fail** |
| **Light `surfaceContainerHighest` vs background** | **1.18:1** | **fail** |
| Light `outline` on background | 4.28:1 | ok for borders |
| **Light `outlineVariant` on card** | **1.52:1** | borderline; then ×0.4 → **1.17:1** |
| **Dark `surfaceContainer` vs background** | **1.12:1** | **fail** |
| **Dark `surfaceContainerHigh` vs background** | **1.27:1** | borderline |
| Dark `onSurface` on background | 14.56:1 | pass |
| **Light `warning` `#D97706` on background** | **3.04:1** | **fail as text (AA)** |
| Light `income` `#15803D` on background | 4.78:1 | pass |
| Light `expense` `#DC2626` on background | 4.60:1 | pass |
| Light `transfer` `#2563EB` on background | 4.92:1 | pass |
| **Light `income` vs `expense` (greyscale)** | **1.04:1** | **fail without a sign** |
| **Placeholder `onSurfaceVariant@0.5` on background** | **2.48:1** | **fail (AA)** |
| **Dark `onPrimaryContainer@0.65` on container** | **4.05:1** | **fail (<18sp)** |
| Light divider `outlineVariant@0.4` on card | 1.17:1 | fail (invisible) |

**Totals: 8 hard failures + 3 borderline, across 25 checks.**

## Appendix B — reproducing these numbers

A runnable validator ships with this audit:

```bash
python3 scripts/contrast_check.py     # exits 1 if any check fails
```

It validates four groups (light text, light surfaces, dark text, dark surfaces)
against WCAG AA plus the surface-separation thresholds, and separately reports
the income-vs-expense luminance separation. Current result: **0 failures across
60 checks**, exit code 0.

Design rules it pins (treat these as the contract for any future palette edit):

| Rule | Threshold |
|---|---|
| Any colour used as text | ≥ 4.5:1 on `background`, `surface`, and its own container |
| Card vs app background | ≥ 1.15:1 light, ≥ 1.10:1 dark |
| Card hairline (`outlineVariant` vs card) | ≥ 1.5:1 |
| Nested panel vs card | ≥ 1.04:1 (felt, not seen) |
| income vs expense | report it, and always pair with sign + arrow |
| **Never** express a text colour as `otherColour.copy(alpha = …)` | — |
