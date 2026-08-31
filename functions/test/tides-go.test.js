const test = require("node:test");
const assert = require("node:assert/strict");
const {
  haversineKm,
  nearestStation,
  tidalRangeLabel,
  bucketByDay,
  parseNoaaTime,
  worldTidesOffsetSeconds,
  resolveTides,
} = require("../tides-go");

/**
 * Spearo Go shipped a synthetic tide model that anchored its M2 term to the
 * Unix epoch and discarded latitude, so the phase was arbitrary everywhere on
 * earth. A customer in Lagos, Portugal reported times matching no real
 * sequence. This module is what replaces it, and the point of it is that the
 * clients contain no tide logic at all — so the logic has to be right here.
 */

const fakeResponse = (body, ok = true, status = 200) => ({
  ok,
  status,
  json: async () => body,
});

test("haversine matches a known separation", () => {
  // Lagos, Portugal to Faro, ~66km apart.
  const km = haversineKm(37.1028, -8.6741, 37.0194, -7.9304);
  assert.ok(Math.abs(km - 66) < 5, `expected ~66km, got ${km.toFixed(1)}`);
});

test("a station beyond the range is not used at all", () => {
  const stations = [
    { id: "1", name: "Far", latitude: 37.1, longitude: -30.0 },
  ];
  // The old WorldTides account default of 10 "degrees" is what let a gauge
  // 1,100km away answer for the Algarve. Nothing outside range may win.
  assert.equal(nearestStation(stations, 37.1028, -8.6741), null);
});

test("the nearest station wins, and carries its distance", () => {
  const stations = [
    { id: "far", name: "Far", latitude: 38.0, longitude: -8.6 },
    { id: "near", name: "Near", latitude: 37.11, longitude: -8.67 },
  ];
  const s = nearestStation(stations, 37.1028, -8.6741);
  assert.equal(s.id, "near");
  assert.ok(s.distanceKm < 5);
});

test("tidal range is labelled from the day's own spread", () => {
  const spring = [{ height: 0.3 }, { height: 3.4 }];
  const neap = [{ height: 1.4 }, { height: 2.1 }];
  const normal = [{ height: 0.9 }, { height: 2.4 }];
  assert.equal(tidalRangeLabel(spring), "Spring");
  assert.equal(tidalRangeLabel(neap), "Neap");
  assert.equal(tidalRangeLabel(normal), "Normal");
  assert.equal(tidalRangeLabel([{ height: 1 }]), "Normal");
});

test("days are split on station-local time, not UTC", () => {
  // 23:30 local on the 31st at +02:00 is 21:30 UTC the same day; an event at
  // 00:30 local on the 1st is 22:30 UTC on the 31st. Bucketing on UTC would
  // put the second one on the wrong day.
  const offset = 7200;
  const events = [
    { t: Date.parse("2026-08-31T21:30:00Z") / 1000, type: "high", height: 3.1, utcOffsetSeconds: offset },
    { t: Date.parse("2026-08-31T22:30:00Z") / 1000, type: "low", height: 0.4, utcOffsetSeconds: offset },
  ];
  const days = bucketByDay(events, []);
  assert.deepEqual(days.map((d) => d.date), ["2026-08-31", "2026-09-01"]);
});

test("days with no extremes are dropped rather than returned empty", () => {
  const heights = [{ t: Date.parse("2026-08-31T10:00:00Z") / 1000, height: 2.0, utcOffsetSeconds: 0 }];
  assert.deepEqual(bucketByDay([], heights), []);
});

test("NOAA wall-clock times are preserved, not shifted", () => {
  // NOAA answers in the station's own local time and states no offset. The
  // digits it reports are what a printed table shows.
  const t = parseNoaaTime("2026-03-05 05:42", 0);
  assert.equal(new Date(t * 1000).toISOString(), "2026-03-05T05:42:00.000Z");
  assert.equal(parseNoaaTime("nonsense", 0), null);
  assert.equal(parseNoaaTime(null, 0), null);
});

test("the WorldTides offset is recovered from its own timestamp", () => {
  // "13:52:46+02:00" against the epoch it also returns. Getting this wrong is
  // what rendered a 13:52 high tide as 11:52 in the Flutter app.
  const dt = Date.parse("2026-08-09T11:52:46Z") / 1000;
  const offset = worldTidesOffsetSeconds({ date: "2026-08-09T13:52:46+02:00", dt });
  assert.equal(offset, 7200);
  assert.equal(worldTidesOffsetSeconds({}), 0);
});

