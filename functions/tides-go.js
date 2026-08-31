/**
 * Tide predictions for Spearo Go.
 *
 * Go has two clients — watchOS in Swift and Wear OS in Kotlin — and no Firebase
 * SDK. Vision decides NOAA-vs-WorldTides in its Dart client and carries a NOAA
 * station list there; copying that into two languages is exactly how Go's two
 * TideService files came to hold the same defect twice. So the whole decision
 * lives here instead, and the clients only fetch, cache and render.
 *
 * Ported from spearo-vision/lib/services/tide_service.dart. The parameters in
 * here each encode a bug already paid for once — read the comments before
 * changing any of them.
 *
 * Kept free of Firebase imports so it can be tested directly.
 */

const NOAA_STATIONS_URL =
  "https://api.tidesandcurrents.noaa.gov/mdapi/prod/webapi/stations.json?type=tidepredictions";
const NOAA_TIDES_URL =
  "https://api.tidesandcurrents.noaa.gov/api/prod/datagetter";
const WORLDTIDES_URL = "https://www.worldtides.info/api/v3";

/** How far a NOAA station may be and still describe the caller's water. */
const MAX_STATION_DISTANCE_KM = 100;

/**
 * How far WorldTides may reach for a real gauge before falling back to its
 * ocean grid model. The account default is 10, which the dashboard labels
 * "degrees" while the API docs describe kilometres. At 10 degrees (~1,100km) a
 * query for the Algarve could be answered by a gauge off Biscay — the same
 * class of defect this whole change exists to remove.
 */
const WORLDTIDES_STATION_DISTANCE_KM = 50;

/**
 * Chart datum, matching the NOAA path's MLLW. WorldTides defaults to mean sea
 * level, which puts low tides below zero — a Portuguese diver would read
 * "-0.76m" for the same water a US diver sees as "0.4m". Beyond looking broken,
 * a negative depth is actively confusing to someone judging whether a spot is
 * divable, and CD is what printed tide tables use.
 */
const WORLDTIDES_DATUM = "CD";

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function nearestStation(stations, lat, lon, maxKm = MAX_STATION_DISTANCE_KM) {
  let best = null;
  for (const s of stations) {
    const distanceKm = haversineKm(lat, lon, s.latitude, s.longitude);
    if (!best || distanceKm < best.distanceKm) best = { ...s, distanceKm };
  }
  return best && best.distanceKm <= maxKm ? best : null;
}

/**
 * Spring / neap, from the day's own range. Time-independent, so it is computed
 * here rather than in each client.
 */
function tidalRangeLabel(events) {
  if (events.length < 2) return "Normal";
  const heights = events.map((e) => e.height);
  const range = Math.max(...heights) - Math.min(...heights);
  if (range > 2.0) return "Spring";
  if (range < 1.0) return "Neap";
  return "Normal";
}

/**
 * Group events and heights into local days.
 *
 * Deliberately NOT computing "current height", "next tide" or "rising" here:
 * those depend on the moment of rendering, and a client caches a week of this.
 * The clients derive them from these events at display time.
 */
function bucketByDay(events, heights) {
  const days = new Map();
  const dayOf = (t, offset) => {
    const local = new Date((t + offset) * 1000);
    return local.toISOString().slice(0, 10);
  };
  for (const e of events) {
    const key = dayOf(e.t, e.utcOffsetSeconds ?? 0);
    if (!days.has(key)) days.set(key, { date: key, extremes: [], heights: [] });
    days.get(key).extremes.push({ t: e.t, type: e.type, height: e.height });
  }
  for (const h of heights) {
    const key = dayOf(h.t, h.utcOffsetSeconds ?? 0);
    if (!days.has(key)) days.set(key, { date: key, extremes: [], heights: [] });
    days.get(key).heights.push({ t: h.t, height: h.height });
  }
  return [...days.values()]
    .filter((d) => d.extremes.length > 0)
    .map((d) => {
      d.extremes.sort((a, b) => a.t - b.t);
      d.heights.sort((a, b) => a.t - b.t);
      d.tidalRange = tidalRangeLabel(d.extremes);
      return d;
    })
    .sort((a, b) => a.date.localeCompare(b.date));
}

/** NOAA returns station-local wall clock, e.g. "2026-03-05 05:42". */
function parseNoaaTime(value, utcOffsetSeconds) {
  if (typeof value !== "string") return null;
  const ms = Date.parse(`${value.replace(" ", "T")}Z`);
  if (Number.isNaN(ms)) return null;
  return Math.floor(ms / 1000) - utcOffsetSeconds;
}

/**
 * WorldTides returns "2026-08-09T13:52:46+02:00" with `localtime` set. The
 * epoch it also returns is authoritative; the offset is recovered from the
 * difference so clients can render station-local wall clock rather than device
 * time. Letting a date parser normalise the string to UTC is what turned a
 * 13:52 high tide into 11:52 in Vision — the same wrong-time defect, two layers
 * below where it was fixed.
 */
function worldTidesOffsetSeconds(entry) {
  if (typeof entry?.date !== "string" || typeof entry?.dt !== "number") return 0;
  const asUtc = Date.parse(entry.date.replace(/[+-]\d{2}:\d{2}$/, "Z"));
  if (Number.isNaN(asUtc)) return 0;
  return Math.round(asUtc / 1000 - entry.dt);
}

async function fetchNoaaStations(fetchImpl) {
  const resp = await fetchImpl(NOAA_STATIONS_URL);
  if (!resp.ok) throw new Error(`NOAA stations ${resp.status}`);
  const body = await resp.json();
  return (body.stations || [])
    .filter((s) => s && s.id != null && s.lat != null && s.lng != null)
    .map((s) => ({
      id: String(s.id),
      name: s.name || "Unknown",
      latitude: Number(s.lat),
      longitude: Number(s.lng),
    }));
}

