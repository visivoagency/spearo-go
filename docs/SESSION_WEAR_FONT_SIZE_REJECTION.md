# Session — Play rejection: "Wear font size" (2026-09-07)

Google Play rejected Wear 2.1.0 (15) on 2026-09-07 under **Wear App Quality
Guidelines: Wear font size**:

> Your app must conform to the font size set by the user in System Settings.
> If the user selects a larger font size, ensure that text and controls are
> not cut off by screen edges.

Evidence: two in-app screenshots, Fish Activity and Conditions, at a large
text size. "CONDITIONS" was clipped to "CO…NS" by the top of the round screen
and the "No swell data" caption was cut by the bottom edge.

This was the **second** rejection for the same guideline. The first, on
2026-04-19, was answered by making the pages scroll
(`SESSION_WEAR_PLAY_SUBMISSION.md`). Scrolling was necessary but not
sufficient.

## Root cause

Two things, both in `wear/`:

1. **A font-scale ceiling.** `SpearoGoTheme` clamped `LocalDensity.fontScale`
   to 1.3 (added 2026-08-31 because the layouts "truncate rather than
   reflow"). That is a direct violation of the first sentence of the guideline,
   and a reviewer can see it: past the ceiling the app stops responding to the
   setting.
2. **Fixed insets on a round screen.** Every data page was a
   `Column.fillMaxSize().verticalScroll().padding(12.dp)` centred in the
   viewport. Short content sits in the middle of the circle, where it is
   widest, and looks fine. Once the text grows the content outgrows the
   viewport, the scroll puts the first line 12 dp from the top — under the
   system clock, on a chord about 100 dp wide — and the last line on the
   bottom arc under the pager dots. Nothing was wrong with the text sizes
   themselves; they are in `sp` and scale correctly.

The ceiling did not even engage on the device that produced the evidence: Wear
OS 5's Text size picker tops out at font scale 1.24 ("Largest"), below 1.3. The
clipping is purely geometry.

## Reproduced

On the `WearOS5_SpearoGo` emulator (Wear OS 5, API 34, 454 px round):

- Settings → Display → Text size steps: 1.0, 1.06, 1.12, 1.18, 1.24.
- At 1.24 with 2.1.0 code: "TIDES" collided with the clock, the tide station
  name was cut by the bottom arc, "CONDITIONS" and "FISH ACTIVITY" sat hard
  against the clock. Matches the reviewer's screenshots.
- Also checked at 1.5 via `adb shell settings put system font_scale 1.5`,
  beyond anything the picker offers, as a stress case.

Note: with `targetSdk = 35` the app **crashes at launch on this emulator
image** — Wear Compose Foundation 1.4.1 reads the `reduce_motion` global
setting, which API 34's settings provider refuses to apps targeting 35
(`SecurityException: Settings key: <reduce_motion> is only readable to apps
with targetSdkVersion lower than or equal to: 34`). Real Wear OS 5/6 watches do
not do this (the Play build was reviewed on one). For emulator work, build with
`targetSdk = 34` temporarily; do not commit that. Tracked in `BACKLOG.md`.

## Fix

- **`SpearoGoTheme`** no longer bounds the font scale.
- **`ui/components/ScrollingPage.kt`** — one shared page/list layout, used by
  every screen: Verdict, Today (both screens), Conditions, Water, Tides, Fish
  Activity, Spots, Info and the three onboarding pages.
  - The **viewport** is inset, not the content: top 10% and bottom 12% of the
    screen are outside the scroll, so the clock and the pager dots are never
    overlapped and neither arc can cut a line. Sides are 8%.
  - The first line starts 5% below the viewport top, i.e. at 15% of the
    screen, clear of the time text.
  - When the content is taller than the viewport its edges **fade** (20% of
    the viewport) while there is more to scroll in that direction — the cue
    every Wear list gives, instead of a hard cut.
  - A page that overflows gets 8% of extra room under its last line, decided at
    **measure time** from the content height. It must not key off
    `ScrollState.maxValue`: that is unbounded before first layout, and a
    spacer driven by it made every page scroll at the default size.
  - Content that fits is still centred, so the default look is unchanged
    (verified by screenshot at 1.0).
  - Percentages, not dp, so a 192 dp Pixel Watch and a 227 dp Galaxy Watch get
    the same geometry.
- **`ConditionRow` / `RowScope.ConditionItem`** — reading rows now share the
  row width (`weight(1f, fill = false)`) so at a very large scale two items
  wrap rather than run past the edges.
- The tide station name on Tides may wrap to two lines with an ellipsis rather
  than being clipped on one.
- Version 2.1.1 (16). `Constants.App.VERSION` (shown on Info) was still
  "1.0.0"; now matches.

## Verified

Every screen screenshotted on the emulator at font scale 1.0, 1.24 and 1.5,
at rest and scrolled to the end: nothing touches the clock, the pager dots or
the arcs; last lines are fully visible at the end of the scroll; the default
size is pixel-for-pixel the same layout as before. 27 JUnit tests pass.

Not verified on a physical watch this session. Worth a sideload on the Galaxy
Watch Ultra at its largest text size before upload (`-Psideload`).

## Not done

- **watchOS** still caps Dynamic Type at `accessibility2` in
  `SpearoGoApp.swift`. Apple has not objected and the `CLAUDE.md` lockstep rule
  is about the type scale, which is unchanged, but the Kotlin comment that
  claimed the two ceilings mirror each other is gone. Backlog.
- `ScrollingPage` uses a plain `Column`; a `TransformingLazyColumn` would give
  Wear's native edge scaling but changes the centred layout of short pages.
