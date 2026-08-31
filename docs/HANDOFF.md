# Handoff

Where Spearo Go stands. Newest first.

---

## 2026-08-31 — Fabricated data removed, weather page added

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