test("NOAA is preferred where it reaches, and WorldTides is never called", async () => {
  const stations = [{ id: "8518750", name: "The Battery", latitude: 40.7, longitude: -74.015 }];
  let worldTidesCalled = false;
  const fetchImpl = async (url) => {
    if (url.includes("worldtides")) {
      worldTidesCalled = true;
      return fakeResponse({});
    }
    return fakeResponse({
      predictions: url.includes("interval=hilo")
        ? [{ t: "2026-08-31 05:58", v: "0.4", type: "L" }, { t: "2026-08-31 12:11", v: "1.6", type: "H" }]
        : [{ t: "2026-08-31 06:00", v: "0.41" }],
    });
  };

  const result = await resolveTides({
    lat: 40.7, lon: -74.015, date: "2026-08-31", days: 7,
    apiKey: "unused", stations, fetchImpl,
  });

  assert.equal(worldTidesCalled, false, "NOAA covered this spot; nothing should be billed");
  assert.equal(result.source, "noaa");
  assert.equal(result.provenance, "gauge");
  assert.equal(result.datum, "MLLW");
  assert.equal(result.days[0].extremes.length, 2);
  assert.equal(result.days[0].extremes[0].type, "low");
});

test("outside NOAA, WorldTides answers with its provenance carried through", async () => {
  // Lagos, Portugal — the customer's spot, and outside NOAA entirely.
  const dt = Date.parse("2026-08-31T02:49:00Z") / 1000;
  const fetchImpl = async (url) => {
    assert.ok(url.includes("datum=CD"), "chart datum, or low tides render negative");
    assert.ok(url.includes("stationDistance=50"), "50km, or Biscay answers for the Algarve");
    return fakeResponse({
      station: "Lagos",
      extremes: [
        { dt, date: "2026-08-31T03:49:00+01:00", type: "Low", height: 0.5 },
        { dt: dt + 22200, date: "2026-08-31T09:59:00+01:00", type: "High", height: 3.2 },
      ],
      heights: [{ dt, date: "2026-08-31T03:49:00+01:00", height: 0.5 }],
    });
  };

  const result = await resolveTides({
    lat: 37.1, lon: -8.67, date: "2026-08-31", days: 7,
    apiKey: "test-key", stations: [], fetchImpl,
  });

  assert.equal(result.available, true);
  assert.equal(result.source, "worldtides");
  assert.equal(result.station, "Lagos");
  assert.equal(result.provenance, "gauge");
  assert.equal(result.utcOffsetSeconds, 3600, "Portugal is UTC+1 in August");
  assert.equal(result.days[0].extremes[0].type, "low");
});

test("a grid-model answer is never presented as a gauge reading", async () => {
  const dt = Date.parse("2026-08-31T02:00:00Z") / 1000;
  const fetchImpl = async () =>
    fakeResponse({
      // No `station` key: this is the ocean model, weaker in exactly the
      // estuaries and inlets this app's users dive.
      extremes: [{ dt, date: "2026-08-31T02:00:00+00:00", type: "High", height: 1.1 }],
      heights: [],
    });

  const result = await resolveTides({
    lat: -20.0, lon: 57.5, date: "2026-08-31", days: 7,
    apiKey: "test-key", stations: [], fetchImpl,
  });

  assert.equal(result.provenance, "model");
  assert.equal(result.station, null);
});

test("no key and no NOAA station reports unavailable rather than guessing", async () => {
  const result = await resolveTides({
    lat: 37.1, lon: -8.67, date: "2026-08-31", days: 7,
    apiKey: null, stations: [], fetchImpl: async () => fakeResponse({}),
  });
  assert.equal(result.available, false);
  assert.equal(result.reason, "outside_coverage");
});

test("an empty WorldTides answer is unavailable, not an empty tide day", async () => {
  const fetchImpl = async () => fakeResponse({ extremes: [], heights: [] });
  const result = await resolveTides({
    lat: 47.0, lon: 8.0, date: "2026-08-31", days: 7,
    apiKey: "test-key", stations: [], fetchImpl,
  });
  assert.equal(result.available, false);
  assert.deepEqual(result.days, []);
});

test("a landlocked coordinate is outside coverage, not a failure", async () => {
  // WorldTides answers HTTP 400 for a point with no sea. Confirmed against the
  // live API for Queidersbach, Rheinland-Pfalz. Reporting that as a failure
  // would have clients retrying a place that will never have tides, and would
  // stop the answer being cached.
  const fetchImpl = async () => ({ ok: false, status: 400, json: async () => ({}) });
  const result = await resolveTides({
    lat: 49.3486, lon: 7.6486, date: "2026-08-31", days: 7,
    apiKey: "test-key", stations: [], fetchImpl,
  });
  assert.equal(result.available, false);
  assert.equal(result.reason, "outside_coverage");
});

test("a server-side WorldTides fault stays a failure, so it is retried", async () => {
  const fetchImpl = async () => ({ ok: false, status: 503, json: async () => ({}) });
  await assert.rejects(
    () => resolveTides({
      lat: 37.1, lon: -8.67, date: "2026-08-31", days: 7,
      apiKey: "test-key", stations: [], fetchImpl,
    }),
    /WorldTides 503/,
  );
});
