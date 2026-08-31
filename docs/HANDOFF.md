# Handoff

Where Spearo Go stands. Newest first.

---

## 2026-08-31 (release prep) — Store readiness sweep

Full audit ahead of App Store and Play submission. Seven findings, three of
them genuine blockers. See `docs/RELEASE-READINESS.md` for the submission
checklist.

**The privacy disclosure was false.** Both apps stated coordinates were "never
stored on any server". `tidesGo` stores a rounded coordinate for 24h and a
hashed IP for rate limiting. Corrected in the in-app text on both platforms, in
`PrivacyInfo.xcprivacy` (which declared no collected data at all), in both store
listings, and with the exact Play Data Safety selections written down.

**The listings claimed offline tides** and "M2+S2 harmonic analysis" — the model
that was deleted this morning. Rewritten across README and both metadata files.

**Two fabrications survived the earlier passes:** wave period defaulted to 0s,
which is not a calm sea but an absent reading, and cost −1 in the score via the
`< 6` branch; and a tide event with no height defaulted to 0m, which is a real
chart-datum value. Both now optional.

**A landlocked spot no longer returns a verdict.** "No sea here" replaces a GO
computed from wind and moon alone. Crucially it distinguishes *no coverage* from
*a failed lookup*, so an outage never triggers it — `TideLookup` and
`NoMarineCoverageException` carry that apart.

**Retention.** Every Firestore document the backend writes now carries an
`expiresAt`. The TTL policies that act on it still need enabling by hand —
`gcloud` was not installed. This is release blocker #1.

**Versions bumped:** watchOS 1.0.0 (2) → **1.1.0 (3)**, Wear 2.0.10 (14) →
**2.1.0 (15)**. `generate_xcodeproj.py` updated too, so regenerating the project
does not revert them.

Release builds clean on both platforms. 27 tests pass. The endpoint was
re-verified live after redeploying.

---

## 2026-08-31 (later) — Real tide predictions, end to end

The tide model was deleted earlier in the day. This is what replaced it.

### What changed

**`tidesGo` backend**, in `functions/`, deployed to the shared `spearo-tracker`
project. NOAA CO-OPS where it reaches (free, gauge-based), WorldTides
everywhere else, and an honest `outside_coverage` where neither answers.

The NOAA and WorldTides logic is **ported from Spearo Vision's**
`lib/services/tide_service.dart`, but lands server-side rather than in the
clients. Vision makes that decision in its Dart client; Go has two clients, and
copying a station list and event assembly into Swift *and* Kotlin is exactly how
Go's two `TideService` files came to hold the same defect twice.

**Both clients wired.** They fetch, cache a week, and render. The only tide
logic left in them is the derivation that genuinely cannot be precomputed —
which tide is next, whether the water is rising, the height right now — because
a week is cached and "next" changes by the minute.

**Provenance reaches the screen.** A named gauge, an ocean-model estimate and a
saved forecast read differently, because the grid model is materially weaker in
the inlets these users dive.

**Last fabrication removed:** `RefreshWorker` still built a flat-calm 22 °C sea
on a failed background lookup.

### Verified

Live against the deployed endpoint:

| Query | Result |
|---|---|
| Lagos, Portugal | `station: "Lagos"`, gauge, datum CD, UTC+1, 7 days |
| New York | `source: noaa`, "NEW YORK (The Battery)", MLLW — never billed |
| Queidersbach (landlocked) | `outside_coverage` |
| `lat=999` | HTTP 400 |

**The customer's report, resolved.** He saw a LOW at 04:49 and a HIGH at 11:00.
The Lagos gauge reads a HIGH at **04:50** and a LOW at **10:49**. The synthetic
model was landing within a minute on timing with high and low **inverted** —
divers were being sent in at the wrong end of the tide.

27 tests pass: 15 on the function, 12 on Kotlin (8 of them pinning the
derivation against that real Lagos day, including the inversion case).

**Not verified on-device.** The watch is landlocked, so it correctly shows "no
tide data"; and its wireless debugging drops whenever the screen sleeps, which
blocked the last install. To see real gauge data on the watch, save a coastal
spot (long-press the verdict page).

### Deploying

```
firebase deploy --only functions:spearogo:tidesGo
```

