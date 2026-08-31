import Foundation

struct MarineData {
    let waveHeight: Double        // metres
    // Optional: the API does not always report them, and a 0-second period is
    // not a calm sea, it is an absent reading. Rendered as "—" and skipped by
    // the score rather than penalised as a short period.
    let wavePeriod: Double?       // seconds
    let waveDirection: Double?    // degrees
    let seaSurfaceTemp: Double    // °C
    let fetchedAt: Date
}
