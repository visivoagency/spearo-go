# Spearo Go: optional sign-in and two-way spot sync

## Context

The owner wants Go to be part of the diver's Spearo account, so the account and spearo.pro hold a record of the spots saved on the watch. Go is the watch app; Vision is the phone app for conditions. They decided on 23 Oct:

- **Two-way spots.** Spots saved on the watch go to the account. Spots saved on spearo.pro, in Vision or in Tracker show up on the watch.
- **The diver only signs in.** There is no code to type and no extra step. After that, Go writes to Firebase on its own, and the website shows the data as soon as it is in the database.
- **Sign-in stays optional.** Go keeps working fully without an account. There is no subscription and no Pro.

No verdict history gets synced. Go keeps none, and the owner agreed that Vision already covers conditions on the phone.

## What already exists (so most of this is reuse)

- **The website reads spots from the database live.** `subscribeSpots` in `Spearo-Empire/website/src/lib/firestore.ts:249` listens to `users/{uid}/spots` and to top-level `spots` where `userId == uid`, and merges them. The Spots page (`dashboard/spots`) has a map. Go spots written to `users/{uid}/spots` appear there **with no website code change**.
- **The rules already allow it.** `users/{uid}/{document=**}` is owner-only (`firestore.rules:112`), and top-level `spots` can be read by the owner (`:120`). There are no new paths, so no rules change.
- **Apnea's watches already sign in this way, and we copy them:**
  - **Wear OS:** Google through RemoteAuth (the phone browser opens and the diver taps their account), then the Firebase Android SDK. Reference: `Spearo-apnea/Spearo Apnea Watch/.../data/sync/FirestoreSyncClient.kt`. Owner rule: never on-watch GoogleSignIn (it fails with 12501).
  - **Apple Watch:** Sign in with Apple natively on the watch (`ASAuthorizationAppleIDProvider`), then FirebaseAuth. Data goes through the Firestore REST API, because the Firestore SDK doesn't support watchOS. Reference: `Spearo-apnea/Spearo Apnea/Spearo Apnea Watch App/Services/AuthManager.swift` and `Data/Sync/FirestoreSyncClient.swift`.
- **Go's spots are the same on both watches:** `id, name, latitude, longitude, createdAt, isActive`.
  - Wear stores them in Room: `wear/.../models/SavedLocation.kt`, `services/LocationDao.kt`, `AppDatabase.kt` v1.
  - watchOS stores them in SwiftData: `SpearoGo/Models/SavedLocation.swift`.

## Design

### Signing in

- **Where:** Settings or Spots gets a row: "Sign in to sync your spots with spearo.pro". Apple Watch uses Sign in with Apple. Wear uses Google through the phone. Signing out keeps every spot on the watch.
- **One account per diver:** sign-in uses the shared `spearo-tracker` Auth.
  - On `account-exists-with-different-credential`, the watch says "You already have a Spearo account with this email. Sign in the way you did before." On a watch that means the other platform's method or the website. Watch-side linking is out of scope.
  - **My call:** the watch only offers one provider per platform, so it shows the message and stops. The diver links methods on spearo.pro (Settings → Sign-in methods already exists there).
- **Hidden-email Apple accounts:** a brand-new account with a `privaterelay` email is a known source of duplicates. **My call:** don't handle it on the watch. The website's existing "I already have an account" banner covers it when they next visit.

### What Go writes

- **Spots:** `users/{uid}/spots/{id}`, in the same shape and casing as the website's `createSpot`/`webRecord` (`firestore.ts:429`).
  - Fields: `userId`, `name`, `latitude`, `longitude`, `isPrivate: true`, `isDeleted`, `createdAt`, `lastModifiedAt`, `cloudId`, plus `source: "go"`.
  - Before building, the builder checks `webRecord` so the casing matches exactly.
  - Location never goes anywhere public.
- **Marker:** `users/{uid}.apps.go.last_seen_at` (server timestamp), written on sign-in and at most once a day after that. The website uses it to show Go as connected.

### Sync rules

- **On sign-in (first sync):**
  - Upload every local spot. **My call:** a local spot whose coordinates are within ~100 m of an account spot with the same name counts as the same spot, so it isn't duplicated.
  - Then pull the account's spots: `users/{uid}/spots`, plus top-level `spots` where `userId == uid` (Vision's), skipping `isDeleted`.
- **After that:**
  - Pull when the app opens and on a manual "Sync now".
  - Push right after a spot is saved, renamed or deleted on the watch.
