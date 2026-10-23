# Optional Sign-in and Two-way Spot Sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A diver can optionally sign in on either watch, and Go's saved spots then sync both ways with `users/{uid}/spots`, which spearo.pro already shows.

**Architecture:** Both clients talk to Firebase over **REST**: Identity Toolkit `accounts:signInWithIdp` / `securetoken` for auth and the Firestore REST API for data. Neither client gets a Firebase SDK. That keeps the Swift and Kotlin apps structurally identical (CLAUDE.md lockstep rule), needs no `google-services.json`, and needs no Swift Package support in `generate_xcodeproj.py`. Sync is a pure function, `planSync(local, remote) -> SyncPlan`, on each platform. It is driven by one fixture file that both platforms run, so the two copies cannot drift.

**Tech Stack:**
- Wear: Kotlin, Room 2.6.1, OkHttp 4.12, Gson, `androidx.wear.phone.interactions` RemoteAuth, JUnit 4.
- watchOS: Swift, SwiftData, `AuthenticationServices`, `URLSession`, Keychain.

**Spec:** `docs/superpowers/specs/2026-10-23-go-account-spot-sync-design.md`. Task 1 amends it where reading the code changed a decision.

## Global Constraints

- **Optional sign-in.** Signed out, Go makes exactly the network calls it makes today and behaves identically.
- **One account:** Firebase project `spearo-tracker`. Web client ID `420670186721-fgqn7l067ppl3mk9mm2cq1lt5ke5i0kk.apps.googleusercontent.com` (same as Apnea, so the same Google account gives the same uid).
- **Writes go only to:**
  - `users/{uid}/spots/{cloudId}`;
  - the single field `users/{uid}.apps.go.last_seen_at`.
  - Never top-level `spots`, never any public collection.
- **Written spot docs are snake_case.** A Go doc must **never** contain the keys `isDeleted` or `lastModifiedAt`, because Tracker's restore queries on those and hard-casts the results (`Spearo-Empire/website/src/lib/web-records.ts:1-20`).
  - Exact keys: `user_id, name, latitude, longitude, is_private (true), is_deleted, created_at, last_modified_at, last_synced_at, cloud_id, source ("go"), sync_status ("synced")`.
  - Timestamps are ISO-8601 UTC strings, like the website's.
- **Reads tolerate every convention.**
  - Key casings: camelCase or snake_case.
  - Last-modified: `lastModifiedAt`, `last_modified_at` or Vision's `updatedAt`.
  - Value types: Firestore `timestampValue` or ISO `stringValue`.
  - Numbers: `doubleValue` or `integerValue`.
  - A doc missing `name`, `latitude` or `longitude` is **skipped, never filled in** (CLAUDE.md: never fabricate).
- **Never on-watch GoogleSignIn on Wear.** Wear sign-in is RemoteAuth only (fails with 12501 otherwise).
- **No limit on spot count** (owner decision 23 Oct).
- **No PII in logs:** no email, no uid, no coordinates in `Log`/`print`/`Logger`.
- **Sync never blocks or alters the verdict refresh.** If sync fails, sign-in state is reported in the sync row and nothing else.
- **Every Kotlin change has its Swift twin in the same commit.**
- **Every Wear screen change goes through `ScrollingPage`/`ScrollingList`** and is checked at Text size → Largest.
- **Swift sources are registered in `generate_xcodeproj.py` `SOURCES`, then the project is regenerated.**

## Review Focus

