/**
 * Cloud Functions for Spearo Go.
 *
 * Deployed to the shared `spearo-tracker` project under its OWN codebase
 * (`spearogo`, see ../firebase.json). Spearo Vision owns the `default`
 * codebase. Two repos deploying the same codebase would delete each other's
 * functions, so the name must stay distinct.
 *
 * Deploy by name:  firebase deploy --only functions:tidesGo
 */

const { onRequest } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp } = require("firebase-admin/firestore");
const crypto = require("node:crypto");

// Tide resolution lives in its own module so the NOAA and WorldTides logic can
// be tested without any Firebase initialisation. See tides-go.js.
const { resolveTides, fetchNoaaStations, nearestStation } = require("./tides-go");

initializeApp();
const db = getFirestore();

// ============================================================================
// tidesGo — tide predictions for Spearo Go
// ----------------------------------------------------------------------------
// Separate from `tidesProxy`, which requires an authenticated caller. Spearo Go
// has no accounts and ships no Firebase SDK, so this is a plain HTTPS endpoint.
//
// That means no App Check: an App Check token needs the client SDK, which Go
// deliberately does not carry. The cost controls below are what stands in for
// it — a per-IP rate limit, a hard daily ceiling on metered calls, and a shared
// cache so the same spot is never billed twice in a day. The ceiling is the one
// that matters: it fails closed, returning `available:false` rather than
// spending, so an open endpoint cannot run up a bill.
// ============================================================================

// Requests per IP per hour. Generous for a watch refreshing every 30 minutes
// across a few saved spots; nowhere near enough to scrape the API.
const TIDES_RATE_LIMIT = 60;

// Metered WorldTides calls per day, across all users.
//
// The WorldTides plan is 20,000 credits a month and is SHARED with Spearo
// Vision, which reads the same WORLDTIDES_API_KEY secret in this project.
// Vision was running at roughly 63 credits a day as of 2026-09-01, so about
// 2,000 a month. This ceiling reserves the remainder for Go while leaving
// Vision several times its current usage in headroom:
//
//   500/day x 31 = 15,500/month for Go, ~4,500 left for Vision
//
// A call is not a user. Each one covers a WEEK for one ~1km grid square and is
// cached server-side for 24 hours, so this is 500 distinct spots a day, not 500
// refreshes. A watch checking one spot every 30 minutes costs one credit a day.
//
// It fails closed: past the ceiling the app reports no tide data rather than
// spending. That is a blunt failure — everyone loses tides for the rest of the
// day — so the per-IP rate limit above is what should stop abuse first, and
// this is the backstop behind it. Daily totals are in the tides_budget
// collection if you want to see where real usage sits before changing it.
const TIDES_DAILY_CALL_CEILING = 500;

// A day of tides does not change. NOAA and WorldTides are both predictions.
const TIDES_CACHE_HOURS = 24;

// Retention. Every collection this function writes carries an `expiresAt`, and
// each has a Firestore TTL policy configured on that field:
//
//   gcloud firestore fields ttls update expiresAt \
//     --collection-group=tides_cache --enable-ttl --project=spearo-tracker
//   (likewise tides_rate and tides_budget)
//
// Nothing here is linked to a person — no accounts, no identifiers — but a
// rounded coordinate and a hashed IP should still not sit around indefinitely,
// and the privacy disclosure promises 24 hours.

// The NOAA station list is ~3,000 entries and changes rarely. Held per warm
// instance so a cold start pays for it at most once a day.
let noaaStationCache = { fetchedAt: 0, stations: null };

async function noaaStations() {
  const age = Date.now() - noaaStationCache.fetchedAt;
  if (noaaStationCache.stations && age < 24 * 60 * 60 * 1000) {
    return noaaStationCache.stations;
  }
  try {
    const stations = await fetchNoaaStations(fetch);
    noaaStationCache = { fetchedAt: Date.now(), stations };
    return stations;
  } catch (e) {
    console.error("NOAA station list unavailable:", e.message);
    // Not fatal: without it every caller simply falls through to WorldTides.
    return noaaStationCache.stations || null;
  }
}

