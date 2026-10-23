# Handoff from the website session (23 Oct 2026): price, age, legal and store disclosures

Written by the Spearo website session (`Spearo-Empire`). **Nothing else in this repo was changed**;
these are directions for a Go session. Work one item at a time and confirm each with the owner.

## 1. Price is $0.99 (owner, 23 Oct 2026)
The live App Store and Google Play listings charge **$0.99** (checked by the website session on
16 Oct). These repo docs still say **$2.99**. Update them to $0.99:
- `README.md:202`
- `wear/README.md:153`
- `docs/APP_STORE_METADATA.md:52`
- `docs/GOOGLE_PLAY_METADATA.md:51` and `:163`
- `docs/SPRINT_PLAN.md:6`
- `docs/SESSION_SUMMARY.md:465` (a history note: add a line saying it changed, rather than rewriting it)

## 2. Adults only, 18+ (owner, 23 Oct 2026)
Every Spearo product is now **18+**. The new Terms, EULA and Privacy Policy say so (website draft PR
#61, published once the owner's lawyer signs off). Go has no age check. Decide with the owner:
- raising the store age rating (App Store age rating / Play content rating questionnaire), and
- whether Go needs an in-app age confirmation. It has no account, so the store rating may be enough.

## 3. A safety disclaimer for the verdict
I found no disclaimer text in the app. The new Terms (§7.2) say: "A GO verdict does not mean it is
safe to dive." Add a short line where the verdict is first shown (onboarding or an info screen),
on both watchOS and Wear OS, for example: "Forecast only. A GO verdict doesn't mean it's safe. Check
conditions yourself and never dive alone."

## 4. Store privacy disclosures don't match what Go does
What Go actually does (checked 23 Oct):
- Sends spot coordinates to Open-Meteo, and to the `tidesGo` Cloud Function, which looks them up with NOAA or WorldTides.
- The server keeps the coordinate, rounded to about 1 km, in `tides_cache` for 24 hours.
- It keeps a hashed IP in `tides_rate`, with a TTL, for rate limiting.
- The Android manifest asks for **coarse** location only.

Problems in the drafts:
- `docs/GOOGLE_PLAY_METADATA.md:72-95` says precise location, "Not shared", "not stored remotely".
- `:171-190` says Shared: Yes, kept 24h.
- The hashed IP isn't mentioned anywhere.

Make the Play Data safety form and the App Store privacy label say the same thing, and match it to
the website Privacy Policy, which now says:
> "To get tide predictions, it sends your spot's coordinates to our server, which looks them up with
> NOAA or WorldTides. Our server keeps the coordinates, rounded to about 1 km, for up to 24 hours so
> it can answer repeat requests, and keeps a one-way hash of your IP address for a short time to
> limit abuse. Neither is linked to you."

If the in-app privacy text (`PrivacyPolicyView.swift`) says anything different, align it too.

## 5. Legal links
Once PR #61 is merged, the canonical pages are https://spearo.pro/terms, /privacy, /eula and
/data-deletion. Point the store listings and any in-app links there.