- **Deleting:** a spot deleted on the watch is soft-deleted in the account (`isDeleted: true`, which matches Tracker, Vision and the website). A spot deleted elsewhere disappears from the watch on the next pull.
- **Conflicts:** last `lastModifiedAt` wins.
- **Stays on the watch:** `isActive` (which spot the watch is showing). The account spot id is stored locally so the two stay matched.
- **Vision's top-level spots:** Go can't write to them. **My call:** Go shows them but can't delete them; the row says "Saved in Vision".
- **No connection:** changes queue locally and push on the next sync. Sync must never block the verdict or show made-up data. If sync fails, Go still works from local spots, and the sync row says what failed.

### Store, privacy and copy (part of this work)

- **Privacy disclosures:** App Store privacy label, Play Data safety, `PrivacyInfo.xcprivacy` and in-app privacy text. They must now say this: if you sign in, your email and the names and coordinates of your saved spots are stored in your Spearo account, linked to you, private, and not shared.
- **CLAUDE.md:** change "No account, no subscription" to "No subscription; account optional, for spot sync".
- **README and listings:** "no account required" stays true.

## Not in scope (handoffs, not edits; one app per conversation)

- **Website** (`Spearo-Empire/docs/` handoff):
  - Drop `go` from `NO_ACCOUNT_APPS`.
  - Treat `apps.go.last_seen_at` as "Go connected".
  - Optionally badge `source: "go"` spots.
  - Decide what the 3-spot free limit means when Go has synced more.
- **Rules tests** (`Spearo-Empire/firebase/rules-test`): add one test that the owner can write `users/{uid}/spots` with `source: "go"`, and that another user can't. The rules themselves don't change.

## Implementation outline

Built in this order, with Swift and Kotlin in the same commits (CLAUDE.md lockstep rule):

1. **Spec into the repo.** Commit this as `docs/superpowers/specs/2026-10-23-go-account-spot-sync-design.md`.
2. **Local model.**
   - Add `cloudId`, `lastModifiedAt`, `isDeleted`, `pendingSync` and `source` to `SavedLocation` on both platforms.
   - Wear: Room v1 → v2 migration. Existing spots must survive it; add a test.
   - watchOS: a SwiftData lightweight migration.