/** Fails closed. A rate-limit check that throws must not become free access. */
async function withinRateLimit(ipHash) {
  const ref = db.collection("tides_rate").doc(ipHash);
  const hourStart = Math.floor(Date.now() / 3600000) * 3600000;
  try {
    return await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const data = snap.exists ? snap.data() : null;
      const count = data && data.windowStart === hourStart ? data.count : 0;
      if (count >= TIDES_RATE_LIMIT) return false;
      // expiresAt drives a Firestore TTL policy. Without it these documents —
      // one per caller IP hash — accumulate forever, which is both unbounded
      // storage and an indefinite retention of something derived from an IP.
      tx.set(ref, {
        windowStart: hourStart,
        count: count + 1,
        expiresAt: Timestamp.fromMillis(hourStart + 2 * 3600000),
      }, { merge: true });
      return true;
    });
  } catch (e) {
    console.error("tidesGo rate limit check failed:", e.message);
    return false;
  }
}

/** Reserves one metered call, or refuses. Also fails closed. */
async function reserveMeteredCall() {
  const day = new Date().toISOString().slice(0, 10);
  const ref = db.collection("tides_budget").doc(day);
  try {
    return await db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      const used = snap.exists ? snap.data().calls || 0 : 0;
      if (used >= TIDES_DAILY_CALL_CEILING) return false;
      tx.set(ref, {
        calls: used + 1,
        day,
        expiresAt: Timestamp.fromMillis(Date.now() + 7 * 86400000),
      }, { merge: true });
      return true;
    });
  } catch (e) {
    console.error("tidesGo budget check failed:", e.message);
    return false;
  }
}

exports.tidesGo = onRequest(
  { region: "us-central1", secrets: ["WORLDTIDES_API_KEY"], cors: true, maxInstances: 5 },
  async (req, res) => {
    const q = req.method === "POST" ? req.body || {} : req.query || {};
    const lat = Number(q.lat);
    const lon = Number(q.lon);

    if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
      res.status(400).json({ available: false, reason: "invalid_argument" });
      return;
    }
    if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
      res.status(400).json({ available: false, reason: "invalid_argument" });
      return;
    }

    // Rejected rather than defaulted if malformed, so a bad client date cannot
    // silently return today's tides for another day.
    const date = typeof q.date === "string" ? q.date : "";
    if (date && !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
      res.status(400).json({ available: false, reason: "invalid_argument" });
      return;
    }

    // WorldTides bills one credit per seven days of extremes, so a week costs
    // what a day costs. Capped so a caller cannot request a year.
    const days = Math.min(Math.max(Number(q.days) || 7, 1), 7);

    const ip = req.headers["x-forwarded-for"]?.split(",")[0]?.trim() || req.ip || "unknown";
    const ipHash = crypto.createHash("sha256").update(ip).digest("hex").slice(0, 32);
    if (!(await withinRateLimit(ipHash))) {
      res.status(429).json({ available: false, reason: "rate_limited" });
      return;
    }

    // Rounded to ~1km so the cache key and any billed request share a grid, and
    // so two divers at the same spot are billed once.
    const gridLat = lat.toFixed(2);
    const gridLon = lon.toFixed(2);
    const startDate = date || new Date().toISOString().slice(0, 10);
    const cacheKey = `${gridLat}_${gridLon}_${startDate}_${days}`;
    const cacheRef = db.collection("tides_cache").doc(cacheKey);

    try {
      const cached = await cacheRef.get();
      if (cached.exists) {
        const { payload, expiresAt } = cached.data();
        if (expiresAt && expiresAt.toMillis() > Date.now()) {
          res.set("Cache-Control", "private, max-age=3600");
          res.json(payload);
          return;
        }
      }
    } catch (e) {
      console.error("tidesGo cache read failed:", e.message);
    }

    const stations = await noaaStations();
    const nearNoaa = stations
      ? nearestStation(stations, lat, lon) !== null
      : false;

    // Only a WorldTides call is metered; NOAA is free.
    if (!nearNoaa && !(await reserveMeteredCall())) {
      res.status(503).json({ available: false, reason: "budget_exceeded" });
      return;
    }

    try {
      const payload = await resolveTides({
        lat: Number(gridLat),
        lon: Number(gridLon),
        date: startDate,
        days,
        apiKey: process.env.WORLDTIDES_API_KEY,
        stations,
        fetchImpl: fetch,
      });

      // Cached when the answer is settled: real predictions, or a definite
      // "this coordinate has no sea". A transient failure is deliberately NOT
      // cached — an outage must not persist as unavailable once it recovers.
      if (payload.available || payload.reason === "outside_coverage") {
        await cacheRef.set({
          payload,
          expiresAt: Timestamp.fromMillis(Date.now() + TIDES_CACHE_HOURS * 3600000),
        });
      }
      res.json(payload);
    } catch (e) {
      console.error("tidesGo lookup failed:", e.message);
      res.status(502).json({ available: false, reason: "lookup_failed" });
    }
  },
);
