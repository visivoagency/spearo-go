# Handoff: Play trader status, one step per app (from Spearo Go, 2026-11-06)

One app per conversation, so Spearo Go did only its own step. Each app below needs the same
step, done in that app's own session.

## What is already done (account level)

On 6 Nov 2026 the **Visivo Studio** Play developer account (info@visivoagency.com, `/console/u/2`,
account id 6266345792555381063) was declared a **trader**. Kevin approved the public details:
VISIVO, LLC, 902 Santa Fe St, Newton KS 67114-2618, US, info@visivoagency.com, +1 316 727 9329,
D-U-N-S 082166586. The name, address and D-U-N-S come from the Google payments profile; the email and
phone come from Account details > Contact details.

Spearo Go's own step was done the same day: "Yes, my trader status applies", Terms URL
`https://spearo.pro/terms`, sent for review.

## The step for each other app

Play Console > the app > **Policy and programs > App content** > "App-level trader status and Terms of
Service" > **Start declaration**:
1. Keep "Yes, my trader status applies to this app".
2. Terms of Service: the app's own public Terms page (it must be a normal web page, reachable worldwide
   with no login, and specific to the app). Check it returns 200 first.
3. Save, then go to **Publishing overview** and **Send changes for review**. If a release is already
   waiting there, it goes along with it.

| App on the account | Package | Repo | Terms URL to check |
|---|---|---|---|
| Spearo Vision Weather | com.visivoagency.spearo_vision | ~/Documents/spearo-vision | probably https://spearo.pro/terms |
| Spearfishing Tracker: Dive Log | com.visivoagency.spearolog | ~/Documents/Spearo-Log | probably https://spearo.pro/terms |
| Spearo Apnea (phone) | com.spearotracker.apnea.phone | ~/Documents/Spearo-apnea | probably https://spearo.pro/terms |
| Spearo Apnea Trainer (Wear) | com.spearotracker.apnea | ~/Documents/Spearo-apnea | probably https://spearo.pro/terms |
| VidBid - Video Estimates | (see console) | not a Spearo app | the app's own Terms page |
| Wisefam: Family Organizer | (see console) | not a Spearo app | the app's own Terms page |

The account-level warning says that without a trader declaration the profile **and all apps** are
removed, so don't leave these for long.