3. **Wear sign-in and sync.**
   - Add Firebase Auth and Firestore plus RemoteAuth (copy Apnea's client), behind a small `SpotSyncService`.
   - Wear also needs a `google-services.json`, and a separate Firebase app for the `.sideload` id.
4. **watchOS sign-in and sync.**
   - Add the FirebaseAuth package, Sign in with Apple (entitlement plus capability) and a REST `SpotSyncService` (copy Apnea's).
   - Register the new files in `generate_xcodeproj.py`.
5. **UI.** Add the sign-in, sync and sign-out row and the "Saved in Vision" label. Test on Wear at Text size → Largest, through `ScrollingPage`.
6. **Pure merge logic.** Keep the merge (dedupe, last-write-wins, soft delete) as a pure function on each platform, with Kotlin unit tests. Swift still has no test target; adding one for this function is my call.
7. **Docs and disclosures** (above), plus the website handoff file.
8. **Ship.** Audit, PR, and version bump: Wear 2.2.0 and watchOS 1.2.0.

## Verification

- **Kotlin tests:** the merge function (duplicates, delete either side, conflict) and the Room migration keeping existing spots.
- **Wear, on the emulator or the Galaxy Watch:**
  1. Sign in through the phone and save a spot. It appears on spearo.pro → Dashboard → Spots within seconds.
  2. Add a spot on spearo.pro and pull. It appears on the watch.
  3. Delete on each side; each delete reaches the other side.
  4. Airplane mode: save a spot, reconnect, and it syncs.
- **watchOS simulator:** the same round trip with Sign in with Apple.
- **Signed out:** the app behaves exactly as today, with no network calls beyond the existing ones.
- **Firestore console:** documents land under `users/{uid}/spots` with `isPrivate: true`, and nothing appears in any public collection.

## Owner decisions (23 Oct)

- **Free limit:** Go syncs every spot, with no cap. The website shows them all and only blocks *adding* new spots past 3 on the website. This goes in the website handoff.
- **No pairing code:** the diver signs in once and the watch talks to Firebase directly, as Apnea does now. Apnea's `generatePairingCode()` is an empty stub and the old pairing functions are not used.

## After this (separate stages, same conversation)

- $0.99 price fixes in the docs (`HANDOFF-from-website-2026-10-23.md` item 1).
- "More Spearo apps" in Settings on both watches (item 4 of the 21 Oct handoff).
- The other 23 Oct items: 18+, safety disclaimer, privacy and legal links. These are folded into the disclosure work above where they overlap.

## Amendments (plan, 23 Oct)

These change the design above. Where they disagree, this section wins.

- **REST on both platforms, not the Firebase SDKs.**
  - The two apps stay in lockstep, with the same calls and the same code shape.
  - The Xcode project is made by a generator that has no Swift Package Manager support.
  - The Firestore SDK does not support watchOS anyway.
- **Snake_case writes, with forbidden keys.**
  - Go writes `user_id, name, latitude, longitude, is_private, is_deleted, created_at, last_modified_at, last_synced_at, cloud_id, source, sync_status`.
  - A Go doc never contains `isDeleted` or `lastModifiedAt`. Tracker's restore queries on those and hard-casts the results.
  - Reads accept every convention (camelCase, snake_case, Vision's `updatedAt`).
- **Links belong to an account, and spots stay with the account they were saved to** (final-review ruling, 23 Oct, supersedes "sign-out detaches spots"; account-switch behaviour DECIDED by the owner, Kevin, 23 Oct, superseding "copy the watch's spots into the new account"). Sign-out keeps spots and their links. Signing into a different account removes the first account's spots from the watch; they are not uploaded to the new one.
  - Go persists `linkedUid`, the uid the local cloudIds belong to (Wear: SharedPreferences "sync", backed up with the spots database; Apple Watch: UserDefaults).
  - Sign-out, planned or a session cleared by a refresh 400, clears the auth store only. Every row and cloudId stays, so signing back into the same account duplicates nothing.
  - Each spot records the account it was saved to: `ownerUid` (Room v3 column, default NULL; SwiftData defaulted optional). It is stamped with the session uid when a spot is created or deleted while signed in, and with the syncing uid whenever a sync writes, links or pushes a row. A spot saved while signed out has none. `LocalSpot` and `planSync` don't carry it; the apply step stamps it.
  - On sign-in, and at the start of every sync, a uid different from `linkedUid` runs `removeAccountSpots(newUid, includeLinked = linkedUid != null)` first (Kotlin `SpotStore.removeAccountSpots`, Room `DELETE WHERE (:includeLinked AND cloudId IS NOT NULL) OR (ownerUid IS NOT NULL AND ownerUid != :newUid)`; Swift `removeAccountSpots(newUid:includeLinked:context:)`). Every row with a cloudId leaves the watch, and so does every row owned by another uid, soft-deleted ones included. That covers a spot saved under the old account but not yet pushed (offline), and one whose PATCH reached the old account but whose link was never recorded. These spots belong to the old account; the linked ones are safe there and come back if that account signs in again. A spot that never reached the old account (saved offline under it) is lost: the owner's rule puts it with that account, and the watch is the only place it existed. Vision rows go the same way. Rows with neither a cloudId nor an ownerUid (saved while signed out) stay and upload to the new account under rule 3. Then `linkedUid` is set to the new uid and the sync runs as normal. No old-account tombstone is ever pushed.
  - On a switch, unsynced edits and signed-out deletes of the old account's spots are discarded with the rows, so a spot deleted while signed out reappears when that account signs in again.
  - If the active spot was removed, its `isActive` goes with the row, so the app falls back to "Use my location" (Wear: the view model re-runs `refresh()`, which finds no active row; Apple Watch: `ContentView` clears the override and refreshes).
  - If the removal fails, the sign-in fails ("Sync failed. Try again."). Go never syncs under the new account with the old account's links in place.
  - Signed out, deleting a never-linked spot removes it. A linked spot stays soft-deleted and pending until its own account signs in again (pushed), or is removed with the rest of that account's spots if a different account signs in. Signed out, Vision spots can be deleted from the watch; Vision still owns them, so they return when that account syncs.
  - `linkedUid == null` (first ever sign-in): linked rows are kept and every unowned spot uploads. Only rows owned by another uid are removed (a session saved, a spot made under it, then the app died before the first link).
  - Why: the owner's decision (23 Oct). A spot saved to an account belongs to that account; another account signing in on the same watch must not receive it. The earlier controller call (copy everything in, to avoid losing spots after an account is recreated or a backup restored) is superseded: the old account still holds those spots.
- **The account marker never creates `users/{uid}`.** The PATCH carries `currentDocument.exists=true`; a 404 or FAILED_PRECONDITION is skipped quietly and retried on the next sync.
- **Google sign-in asks for `openid email` only.** No `profile` scope, so no name or photo is collected.
- **Merge rule 2:** a pending local delete whose remote doc is gone is dropped locally, never pushed. A local linked to a doc that exists but cannot be decoded is left out of the merge (untouched).
- **Swift is verified by a harness, not an XCTest target.** The harness runs against the same shared fixtures as the Kotlin tests. Go still has no Swift test target.
- **A sign-in failure never falls back to a local Apple user ID.**
  - Apnea's `AuthManager` does this. It would write under a uid that is not a Firebase uid.
  - Go stays signed out and shows an error instead.