Note the codebase in the filter. Go owns `spearogo`; Vision owns `default`. Two
repos deploying one codebase would delete each other's functions.

### Pick up here

1. **Reply to the Lagos customer.** Vision's
   `docs/CUSTOMER-REPLY-alejandro-tides.md` is the template. The inversion
   finding is worth telling him — he was more right than he knew.
2. **Store listings** still advertise offline tides. See BACKLOG.
3. **Decide the landlocked case** — see BACKLOG.

---

## 2026-08-31 (earlier) — Fabricated data removed, weather page added

Triggered by a customer in Lagos, Portugal reporting tide times that matched no
real sequence. Investigation found the tide model was not the only invented
data in the app.

### What changed

**Tides — model deleted.** `TideService` returned a synthetic curve whose
comments claimed "±30–45 min" accuracy. It anchored its M2 term to the **Unix
epoch** rather than the Moon, discarded latitude entirely, and mapped every
location on earth onto a fixed 0–3 m range. Deleted rather than improved; the
service remains as the seam for the real backend. The Tides page now says there
is no data and the score renormalises without it.

**Marine — stopped inventing a sea.** Open-Meteo Marine returns **HTTP 200 with
null fields** for a landlocked coordinate, not the 400 the code expected, so the
catch-block fallback never fired. Null coalescing at the parse site turned
absent data into a 0.0 m sea at 20 °C, which scored 9/10. Verified on a watch in
Queidersbach, 400 km from the sea, showing "GO 8.3 — Perfect day. No excuses."

**Solunar — four bugs.** `moonTransit` took the complement of the hour angle
(inverting the offset), assumed the opposite culmination was 6h away rather than
12h25m, and anchored its result to `Date()` rather than the date passed in.
Separately, `riseSet` placed its result with `ra - longitude / 15`, an
expression that never references sidereal time, so sunrise and sunset came out
inverted and thirteen hours wrong. Culminations and rise/set are now solved
numerically and agree with independent computation to under a minute.

**Weather — nulls no longer defaulted.** Wind speed and direction are required;
a 0 kn default reads as a flat calm and scores as ideal. Visibility and cloud
cover render as "—" and are skipped by the score.

**Score** renormalises over the signals actually measured (`DiveScore.calculate`
takes optionals), and the verdict card names what it could not see.

**Typography.** The scale bottomed out at 8 pt/sp against a ~12 legibility
floor, and watchOS ignored Accessibility → Larger Text entirely because
`Font.system(size:)` is a fixed size. Raised so nothing sits below 11, and
scaled via `@ScaledMetric` on watchOS, bounded at `accessibility2` / fontScale
1.3 because these layouts truncate rather than reflow.

**Verdict page** restyled to Spearo Vision's dive score card: full-bleed verdict
gradient, white type, white-on-translucent ring. This adopted Vision's colour
semantics, which moves MAYBE from orange to blue.

**New Today page**, second in the pager: two vertically paged screens. "Today"
carries condition, current temperature and the day's high/low; "Sky" carries
rain, cloud and daylight times.

### Current state

- Both targets build. 8 JUnit tests pass (4 solunar, 4 score). Swift has no test
  target; its solunar output was verified by running the shipping code
  standalone against the same fixtures.
- Verified on a real Galaxy Watch Ultra (SM-L705F, Wear OS 6) by sideloading.
  Build a sideload APK with `-Psideload` — it installs alongside the Play copy
  under `com.spearotracker.spearogo.sideload`, because a locally built APK can
  never update the store install (Play App Signing re-signs uploads).
- Weather and solunar are verified accurate against independent computation.
  Marine and tides honestly report nothing where there is no data.

### Pick up here

1. **Build `tidesGo`** — the design is written and approved in
   `docs/superpowers/specs/2026-08-31-tide-accuracy-design.md`. WorldTides
   credit headroom is confirmed available (2026-08-31), so §7 item 1 is closed.
   Order of work is in §9 of that spec.
2. **Reply to the Lagos customer.** Spearo Vision's
   `docs/CUSTOMER-REPLY-alejandro-tides.md` is the template — he found a real
   defect and deserves the same treatment.
3. **Decide the landlocked case** — see BACKLOG.
