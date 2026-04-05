# Spearo Go — Xcode Setup Guide

Step-by-step instructions for creating the Xcode project and wiring everything up so the app stays pixel-perfect to the brand from day one.

---

## Step 1 — Create the Xcode project

1. Open Xcode → **File → New → Project**
2. Select **watchOS → App** → Next
3. Fill in:
   | Field | Value |
   |-------|-------|
   | Product Name | `SpearoGo` |
   | Team | Visivo Agency (your Apple Developer account) |
   | Organisation ID | `agency.visivo` |
   | Bundle ID | `agency.visivo.SpearoGo` |
   | Interface | SwiftUI |
   | Language | Swift |
   | Include SwiftData | ✅ checked |
4. Save into `~/Documents/spearo-go/`
5. Xcode creates `SpearoGo.xcodeproj` — this is your project file going forward

---

## Step 2 — Add the Swift source files

1. In the Xcode Project Navigator, right-click the `SpearoGo` group → **Add Files to "SpearoGo"**
2. Navigate to `~/Documents/spearo-go/SpearoGo/`
3. Select **all folders and files** — Views, Models, Services, Utils, AppState.swift, ContentView.swift
4. Make sure **"Add to targets: SpearoGo"** is checked
5. Click Add

> Xcode will have already created stub `ContentView.swift` and `SpearoGoApp.swift` — **delete those stubs** and use the ones from the repo.

---

## Step 3 — Replace the Assets catalog

Xcode creates a default `Assets.xcassets`. Replace it entirely:

1. Delete Xcode's generated `Assets.xcassets` (Move to Trash)
2. Drag `~/Documents/spearo-go/SpearoGo/Assets.xcassets` into the project navigator
3. Ensure "Add to targets: SpearoGo" is checked

The catalog contains:
```
Assets.xcassets/
├── AppIcon.appiconset/     ← slots ready, add PNG artwork here
└── Colors/
    ├── Background          #000000
    ├── OceanBlue           #0077B6
    ├── Teal                #00B4D8
    ├── TextPrimary         #FFFFFF
    ├── TextSecondary       #6B7D8E
    ├── VerdictGo           #2ECC71
    ├── VerdictMaybe        #F39C12
    ├── VerdictSketchy      #E67E22
    └── VerdictNoGo         #E74C3C
```

---

## Step 4 — Verify build settings

In **Project → SpearoGo target → Build Settings**:

| Setting | Value |
|---------|-------|
| Deployment Target (watchOS) | 10.0 |
| Swift Language Version | Swift 5 |
| Supported Destinations | Apple Watch |
| Enable SwiftData | Yes (auto if you checked it at creation) |

In **Signing & Capabilities**:
- Add **HealthKit** (not used yet, but needed for future dive logging)
- Confirm **Background Modes → Background App Refresh** is on (Sprint 2)

---

## Step 5 — Run in simulator

1. Select scheme **SpearoGo** → **Apple Watch Series 9 (45mm)** simulator
2. **⌘R** to build and run
3. The app should launch to the Verdict page with a spinner, then fetch live data

If you see "No such module 'SwiftData'" errors, check the iOS deployment target — it must be watchOS, not iOS.

---

## Step 6 — Set up Xcode Previews

Every view file should have a `#Preview` block at the bottom. Use the helpers from `PreviewHelpers.swift`:

```swift
// Minimal — single page, GO state
#Preview("Verdict – GO") {
    VerdictPage()
        .previewAsWatch()
        .environment(AppState.preview(verdict: .go))
}

// All 4 verdict states side by side
#Preview("All Verdicts") {
    AllVerdictsPreview()
}
```

**Watch sizes available:**
- `.mm41` — 176×215pt
- `.mm44` — 184×224pt (default in previews)
- `.mm45` — 198×242pt
- `.mm49` — 205×251pt (Ultra)

---

## Step 7 — Using brand tokens (not raw hex)

**Always use `Brand.*` or named colors — never hardcode hex strings in views.**

```swift
// ✅ Correct
.foregroundStyle(Brand.Colors.primary)
.font(Brand.Typography.dataValue)
.padding(Brand.Spacing.page)
Color("OceanBlue")          // resolves from xcassets

// ❌ Wrong — breaks if colors change
.foregroundStyle(Color(hex: "#0077B6"))
.font(.system(size: 18, weight: .bold))
.padding(12)
```

If you need to add a new color:
1. Add the colorset in `Assets.xcassets/Colors/` (duplicate an existing one)
2. Add a static property in `Brand.Colors`
3. Use it via `Brand.Colors.yourNewColor`

---

## Step 8 — Adding a new screen

Checklist for every new SwiftUI view:

```swift
import SwiftUI

struct MyNewPage: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        VStack(spacing: Brand.Spacing.section) {
            // 1. Section header
            Text("MY PAGE")
                .brandSectionHeader()

            // 2. Content using Brand tokens
            Text("Value")
                .dataValueStyle()

            // 3. Black background
        }
        .padding(Brand.Spacing.page)
        .brandPage()   // ← always last — sets the black background
    }
}

// 4. Preview
#Preview {
    MyNewPage()
        .previewAsWatch()
        .environment(AppState.preview())
}
```

---

## Step 9 — App Icon workflow

The `AppIcon.appiconset` has slots ready. To add artwork:

| Slot | Size | Notes |
|------|------|-------|
| Watch app launcher (38–40mm) | 88×88px @2x | |
| Watch app launcher (44mm) | 100×100px @2x | |
| Watch app launcher (45mm+) | 102×102px @2x | |
| Watch notification | 66×66px @2x | |
| Watch Quick Look | 88×88px @2x | |
| App Store / marketing | 1024×1024px @1x | Required for submission |

**Icon design rules:**
- Background: `#000000` or deep ocean `#0077B6`
- No transparency (App Store rejects transparent icons)
- No rounded corners in artwork — watchOS clips them automatically
- Simple silhouette reads well at 44px

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Color("Background")` shows white | xcassets not added to target — check "Target Membership" in File Inspector |
| `@Observable` error | Confirm watchOS 10 deployment target, not watchOS 9 |
| SwiftData crash on first run | Delete app from simulator, clean build folder (⇧⌘K), rebuild |
| Preview shows wrong size | Use `.previewAsWatch(size: .mm45)` to match your simulator |
| `CLLocationManager` permission dialog missing | Add `NSLocationWhenInUseUsageDescription` to `Info.plist` |
