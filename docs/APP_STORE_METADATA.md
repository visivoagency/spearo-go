# App Store Metadata — Spearo Go

## App Name
Spearo Go

## Subtitle (30 chars max)
Dive-Day Verdict at a Glance

## Promotional Text (170 chars max, can be updated without review)
Check conditions before you gear up. Spearo Go crunches weather, swell, tides, and fish activity into one score so you know whether it's a GO day.

## Description

Spearo Go is the spearfisher's pre-dive checklist — on your wrist.

Before you load the car, just glance at your Apple Watch. Spearo Go combines real-time weather, swell height and period, tide phases, and solunar fish activity into a single 0–10 score with a clear verdict: GO, MAYBE, SKETCHY, or NO GO.

WHAT IT SCORES
• Wind speed, gusts, and direction
• Swell height and period
• Water temperature and visibility estimate
• Tide state (rising, falling, slack)
• Solunar major and minor feeding periods
• Moon phase influence on fish activity

HOW IT WORKS
Each factor is scored individually, then combined using a weighted algorithm (Weather 30%, Marine 30%, Tides 15%, Solunar 25%) tuned for spearfishing conditions. The result is one number and one word — so you spend less time checking apps and more time in the water.

FEATURES
• One-tap verdict — GO, MAYBE, SKETCHY, or NO GO
• Composite score from 0 to 10 with animated score ring
• Five detailed pages: Verdict, Conditions, Water, Tides, Fish Activity
• Save multiple dive spots and switch between them
• Background refresh every 30 minutes — always current
• Real tide predictions from NOAA and WorldTides gauges
• Haptic feedback when conditions change
• Fully accessible with VoiceOver support
• No subscriptions, no ads. Account optional, for spot sync

Spearo Go uses free Open-Meteo APIs for weather and marine data, and Spearo's own tide service, which sources predictions from NOAA tide stations in US waters and WorldTides elsewhere. Solunar periods are computed on the watch using Meeus orbital math. Your position is used only to fetch conditions. The tide service rounds it to about a kilometre and keeps that point for up to 24 hours so the same spot is not looked up repeatedly, and keeps a one-way hash of your IP address for a short time to limit abuse; neither is linked to you. There are no ads and no analytics. Signing in to a Spearo account is optional; if you do, your email and saved spots are kept in it, private and never shared, so you can see them at spearo.pro. Where a location has no marine or tide coverage, the app says so rather than estimating.

## Keywords (100 chars max, comma-separated)
spearfishing,dive,swell,tides,solunar,fishing,marine,weather,surf,ocean,watch,score,conditions,wind

## Primary Category
Sports

## Secondary Category
Weather

## Price
$2.99 (Tier 1)

## Content Rating
4+ (No objectionable content)

## Copyright
© 2026 Visivo Agency

## Support URL
https://spearotracker.com/contact

## Privacy Policy URL
https://spearotracker.com/privacy-policy

## App Privacy (nutrition label)

Both stores must list the same data types. Last aligned 2026-10-23 with the website
Privacy Policy and with the Play section in `docs/GOOGLE_PLAY_METADATA.md`. The
app's own manifest is `SpearoGo/PrivacyInfo.xcprivacy`; keep the three in step.

| Data type | Where it goes | Linked to the user | Shared | Purpose |
|-----------|---------------|--------------------|--------|---------|
| Location (device position) | Open-Meteo and the tide service (NOAA or WorldTides) | No | Yes, with those services, only to look up conditions | App functionality |
| Location (saved spot coordinates) | Open-Meteo and the tide service. If signed in, also your Spearo account | Yes, once signed in | No (the account copy is private) | App functionality |
| Email address (signed in only) | Your Spearo account | Yes | No | App functionality |
| Saved spot names (signed in only) | Your Spearo account | Yes | No | App functionality |

None of it is used for tracking, advertising or analytics.

What the server keeps:
- The tide service keeps the coordinate, rounded to about 1 km, for up to 24 hours so it can answer repeat requests.
- It also keeps a one-way hash of the IP address for a short time to limit abuse.
- Neither is linked to you.

App Store Connect answers (Data Types you collect):

| Apple data type | Linked to the user | Used to track | Purpose |
|-----------------|--------------------|---------------|---------|
| Coarse Location (device position) | No | No | App Functionality |
| Precise Location (saved spot coordinates) | Yes, once signed in | No | App Functionality |
| Email Address (signed in only) | Yes | No | App Functionality |
| Other User Content (saved spot names, signed in only) | Yes | No | App Functionality |

- Data Used to Track You: none. `NSPrivacyTracking` is false and there are no tracking domains.
- The one-way IP hash is kept for abuse limiting only and is not linked to you. It is described in the notes above rather than as a separate type; confirm that reading with the owner before submitting.
- Required-reason API: UserDefaults (CA92.1), already declared.

## Age Rating Questionnaire
- Made for Kids: No
- Unrestricted Web Access: No
- Gambling / Contests: No
- Violence: None
- Medical/Treatment Info: No
- Profanity / Crude Humor: No
- Horror/Fear Themes: No
- Alcohol/Tobacco/Drug References: No
- Sexual Content/Nudity: No

## What's New (Version 1.1.0)

Tide times now come from real tide stations.

Until now Spearo Go calculated tides on the watch using a simplified model. It
was wrong — in some places badly enough to show a high tide as a low. If you
have been using Spearo Go to time a dive around the tide, please treat any
earlier reading as unreliable.

Tides are now sourced from NOAA tide stations in US waters and from WorldTides
gauges elsewhere, and the app tells you which station a reading came from. Where
a spot has no tide or sea data at all, it now says so instead of estimating.

Also in this release:

• Save dive spots anywhere — search for a place by name and switch between
  your spots, instead of only getting conditions where you are standing
• Larger, clearer text throughout, and it now follows your watch's text size
• A new Today screen — air temperature, conditions, high and low, rain, and
  sunrise and sunset
• Corrected sunrise, sunset and moon times, which could be hours out
• Sea temperature and swell are no longer guessed where there is no data
• The verdict screen is redesigned, and says which readings it could not include

