# Session Summary — Wear OS Play Store Submission & Rejection Fixes

Date: 2026-04-19
Platform: Google Play Console (Wear OS track)
App: Spearo Go (`com.spearotracker.spearogo`)

## Goal
Publish the first Wear OS release of Spearo Go to Google Play and respond to the
Play policy rejection we received on the initial submission.

## Version progression
| Version | versionCode | Notes |
|---------|-------------|-------|
| 2.0.3   | 7  | Pre-session baseline |
| 2.0.4   | 8  | First AAB built this session; uploaded but superseded before submit |
| 2.0.5   | 9  | Rebuilt after Play Console reuse error on versionCode |
| 2.0.6   | 10 | First AAB that successfully reached the Wear OS Production track |
| 2.0.7   | 11 | Rebuilt after the release was marked "Superseded by another release" |
| 2.0.8   | 12 | Policy-compliance fixes (splash, scrollbar, scrollable pages) |
| 2.0.9   | 13 | Final bundle of this session, shipped with updated store listing |

Every AAB above is signed with the release keystore (`spearogo-release.keystore`,
which is gitignored and lives only on the developer machine).

## What the Play reviewer flagged (4 issues)
1. Wear App Quality Guidelines — Play listing description: listing didn't
   mention Tile support even though the app ships a tile service.
2. Wear App Quality Guidelines — Missing app icon in splash screen: launch did
   not show the 48dp app icon on a black background (branded launch).
3. Wear App Quality Guidelines — Wear app functionality not working as
   described: text was cut off when the system font size was set large.
4. Wear App Quality Guidelines — Missing scrollbar: scrollable views did not
   display a scroll indicator (evidence screenshot was the Info/privacy page).

## What we fixed in code

### 1. Branded launch splash screen
- Added `androidx.core:core-splashscreen:1.0.1` to
  [wear/app/build.gradle.kts](../wear/app/build.gradle.kts).
- Defined `Theme.App.Splash` in
  [wear/app/src/main/res/values/styles.xml](../wear/app/src/main/res/values/styles.xml)
  with `windowSplashScreenBackground=@android:color/black` and
  `windowSplashScreenAnimatedIcon=@mipmap/ic_launcher`, with
  `postSplashScreenTheme=@style/AppTheme` to hand off to the app theme after
  launch.
- Pointed the launcher activity at the splash theme in
  [wear/app/src/main/AndroidManifest.xml](../wear/app/src/main/AndroidManifest.xml).
- Called `installSplashScreen()` at the top of
  [MainActivity.onCreate()](../wear/app/src/main/java/com/spearotracker/spearogo/MainActivity.kt).

### 2. Scroll indicator on all scrollable views
Wrapped the scrollable content of every page in Wear Compose Material3
`ScreenScaffold(scrollState = ...)`, which provides the platform scroll
indicator for free. Pages updated:
- `InfoPage.kt`
- `TidesPage.kt`
- `FishActivityPage.kt`
- `VerdictPage.kt`
- `ConditionsPage.kt`
- `WaterPage.kt`

### 3. Large-font safety
Converted the previously non-scrollable pages (`VerdictPage`, `ConditionsPage`,
`WaterPage`, and the two `OnboardingScreen` sub-pages that lacked scrolling) to
use `verticalScroll(rememberScrollState())` so oversized system font scales
scroll rather than clip.

### 4. Store listing description
Updated [docs/GOOGLE_PLAY_METADATA.md](GOOGLE_PLAY_METADATA.md) to add an
explicit "TILE SUPPORT" section and a "Dedicated Wear OS Tile" bullet in the
FEATURES list so the reviewer can see that Tile support is documented.

## Play Console gotchas we hit (not code issues)
- A Wear OS release cannot be uploaded to a phone track. Use the Wear OS form
  factor's own Production/Testing track.
- `versionCode` is immutable per upload, even for drafts — every new AAB needs a
  fresh `versionCode`.
- "Superseded by another release" means the AAB was replaced by a newer draft
  before being sent for review. After uploading, you must click
  **Review release → Start rollout to production** to actually submit.
- Tracks remain "Inactive" until a release is sent for review and approved.

## Build command used
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd wear && ./gradlew bundleRelease
```
Output: `wear/app/build/outputs/bundle/release/app-release.aab`.

## Files touched this session
- [wear/app/build.gradle.kts](../wear/app/build.gradle.kts) — versionCode/Name bumps, splash dep
- [wear/app/src/main/AndroidManifest.xml](../wear/app/src/main/AndroidManifest.xml) — splash theme on MainActivity
- [wear/app/src/main/res/values/styles.xml](../wear/app/src/main/res/values/styles.xml) — `Theme.App.Splash`
- [wear/app/src/main/java/com/spearotracker/spearogo/MainActivity.kt](../wear/app/src/main/java/com/spearotracker/spearogo/MainActivity.kt) — `installSplashScreen()`
- `wear/app/src/main/java/com/spearotracker/spearogo/ui/pages/*.kt` — `ScreenScaffold` + scroll
- [docs/GOOGLE_PLAY_METADATA.md](GOOGLE_PLAY_METADATA.md) — Tile mention, release notes
- [.gitignore](../.gitignore) — excluded `*.keystore`, `*.jks`, `local.properties`
