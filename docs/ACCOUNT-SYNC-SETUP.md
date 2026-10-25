# Account sync: console setup

This is the console work for optional sign-in and spot sync in Spearo Go. The builder does not do it. Kevin does.

- Wear sign-in will not work end to end until P1 and P2 are done.
- Apple Watch sign-in will not work until P3, P4 and P5 are done.

Firebase project: `spearo-tracker`.
Web client ID: `420670186721-fgqn7l067ppl3mk9mm2cq1lt5ke5i0kk.apps.googleusercontent.com` (same as Apnea, so the same Google account gives the same uid).

## P1. Google Cloud: redirect URIs — DONE 25 Oct

All four added to the Web client, after Apnea's three, which are unchanged. Confirmed after a reload. Google says changes can take from 5 minutes to a few hours to apply.


Google Cloud, Credentials, the Web client above. Add redirect URIs `https://wear.googleapis.com/3p_auth/com.spearotracker.spearogo` and `https://wear.googleapis.com/3p_auth/com.spearotracker.spearogo.sideload`, each also with a trailing slash.

**How to check:** start a Wear sign-in. If Google shows `redirect_uri_mismatch`, a URI is missing. Check all four (two ids, with and without the slash).

## P2. `wear/local.properties` — DONE 25 Oct

Copied from Apnea's `Spearo Apnea Watch/local.properties`, on Kevin's Mac only. Another machine needs the same step.


Set `WEB_CLIENT_SECRET=` to the same value Apnea's `local.properties` uses. Never commit it.

**How to check:** rebuild Wear and start a sign-in. A missing or wrong secret fails at the token exchange with `invalid_client` or `invalid_request`. `git status` must not list `local.properties`.

## P3. Apple Developer — DONE 25 Oct

Sign In with Apple ticked on `agency.visivo.SpearoGo.watchkitapp` ("Enable as a primary App ID"). Signing is automatic, so a device build with `-allowProvisioningUpdates` regenerated the team profile; its entitlements include `com.apple.developer.applesignin` (created 2026-10-25).


- Enable **Sign in with Apple** on App ID `agency.visivo.SpearoGo.watchkitapp`.
- Regenerate the provisioning profile.

**How to check:** in the Developer portal, open the App ID and confirm the capability is ticked. In Xcode, a build with the entitlement fails with a provisioning error if the profile is stale.

## P4. Firebase console: Project settings — DONE 25 Oct

Registered as "Spearo Go Watch", app ID `1:420670186721:ios:f9835d565ff4eac2c683a0` (`firebase apps:create`). Its config carries the same iOS key Go uses (`AIzaSyCx0Fk...`).


Register an Apple app with bundle ID `agency.visivo.SpearoGo.watchkitapp`, so Firebase accepts Apple ID tokens with that audience. Sign in with Apple is already enabled as a provider, because Apnea uses it.

**How to check:** run `firebase apps:list --project spearo-tracker` (read-only). An iOS app with that bundle ID must be listed. If it is not, Apple sign-in fails at the token exchange with an audience error.

## P5. Google Cloud: API key — DONE 25 Oct (no change needed)

Both keys were checked in Google Cloud Credentials. "iOS key (auto created by Firebase)" (`AIzaSyCx0Fk...`) and "Android key (auto created by Firebase)" (`AIzaSyCCzSR...`, confirmed by Show key) both have **Application restrictions: None**. Their API lists include Cloud Firestore, Identity Toolkit and Token Service, so neither watch will get a `*_APP_BLOCKED` 403. Google recommends adding app restrictions. That isn't done, because the same keys serve Apnea and Tracker, and a wrong restriction would break them. It's logged in BACKLOG.


- Confirm the browser/Firebase API key used by Apnea's watch (`AIzaSyCx0Fk...`, in `Spearo Apnea Watch App/Data/Sync/FirestoreSyncClient.swift`) belongs to `spearo-tracker`.
- Check its **application restrictions** allow calls from Go:
  - if iOS-restricted, add `agency.visivo.SpearoGo.watchkitapp`;
  - if Android-restricted, add `com.spearotracker.spearogo` plus the release and upload SHA-1s.
- The clients send `X-Ios-Bundle-Identifier` / `X-Android-Package` + `X-Android-Cert` headers accordingly.

**Result of the project check (23 Oct, read-only, `firebase apps:sdkconfig`):** VERIFIED. Key `AIzaSyCx0FkVdpNN7jXgnJztnb622wPbw7GMBL0` is the key in the config of three iOS apps in `spearo-tracker`: Spearo Apnea Watch, Spearo Apnea Phone and Spearo Tracker.

**Not verified:** the key's application restrictions. The Firebase CLI cannot read them. Open Google Cloud, APIs and Services, Credentials, and look at this key.

- Note that the Apnea Watch Android app uses a different key (`AIzaSyCCzSR...`). Go's Wear client must not assume the iOS key works for Android calls. If the iOS key is iOS-restricted, Wear needs the Android key or a second package entry.

**What Go uses (Task 5):** each platform uses its own key, in `Constants.Firebase`.
- Wear: the Android key `AIzaSyCCzSR...` (from Apnea's `google-services.json`, project `spearo-tracker`). It sends `X-Android-Package` and `X-Android-Cert` (the SHA-1 of the running APK's signing cert). If that key is Android-restricted, add `com.spearotracker.spearogo` with the Play app-signing and upload SHA-1s, and `com.spearotracker.spearogo.sideload` with the local release and debug SHA-1s.
- Apple Watch: the iOS key `AIzaSyCx0Fk...`. It sends `X-Ios-Bundle-Identifier: agency.visivo.SpearoGo.watchkitapp`.

**How to check:** the first REST call from Go returns HTTP 403 `API_KEY_IOS_APP_BLOCKED` or `API_KEY_ANDROID_APP_BLOCKED` when a restriction blocks it. HTTP 400 `API key not valid` means the key is wrong for the project.