1. **Signing out and then into a *different* account** must not delete anything from the watch. Sign-out keeps spots and their links. Signing into a different account copies the watch's spots into it; nothing is deleted from the watch.
   - Superseded at the final review (23 Oct): sign-out used to detach. Now `linkedUid` is persisted, sign-out keeps links, and an account change runs `detachAll` before the first sync (the old account's pending deletes are purged, never pushed).
   - **Superseded 23 Oct: see the spec amendment** (spots stay with the account they were saved to; `removeAccountSpots` replaces `detachAll`).
   - Pinned by `SpotSyncServiceTest` `refresh400ThenOtherAccountKeepsSpots`, `sameAccountReSignInNoDuplicates`, `signOutKeepsSpotsAndLinks`, `syncUnderNewAccountDetachesFirst` and `detachFailureFailsSignIn`; the DAO by `accountChangeDetachesEverySpot` (Task 4).
2. **A Tracker or Vision camelCase doc in `users/{uid}/spots`** (`isDeleted`, Timestamp `createdAt`, no `last_modified_at`) must parse.
   - Pinned by the fixtures `vision-copy-camel.json` and `tracker-camel-deleted.json` (Task 3).
3. **First sign-in with a watch spot already in the account** (same name, ≤100 m) must link it, not duplicate it.
   - The case "Lagos" vs "lagos " 60 m apart. Pinned in `merge-cases.json` (Task 2).
4. **Offline delete, then sync later** must soft-delete remotely.
   - The local row stays hidden (`isDeleted`) until the push succeeds. Pinned in `merge-cases.json` case `pending-delete` (Task 2).
5. **Expired ID token (after 1 h)** must refresh silently. A revoked refresh token must put the UI back to "signed out", not a stuck "syncing".
   - Pinned by `AuthClientTest.refreshFailureSignsOut` (Task 5).

---

## Prerequisites (console work for Kevin — the builder lists these, doesn't do them)

Collect these into `docs/ACCOUNT-SYNC-SETUP.md` in Task 1. Wear sign-in won't work end to end until P1–P2 are done; Apple Watch sign-in won't until P3–P5 are.

- **P1. Google Cloud → Credentials → the Web client above:** add redirect URIs `https://wear.googleapis.com/3p_auth/com.spearotracker.spearogo` and `…/com.spearotracker.spearogo.sideload`, each also with a trailing slash.
- **P2. `wear/local.properties`:** `WEB_CLIENT_SECRET=` the same value Apnea's `local.properties` uses (never committed).
- **P3. Apple Developer:**
  - Enable **Sign in with Apple** on App ID `agency.visivo.SpearoGo.watchkitapp`.
  - Regenerate the provisioning profile.
- **P4. Firebase console → Project settings:** register an Apple app with bundle ID `agency.visivo.SpearoGo.watchkitapp`, so Firebase accepts Apple ID tokens with that audience. Sign in with Apple is already enabled as a provider, because Apnea uses it.
- **P5. Google Cloud → API key:**
  - Confirm the browser/Firebase API key used by Apnea's watch (`AIzaSyCx0Fk…`, in `Spearo Apnea Watch App/Data/Sync/FirestoreSyncClient.swift`) belongs to `spearo-tracker`.
  - Check its **application restrictions** allow calls from Go:
    - if iOS-restricted, add `agency.visivo.SpearoGo.watchkitapp`;
    - if Android-restricted, add `com.spearotracker.spearogo` plus the release and upload SHA-1s.
  - The clients send `X-Ios-Bundle-Identifier` / `X-Android-Package` + `X-Android-Cert` headers accordingly.

---

### Task 1: Amend the spec, write the setup doc

**Files:**
- Modify: `docs/superpowers/specs/2026-10-23-go-account-spot-sync-design.md`
- Create: `docs/ACCOUNT-SYNC-SETUP.md`

- [ ] **Step 1: Amend the spec** with an "Amendments (plan, 23 Oct)" section:
  - REST on both platforms instead of the Firebase SDKs (why: lockstep, no SPM in the generator, and the Firestore SDK doesn't support watchOS anyway).
  - Snake_case writes and the forbidden keys (from Global Constraints).
  - Links belong to an account: sign-out keeps them, an account change detaches (Review Focus 1). **Superseded 23 Oct: see the spec amendment** (spots stay with the account they were saved to; `removeAccountSpots` replaces `detachAll`).
  - Swift is verified by a harness against the shared fixtures, not an XCTest target.
  - A sign-in failure never falls back to a local Apple user ID. Apnea's `AuthManager` does that, and it would write under a uid that isn't a Firebase uid. Go stays signed out with an error instead.
- [ ] **Step 2: Write `docs/ACCOUNT-SYNC-SETUP.md`** with P1–P5 verbatim, plus how to check each (e.g. P1: start a Wear sign-in, and `redirect_uri_mismatch` means it's missing).
- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-10-23-go-account-spot-sync-design.md docs/ACCOUNT-SYNC-SETUP.md
git commit -m "Spec amendments from planning, and the console setup list"
```

---

### Task 2: The merge, as a pure function, pinned by shared fixtures (Kotlin + Swift)

**Files:**
- Create: `fixtures/spot-sync/merge-cases.json` (repo root, so both platforms read it)
- Create: `wear/app/src/main/java/com/spearotracker/spearogo/sync/SpotMerge.kt`
- Create: `wear/app/src/test/java/com/spearotracker/spearogo/sync/SpotMergeTest.kt`
- Create: `SpearoGo/Sync/SpotMerge.swift`
- Create: `tools/spot-sync-harness/main.swift` (runs the Swift merge against the fixtures)
- Modify: `wear/app/build.gradle.kts`: point the test resources at the root `fixtures/` dir, e.g. `sourceSets["test"].resources.srcDir("../../fixtures")`.

**Interfaces — Produces** (the same names on both platforms; Swift uses `struct`/`enum` and `Date` where Kotlin uses epoch-millis `Long`):

```kotlin
enum class SpotOrigin { GO, ACCOUNT, VISION }   // VISION = top-level /spots, read-only

data class LocalSpot(
    val id: String, val cloudId: String?, val name: String,
    val latitude: Double, val longitude: Double,
    val createdAt: Long, val lastModifiedAt: Long,
    val isDeleted: Boolean, val pendingSync: Boolean, val origin: SpotOrigin)

data class RemoteSpot(
    val docId: String, val origin: SpotOrigin,          // ACCOUNT or VISION
    val name: String, val latitude: Double, val longitude: Double,
    val createdAt: Long?, val lastModifiedAt: Long?, val isDeleted: Boolean,
    val camelCase: Boolean)                             // doc's own key convention

data class SyncPlan(
    val upsertLocal: List<LocalSpot>,       // write these rows locally (pendingSync=false unless noted)
    val deleteLocal: List<String>,          // hard-delete these local ids
    val pushUpsert: List<LocalSpot>,        // PATCH users/{uid}/spots/{cloudId}
    val pushDelete: List<RemoteSpot>)       // soft-delete this remote doc, in its own casing

fun planSync(local: List<LocalSpot>, remote: List<RemoteSpot>, newId: () -> String): SyncPlan
fun isSameSpot(a: LocalSpot, b: RemoteSpot): Boolean   // case-insensitive trimmed name AND ≤100 m (haversine)
```

**The rules `planSync` implements.** They aren't fully determined by the signature, so here they are exactly. `remote` is complete: both collections, all pages.

1. **Local with `cloudId` == a remote `docId`:**
   - If `local.isDeleted`, add the remote to `pushDelete`, unless it is VISION. VISION locals can't be deleted (the UI prevents it); if one is anyway, drop it from `deleteLocal`.
   - Else if the remote is deleted:
     - if `local.pendingSync` and `local.lastModifiedAt > (remote.lastModifiedAt ?: 0)`, add it to `pushUpsert`;
     - otherwise add it to `deleteLocal`.
   - Else if `local.pendingSync` and `local.lastModifiedAt >= (remote.lastModifiedAt ?: 0)`, add it to `pushUpsert`.
   - Else, `upsertLocal` gets local's id with remote's name, lat, lon and lastModifiedAt.
2. **Local with a `cloudId` that no remote has:** `pendingSync` → `pushUpsert`; otherwise `deleteLocal` (it was hard-deleted elsewhere).
3. **Local without a `cloudId`:**
   - If `isDeleted`, add it to `deleteLocal`.
   - Else if `isSameSpot` matches a non-deleted remote that no other local is linked to, `upsertLocal` gets it linked (`cloudId = docId`, `origin = remote.origin`, `pendingSync = false`).
   - Else `pushUpsert` with `cloudId = id`, so the doc id is the local UUID.
4. **Remote not linked or matched by any local, and not deleted:** `upsertLocal` gets a new row with `id = newId()`, `cloudId = docId`, `origin = remote.origin`, `pendingSync = false`, `isDeleted = false`.
5. **`isActive` is not part of `LocalSpot`.** The caller preserves it; `planSync` never sees it.

- [ ] **Step 1: Write `fixtures/spot-sync/merge-cases.json`.** It is an array of `{ "name", "local": [...], "remote": [...], "expect": { "upsertLocal": [ids or "new:<docId>"], "deleteLocal": [...], "pushUpsert": [...], "pushDelete": [docIds] } }`. Required cases, one per rule branch, plus:
  - `first-signin-dedupe`: local "Lagos" (37.1020, -8.6730), remote "lagos " 60 m away → linked, nothing pushed.
  - `first-signin-far`: same name, 300 m away → both kept, the local is pushed.
  - `pending-delete`: local isDeleted with a cloudId → pushDelete.
  - `vision-readonly`: a VISION remote appears locally and is never pushed.
  - `remote-deleted-local-newer`: the local edit wins and is pushed.
  - `remote-deleted-local-clean`: the local is removed.
  - `hard-deleted-elsewhere`: a clean local with an unknown cloudId is removed.
  - `two-locals-one-remote`: only the first matching local links; the second is pushed.
- [ ] **Step 2: Write `SpotMergeTest.kt`.** It loads every case with Gson from the classpath resource `spot-sync/merge-cases.json` and asserts each `expect` list as a set, with the case name in the failure message. `newId` returns `"new:" + docId` in a counter-free way: the test passes a lambda reading a queue built from the expected `new:` entries, or compares ignoring new ids. Pick one and say so in a comment.
- [ ] **Step 3: Run the test and confirm it fails.** `cd wear && ./gradlew :app:testDebugUnitTest --tests '*SpotMergeTest*'` should FAIL to compile (`planSync` undefined).
- [ ] **Step 4: Implement `SpotMerge.kt`** per the rules above. Haversine with an Earth radius of 6,371,000 m and a threshold of `<= 100.0`.
- [ ] **Step 5: Run the test again.** Expected: PASS, every case.
- [ ] **Step 6: Implement `SpotMerge.swift`,** a line-for-line twin with the same names and rules.
- [ ] **Step 7: Write `tools/spot-sync-harness/main.swift`.** It decodes the fixture file (path in `argv[1]`), runs `planSync`, prints `PASS <name>` or `FAIL <name>: <diff>` per case, and exits non-zero on any failure.
- [ ] **Step 8: Run the harness.**

```bash
swiftc -o /tmp/spot-harness SpearoGo/Sync/SpotMerge.swift tools/spot-sync-harness/main.swift && /tmp/spot-harness fixtures/spot-sync/merge-cases.json
```

Expected: every line `PASS`, exit 0. (Use the session scratchpad instead of `/tmp` when running as an agent.)
- [ ] **Step 9: Register `Sync/SpotMerge.swift`** in `generate_xcodeproj.py` `SOURCES` (a new `# Sync` block), then run `python3 generate_xcodeproj.py`.
- [ ] **Step 10: Commit**

```bash
git add fixtures wear/app/build.gradle.kts wear/app/src/main/java/com/spearotracker/spearogo/sync wear/app/src/test/java/com/spearotracker/spearogo/sync SpearoGo/Sync tools/spot-sync-harness generate_xcodeproj.py SpearoGo.xcodeproj
git commit -m "Spot sync merge as a pure function, one fixture set for both apps"
```

---

### Task 3: Firestore document codec (Kotlin + Swift)

**Files:**
- Create: `fixtures/spot-sync/docs/` with these files, each a raw Firestore REST `Document` JSON:
  - `website-snake.json`
  - `vision-copy-camel.json` (users/ copy, with `isDeleted`, `lastModifiedAt` Timestamp)
  - `vision-top.json` (with `userId`, `updatedAt`, no `lastModifiedAt`)
  - `tracker-camel-deleted.json`
  - `go-written.json`
  - `missing-latitude.json`
  - `integer-coords.json`
- Create: `fixtures/spot-sync/codec-cases.json` (`[{ "file", "origin", "expect": RemoteSpot | null }]`)
- Create: `wear/.../sync/SpotCodec.kt`, `wear/.../test/.../sync/SpotCodecTest.kt`
- Create: `SpearoGo/Sync/SpotCodec.swift`; extend `tools/spot-sync-harness/main.swift` with a `codec` mode.

**Interfaces — Produces:**

```kotlin
object SpotCodec {
    /** null when name, latitude or longitude is absent — never defaulted. */
    fun decode(document: JsonObject, origin: SpotOrigin): RemoteSpot?
    /** Full `fields` map for PATCH users/{uid}/spots/{cloudId}; snake_case only. */
    fun encodeUpsert(spot: LocalSpot, uid: String, nowIso: String): JsonObject
    /** `fields` + the updateMask field paths for a soft delete, in the doc's own casing:
     *  camel → isDeleted=true, lastModifiedAt=<timestamp>; snake → is_deleted=true, last_modified_at=<iso>. */
    fun encodeSoftDelete(remote: RemoteSpot, nowIso: String): Pair<JsonObject, List<String>>
}
```

- **`docId`** is the last path segment of `document.name`.
- **`camelCase`** is true if any key matches `isDeleted|lastModifiedAt|createdAt|userId`.
- **Last-modified precedence:** `last_modified_at` > `lastModifiedAt` > `updatedAt` > null.

- [ ] **Step 1:** Write the fixture docs and `codec-cases.json`. Each `expect` is the exact `RemoteSpot`, or `null` for `missing-latitude.json`.
- [ ] **Step 2:** Write `SpotCodecTest`:
  - Table-driven over `codec-cases.json`.
  - `encodeUpsertHasNoTrackerKeys`: the encoded keys contain neither `isDeleted` nor `lastModifiedAt`, and exactly equal the Global Constraints key list.
  - `encodeSoftDeleteKeepsCasing`: camel → `["isDeleted","lastModifiedAt"]`; snake → `["is_deleted","last_modified_at"]`.
- [ ] **Step 3:** Run `./gradlew :app:testDebugUnitTest --tests '*SpotCodecTest*'`. Expected: FAIL (undefined).
- [ ] **Step 4:** Implement `SpotCodec.kt` with Gson `JsonObject`. Timestamps parse with `java.time.Instant.parse` and format with `DateTimeFormatter.ISO_INSTANT`.
- [ ] **Step 5:** Run the tests. Expected: PASS.
- [ ] **Step 6:** Implement `SpotCodec.swift` (`[String: Any]` from `JSONSerialization`, `ISO8601DateFormatter` with `.withFractionalSeconds` tolerated). Add harness `codec` mode, then run: `swiftc -o $T <the explicit file list in the header comment of tools/spot-sync-harness/main.swift> && $T codec fixtures/spot-sync/codec-cases.json`. Expected: all PASS.
- [ ] **Step 7:** Register the Swift file in `generate_xcodeproj.py`, regenerate, and commit: `"Read every spot convention, write only the website's"`.

---

### Task 4: Local model v2 and sign-out detach (Kotlin + Swift)

**Files:**
- Modify: `wear/.../models/SavedLocation.kt`, `services/LocationDao.kt`, `services/AppDatabase.kt` (version 2, `exportSchema = true`), `di/AppModule.kt` (`.addMigrations(MIGRATION_1_2)`)
- Create: `wear/.../services/Migrations.kt`, `wear/app/src/androidTest/.../MigrationTest.kt`, `wear/app/schemas/` (Room schema export; add the `room.schemaLocation` ksp arg plus `androidTestImplementation("androidx.room:room-testing:2.6.1")`, `androidx.test.ext:junit`, `androidx.test:runner`)
- Modify: `SpearoGo/Models/SavedLocation.swift`
- Create: `wear/.../sync/SpotMapping.kt`, `SpearoGo/Sync/SpotMapping.swift` (SavedLocation ⇄ LocalSpot)

**New columns and properties** (all with defaults, so existing rows survive):
- `cloudId: String? = null`
- `lastModifiedAt: Long = createdAt` (migration: `UPDATE saved_locations SET lastModifiedAt = createdAt`)
- `isDeleted: Boolean = false`
- `pendingSync: Boolean = false`
- `origin: String = "GO"`

Swift uses the same names, with `lastModifiedAt: Date`.

**Interfaces — Produces (DAO):**
- `fun getAllLocations(): Flow<List<SavedLocation>>`: now `WHERE isDeleted = 0`.
- `suspend fun getAllIncludingDeleted(): List<SavedLocation>`
- `suspend fun markDeleted(id: String, now: Long)`: sets `isDeleted = 1, pendingSync = 1, lastModifiedAt = now, isActive = 0`.
- `suspend fun hardDelete(ids: List<String>)`
- `suspend fun upsertAll(rows: List<SavedLocation>)`
- `suspend fun detachAll()`: `DELETE WHERE isDeleted = 1`, then `UPDATE SET cloudId = NULL, pendingSync = 0`. **Superseded 23 Oct: see the spec amendment** (spots stay with the account they were saved to; `removeAccountSpots` replaces `detachAll`).

**Interfaces — Produces (mapping):**
- `fun SavedLocation.toLocalSpot(): LocalSpot`
- `fun LocalSpot.toSavedLocation(isActive: Boolean): SavedLocation`

- [ ] **Step 1:** Write `MigrationTest.migrate1To2KeepsSpots`: create a v1 DB with the row `("a", "Lagos", 37.10, -8.67, 1000, isActive=1)`, migrate, and assert the row is intact with `lastModifiedAt == 1000`, `cloudId == null`, `isDeleted == false`, `isActive == true`. Also write `signOutDetachesEverySpot`: two rows, one with a cloudId and pending, one deleted; `detachAll()` → one row left, `cloudId == null`, `pendingSync == false`. (Final review: renamed `accountChangeDetachesEverySpot`; detachAll now runs on an account change, not on sign-out. See Review Focus 1.) **Superseded 23 Oct: see the spec amendment** (spots stay with the account they were saved to; `removeAccountSpots` replaces `detachAll`). The test is now `accountChangeRemovesOldAccountSpots`.
- [ ] **Step 2:** Run `./gradlew :app:connectedDebugAndroidTest` on the emulator, with `targetSdk = 34` set locally and never committed (HANDOFF "things that will bite you"). Expected: FAIL.
- [ ] **Step 3:** Implement the entity, migration, DAO and mapping. `AppViewModel.deleteLocation` now calls `markDeleted` instead of `delete`.
- [ ] **Step 4:** Run the tests. Expected: PASS. Revert the local targetSdk.
- [ ] **Step 5:** Swift:
  - Add the properties with default values (SwiftData lightweight migration handles added defaulted properties automatically).
  - `LocationsView.delete(at:)` becomes a soft delete (`isDeleted = true, pendingSync = true, lastModifiedAt = .now, isActive = false`).
  - Every `@Query`/`FetchDescriptor` that lists spots filters `!isDeleted`.
  - Add `func detachAll(context: ModelContext)` in `SpotMapping.swift`. **Superseded 23 Oct: see the spec amendment** (spots stay with the account they were saved to; `removeAccountSpots` replaces `detachAll`).
- [ ] **Step 6:** Verify on the watchOS simulator: install the **current main build**, save "Lagos", install this build over it, and confirm "Lagos" is still listed and still active. Build commands are in `CLAUDE.md`.
- [ ] **Step 7:** Wear: also run `./gradlew :app:testDebugUnitTest` (all suites green). Register the Swift files, regenerate, and commit: `"Spots remember their account twin; deletes wait for sync"`.

---

### Task 5: Auth over REST (Kotlin + Swift)

**Files:**
- Create: `wear/.../sync/AuthClient.kt`, `wear/.../sync/TokenStore.kt`, `wear/.../sync/RemoteAuthSignIn.kt`, test `AuthClientTest.kt`
- Create: `SpearoGo/Sync/AuthClient.swift`, `SpearoGo/Sync/TokenStore.swift` (Keychain), `SpearoGo/Sync/AppleSignIn.swift`
- Modify:
  - `wear/app/build.gradle.kts`: `buildConfigField WEB_CLIENT_SECRET` from `local.properties`, as Apnea does; `buildFeatures.buildConfig = true`; `testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`.
  - `wear/app/src/main/AndroidManifest.xml`: `android:allowBackup="false"`, or a `dataExtractionRules` that excludes the `auth` prefs.
  - `SpearoGo/SpearoGo.entitlements`: add `com.apple.developer.applesignin = [Default]`.
- Create: `wear/.../utils/Constants.kt` `Firebase` object and `SpearoGo/Utils/Constants.swift` `enum Firebase`: API key, project id `spearo-tracker`, web client id.

**Interfaces — Produces:**

```kotlin
data class Session(val uid: String, val idToken: String, val refreshToken: String, val expiresAt: Long, val email: String?)
sealed interface SignInResult {
    data class Success(val session: Session) : SignInResult
    data object AccountExistsWithDifferentMethod : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}
class AuthClient(http: OkHttpClient, baseIdentity: String, baseSecureToken: String, store: TokenStore, clock: () -> Long) {
    suspend fun signInWithIdp(providerId: String /* "google.com" | "apple.com" */, idToken: String, nonce: String?): SignInResult
    /** Valid ID token, refreshing if expiring within 5 min; null and store cleared if refresh is rejected. */
    suspend fun currentIdToken(): String?
    fun session(): Session?
    fun signOut()
}
```

- **Sign-in endpoint:** `POST {baseIdentity}/v1/accounts:signInWithIdp?key=API_KEY` with body `{ postBody: "id_token=…&providerId=…[&nonce=…]", requestUri: "http://localhost", returnSecureToken: true, returnIdpCredential: true }`.
  - `needConfirmation == true`, or an error message containing `FEDERATED_USER_ID_ALREADY_LINKED` or `EMAIL_EXISTS`, maps to `AccountExistsWithDifferentMethod`.
- **Refresh endpoint:** `POST {baseSecureToken}/v1/token?key=API_KEY`, `grant_type=refresh_token`.
  - HTTP 400 → clear the store and return null.
  - A network error → return null **without** clearing.
- The base URLs are constructor parameters, so tests point them at MockWebServer.

- [ ] **Step 1:** Write `AuthClientTest` against MockWebServer:
  - `successStoresSession`
  - `needConfirmationIsAccountExists`
  - `emailExistsIsAccountExists`
  - `refreshesWhenExpiring` (clock at expiresAt − 1 min → one refresh call, new token returned)
  - `refreshFailureSignsOut` (400 → null, `session() == null`)
  - `networkErrorKeepsSession` (dispatcher throws → null, `session() != null`)
- [ ] **Step 2:** Run it. Expected: FAIL.
- [ ] **Step 3:** Implement `AuthClient.kt` and `TokenStore.kt`. The store is SharedPreferences `"auth"`, `MODE_PRIVATE`, behind an interface so tests use an in-memory one.
- [ ] **Step 4:** Run it. Expected: PASS.
- [ ] **Step 5:** Implement `RemoteAuthSignIn.kt`:
  - Port Apnea's `signInWithRemoteAuth` + `exchangeCodeForTokens` (`Spearo-apnea/Spearo Apnea Watch/app/src/main/kotlin/com/spearotracker/apnea/data/sync/FirestoreSyncClient.kt:402-590`).
  - Redirect URI: `https://wear.googleapis.com/3p_auth/${context.packageName}`, so the sideload build uses its own.
  - Drop `prompt=consent` and `access_type=offline`; Firebase issues its own refresh token.
  - The Google `id_token` goes to `AuthClient.signInWithIdp("google.com", …)`.
  - Map `RemoteAuthClient.ERROR_PHONE_UNAVAILABLE` to `Failed("Keep your phone nearby and try again")`.
  - No PII logging.
  - Add `implementation("androidx.wear:wear-phone-interactions:1.0.1")`.
- [ ] **Step 6:** Swift:
  - `AuthClient.swift` is a twin of the Kotlin one (same results, same 5-min refresh window, same 400-vs-network rule).
  - `TokenStore.swift` uses Keychain, `kSecClassGenericPassword`, service `agency.visivo.SpearoGo.auth`.
  - `AppleSignIn.swift` wraps `ASAuthorizationController` with a SHA-256 nonce (copy the nonce helpers from Apnea's `AuthManager.swift:178-190`) and returns `SignInResult`.
  - **No fallback to the Apple user ID** (Task 1 amendment).
- [ ] **Step 7:** Build both:
  - `cd wear && ./gradlew :app:assembleDebug`
  - the watchOS target build from `CLAUDE.md`

  Both must succeed. Register the Swift files, regenerate, and commit: `"Sign in over REST: Google via the phone on Wear, Apple on the watch"`.

---

### Task 6: SpotSyncService (Kotlin + Swift)

**Files:**
- Create: `wear/.../sync/FirestoreClient.kt`, `wear/.../sync/SpotSyncService.kt`, test `SpotSyncServiceTest.kt`
- Create: `SpearoGo/Sync/FirestoreClient.swift`, `SpearoGo/Sync/SpotSyncService.swift`
- Modify: `wear/.../di/AppModule.kt` (provide `OkHttpClient`, `AuthClient`, `SpotSyncService` singletons)

**Interfaces — Consumes:** `planSync`, `SpotCodec`, `AuthClient.currentIdToken()`, the DAO from Task 4, and the mapping.

**Interfaces — Produces:**

```kotlin
sealed interface SyncState { data object SignedOut : SyncState; data object Idle : SyncState; data object Syncing : SyncState; data class Synced(val at: Long) : SyncState; data class Failed(val message: String) : SyncState }
class SpotSyncService(...) {
    val state: StateFlow<SyncState>
    suspend fun sync()                      // no-op when signed out; one at a time (Mutex); never throws
    suspend fun onSignedIn(session: Session) // writes marker, then sync()
    suspend fun signOut()                   // AuthClient.signOut(); links kept (final review); state = SignedOut
}
class FirestoreClient(http: OkHttpClient, baseUrl: String, projectId: String) {
    suspend fun listUserSpots(uid: String, token: String): List<JsonObject>   // follows nextPageToken, pageSize=300
    suspend fun queryVisionSpots(uid: String, token: String): List<JsonObject> // :runQuery, spots where userId == uid
    suspend fun patch(path: String, fields: JsonObject, mask: List<String>?, token: String)
}
```

- **`sync()` order:**
  1. Get a token; if null and signed out → `SignedOut`.
  2. Fetch both collections. If either fetch fails → `Failed`, apply nothing (a partial `remote` would make rule 2 delete spots).
  3. `planSync`.
  4. Pushes first: `pushUpsert` then `pushDelete`.
  5. Then apply locally:
     - `upsertLocal`, keeping each existing row's `isActive`;
     - `deleteLocal`;
     - successful pushes: set `pendingSync = false`, and hard-delete rows whose soft delete pushed.
  6. A push that fails leaves its row pending, and the state becomes `Failed("Couldn't upload N spots")`.
- **Marker:** `PATCH users/{uid}?updateMask.fieldPaths=apps.go.last_seen_at&currentDocument.exists=true`, at most once per 24 h (last-written time kept in prefs/UserDefaults). Final review: it never creates `users/{uid}`; a 404 / FAILED_PRECONDITION is skipped and retried next sync.

- [ ] **Step 1:** Write `SpotSyncServiceTest` with MockWebServer and an in-memory DAO fake:
  - `fetchFailureAppliesNothing`: the list call returns 500 → the DAO is untouched and the state is `Failed`.
  - `pushesThenPulls`: one pending local plus one website doc → one PATCH whose body has `source: "go"` and no `isDeleted` key; the local gains the website spot.
  - `failedPushStaysPending`
  - `activeSpotSurvivesSync`
  - `markerOncePerDay`: two syncs → one marker PATCH.
- [ ] **Step 2:** Run it. Expected: FAIL.
- [ ] **Step 3:** Implement `FirestoreClient.kt` and `SpotSyncService.kt`. Base URL `https://firestore.googleapis.com/v1/projects/spearo-tracker/databases/(default)/documents`, `Authorization: Bearer`, plus the P5 restriction headers.
- [ ] **Step 4:** Run it. Expected: PASS, and the full `testDebugUnitTest` green.
- [ ] **Step 5:** Swift twins. `SpotSyncService` is an `actor`, applying through a `ModelContext` from the app's container; `state` is published to the UI on the main actor.
- [ ] **Step 6:** Build both, register the Swift files, regenerate, and commit: `"Sync spots with the Spearo account"`.

---

### Task 7: UI and triggers (Kotlin + Swift)

**Files:**
- Modify: `wear/.../ui/pages/SpotsPage.kt`, `wear/.../ui/AppViewModel.kt`, `wear/.../MainActivity.kt`
- Modify: `SpearoGo/Views/LocationsView.swift`, `SpearoGo/ContentView.swift` (scenePhase)

**Copy (exact, both platforms):**
- Signed out: row "Sync with spearo.pro", subtitle "Sign in to keep your spots in your Spearo account".
  - Wear: "Sign in with Google".
  - Apple: the system `SignInWithAppleButton`.
- While signing in (Wear): "Check your phone to sign in".
- Account exists: "You already have a Spearo account with this email. Sign in the way you did before, or link methods at spearo.pro."
- Signed in: "Synced · just now" / "Synced · N min ago" / "Syncing…" / the failure message. Tapping the row runs Sync now.
- Then a "Sign out" row, subtitle "Your spots stay on this watch".
- VISION spots: a "Saved in Vision" caption on the row. Long-press delete is disabled for them.

**Triggers:**
- `sync()` on app foreground (Wear `MainActivity.onResume` → `viewModel.syncSpots()`; watchOS `scenePhase == .active`).
- After `saveAndActivate`, `addLocation` and `deleteLocation`: Kotlin uses `markDeleted`; Swift uses the soft delete. Each of these sets `pendingSync = true` and `lastModifiedAt = now` before calling `sync()`.

- [ ] **Step 1:** Wear UI: the rows go inside the existing `ScrollingList`, above "About".
- [ ] **Step 2:** Run the emulator at Text size → Largest (font scale 1.24) and at 1.0, signed out and signed in. Nothing may clip under the clock or the arcs. Screenshot both into the PR.
- [ ] **Step 3:** Swift UI in `LocationsView`, the same rows. Run it in the Ultra simulator at the largest Dynamic Type allowed.
- [ ] **Step 4:** Run the end-to-end check (needs P1–P5):
  1. Wear sideload build, signed in. Save a spot, then open spearo.pro → Dashboard → Spots: it shows within seconds.
  2. Add a spot on spearo.pro, then foreground the watch: it appears.
  3. Delete on the watch: it disappears from the website. Delete on the website: it disappears from the watch.
  4. Airplane mode: save a spot, reconnect, foreground: it syncs.
  5. Sign out: the spots stay. Sign in again: no duplicates.
  6. Repeat 1–5 on the watchOS simulator with Sign in with Apple.
  7. In the Firestore console, confirm Go docs carry `source: "go"`, `is_private: true`, and no camelCase keys.
- [ ] **Step 5:** Signed out, run a Wear network capture (`adb logcat` OkHttp or a proxy): only Open-Meteo and `tidesGo` hosts appear.
- [ ] **Step 6:** Commit: `"Sign-in and sync rows on the Spots screen"`.

---

### Task 8: Disclosures, docs, handoff, version

**Files:**
- Modify: `wear/.../ui/pages/InfoPage.kt` and `SpearoGo/Views/PrivacyPolicyView.swift`.
  - Replace the "Saved Locations" and "No Tracking" text with exactly:
    - **"Saved Locations":** "Dive spots are stored on your watch. If you sign in, they are also kept in your Spearo account so you can see them at spearo.pro. They are private and never shared."
    - **"No Tracking":** "No ads, no analytics. An account is optional; if you sign in, your email and saved spots are linked to it."
  - Also fix the stale "Tides and solunar calculated on-device" line to "Solunar calculated on-device. Tides from NOAA or WorldTides via Spearo's tide service." The current text is a false claim, so it is in scope under the never-fabricate rule.
- Modify: `SpearoGo/PrivacyInfo.xcprivacy`: add collected types EmailAddress and PreciseLocation (spot coordinates), linked to the user, purpose App Functionality, not tracking.
- Modify: `docs/APP_STORE_METADATA.md`, `docs/GOOGLE_PLAY_METADATA.md` (privacy label / Data safety sections, kept consistent with each other); `CLAUDE.md` ("No account, no subscription" → "No subscription. Account optional, for spot sync."); `docs/HANDOFF.md` (new "Start here" state); `docs/BACKLOG.md`.
- Create: `~/Documents/Spearo-Empire/docs/HANDOFF-from-go-2026-10-23.md`. This is the only file written outside this repo; it's directions only. It covers:
  - Go writes `users/{uid}/spots` with `source: "go"` and `apps.go.last_seen_at`.
  - Take `go` out of `NO_ACCOUNT_APPS` and count `apps.go.last_seen_at` as connected.
  - Go spots have no `difficulty`/`access_type`/`description`, so the Spots page must render them without those.
  - Free limit: show every synced spot; block only adding past 3 on the web (owner, 23 Oct).
  - Add one rules test for an owner `users/{uid}/spots` write with `source: "go"` and a denied cross-user write.
  - ~~A Go sign-in may create `users/{uid}` containing only `apps` if the diver is new.~~ No longer true (final review): the marker only updates an existing doc.
- Modify versions: Wear `versionCode 17`, `versionName "2.2.0"`; watchOS `1.2.0 (4)` in `generate_xcodeproj.py` and `Constants.App.version`, then regenerate.

- [ ] **Step 1:** Make the text and doc edits above. The Play Data safety and App Store label must list the same data types.
- [ ] **Step 2:** Run all Kotlin tests, both harness modes and both release builds:
  - `./gradlew :app:testDebugUnitTest :app:assembleRelease`
  - the watchOS target build
  - the harness
- [ ] **Step 3:** Commit: `"Say what sign-in stores, everywhere it's described"`.
- [ ] **Step 4:** Finish with `button-up`: audit, push the branch, and open the PR against `main`. Merge only on Kevin's yes. Store submission is a separate step.