async function fetchNoaaPredictions(stationId, beginDate, endDate, interval, fetchImpl) {
  const url =
    `${NOAA_TIDES_URL}?product=predictions&station=${encodeURIComponent(stationId)}` +
    `&begin_date=${beginDate}&end_date=${endDate}` +
    `&datum=MLLW&time_zone=lst_ldt&units=metric&format=json&interval=${interval}`;
  const resp = await fetchImpl(url);
  if (!resp.ok) throw new Error(`NOAA predictions ${resp.status}`);
  const body = await resp.json();
  return body.predictions || [];
}

/** YYYYMMDD, as NOAA expects. */
function noaaDateKey(date) {
  return date.toISOString().slice(0, 10).replace(/-/g, "");
}

async function fromNoaa({ station, date, days, fetchImpl }) {
  const begin = new Date(`${date}T00:00:00Z`);
  const end = new Date(begin.getTime() + (days - 1) * 86400000);
  const [hilo, hourly] = await Promise.all([
    fetchNoaaPredictions(station.id, noaaDateKey(begin), noaaDateKey(end), "hilo", fetchImpl),
    fetchNoaaPredictions(station.id, noaaDateKey(begin), noaaDateKey(end), "h", fetchImpl),
  ]);

  // NOAA answers in the station's own local time and does not state the offset.
  // Treating those digits as UTC and carrying an offset of zero keeps the wall
  // clock the station reported, which is what a printed table shows.
  const events = hilo
    .map((p) => ({
      t: parseNoaaTime(p.t, 0),
      type: p.type === "H" ? "high" : "low",
      height: Number.parseFloat(p.v) || 0,
      utcOffsetSeconds: 0,
    }))
    .filter((e) => e.t != null);
  const heights = hourly
    .map((p) => ({ t: parseNoaaTime(p.t, 0), height: Number.parseFloat(p.v) || 0, utcOffsetSeconds: 0 }))
    .filter((h) => h.t != null);

  return {
    available: events.length > 0,
    source: "noaa",
    station: station.name,
    stationDistanceKm: Math.round(station.distanceKm * 10) / 10,
    provenance: "gauge",
    datum: "MLLW",
    utcOffsetSeconds: 0,
    days: bucketByDay(events, heights),
  };
}

async function fromWorldTides({ lat, lon, date, days, apiKey, fetchImpl }) {
  const url =
    `${WORLDTIDES_URL}?extremes&heights&localtime` +
    `&datum=${WORLDTIDES_DATUM}` +
    `&lat=${lat}&lon=${lon}&days=${days}` +
    (date ? `&date=${encodeURIComponent(date)}` : "") +
    `&stationDistance=${WORLDTIDES_STATION_DISTANCE_KM}` +
    `&key=${apiKey}`;

  const resp = await fetchImpl(url);
  if (!resp.ok) {
    // A 4xx from WorldTides means it has nothing for this coordinate — it
    // answers 400 for a landlocked point. That is a settled fact about the
    // place, not a fault, and must be reported differently from a 5xx or a
    // network error: the client may cache "there is no sea here" and should
    // keep retrying a transient failure.
    if (resp.status >= 400 && resp.status < 500) {
      return { available: false, reason: "outside_coverage" };
    }
    throw new Error(`WorldTides ${resp.status}`);
  }
  const body = await resp.json();

  const rawExtremes = body.extremes || [];
  const offset = rawExtremes.length ? worldTidesOffsetSeconds(rawExtremes[0]) : 0;

  const events = rawExtremes
    .filter((e) => typeof e.dt === "number")
    .map((e) => ({
      t: e.dt,
      type: String(e.type || "").toLowerCase() === "high" ? "high" : "low",
      height: Number(e.height) || 0,
      utcOffsetSeconds: offset,
    }));
  const heights = (body.heights || [])
    .filter((h) => typeof h.dt === "number")
    .map((h) => ({ t: h.dt, height: Number(h.height) || 0, utcOffsetSeconds: offset }));

  return {
    available: events.length > 0,
    // `station` is present only when a real gauge answered. Its absence means
    // the ocean grid model, which is materially weaker in estuaries and inlets
    // — exactly the water this app's users dive. Carried through so the UI can
    // never present the two as equally trustworthy.
    source: "worldtides",
    station: body.station || null,
    stationDistanceKm: null,
    provenance: body.station ? "gauge" : "model",
    datum: WORLDTIDES_DATUM,
    utcOffsetSeconds: offset,
    days: bucketByDay(events, heights),
  };
}

/**
 * NOAA first where it reaches — it is free and gauge-based — then WorldTides,
 * which is metered.
 *
 * `stations` is passed in so the caller owns caching the ~3,000-entry list.
 */
async function resolveTides({ lat, lon, date, days, apiKey, stations, fetchImpl = fetch }) {
  const station = stations ? nearestStation(stations, lat, lon) : null;
  if (station) {
    const result = await fromNoaa({ station, date, days, fetchImpl });
    if (result.available) return result;
  }
  if (!apiKey) return { available: false, reason: "outside_coverage" };
  return fromWorldTides({ lat, lon, date, days, apiKey, fetchImpl });
}

module.exports = {
  haversineKm,
  nearestStation,
  tidalRangeLabel,
  bucketByDay,
  parseNoaaTime,
  worldTidesOffsetSeconds,
  fetchNoaaStations,
  resolveTides,
  fromNoaa,
  fromWorldTides,
  MAX_STATION_DISTANCE_KM,
  WORLDTIDES_STATION_DISTANCE_KM,
  WORLDTIDES_DATUM,
};
