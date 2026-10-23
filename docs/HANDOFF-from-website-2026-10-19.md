# Handoff from the website session (19 Oct 2026): optional Spearo sign-in for Go

Written by the Spearo website session (`Spearo-Empire`). **Nothing in this repo was changed**;
these are directions for a Go session. This changes a design rule in `CLAUDE.md`
("No account, no subscription"), so start with brainstorming and a short spec for the owner.

## Owner decision (19 Oct 2026)
Spearo Go should be able to **sign in to the shared Spearo account** (Firebase project
`spearo-tracker`, the same Auth as Tracker, Vision and Apnea) so the website (spearo.pro) can
show the diver a Go dashboard.

## Suggested shape (for the Go spec; the owner decides)
- **Optional.** Go keeps working fully without an account; sign-in only adds sync. No subscription.
- **Sign-in on a watch:** the phone-browser handoff Apnea uses on Wear OS (RemoteAuth / sign in on
  the phone), not on-watch Google Sign-In (it fails with 12501 on Wear OS; see Apnea's notes).
  Apple Watch: sign in on the paired iPhone app or Sign in with Apple.
- **One account per diver:** handle `account-exists-with-different-credential` and link
  methods, as in `spearo-vision/docs/HANDOFF-from-website-2026-10-18.md`.
- **What Go would write** (to agree with the website before building):
  - `users/{uid}` marker that Go has been used (for example `apps.go.last_seen_at`), so the
    website's Connected Apps shows Go. Today the website shows Go as "No account needed"
    (`Spearo-Empire/website/src/lib/app-connections.ts`, `NO_ACCOUNT_APPS`).
  - Saved spots and recent condition checks under `users/{uid}/…` (score, verdict, day), never
    shared publicly.
- **Private by default** (owner rule): nothing Go writes is public; never location in any
  public record; only the diver-chosen username is ever shown to others.
- **Firestore rules are project-wide** and live in `Spearo-Empire/firebase/firestore.rules`.
  Any new paths need a rules change there (with tests), not in Go's `firebase.json`.

## Website side (after Go writes data)
The website session adds a Go section to the dashboard and drops Go from `NO_ACCOUNT_APPS`.

## Store listings
Go's listings promise "no account required" / "nothing is linked to you". Adding sign-in means
updating the App Store privacy labels, the Play Data safety form and that copy (sign-in stays
optional, so "no account required" can stay true).
