import Foundation
import CoreLocation

// Solunar calculator — pure math, no API required, global coverage.
// Based on John Alden Knight's solunar theory:
//   Major periods: moon transit + anti-transit (~2h each)
//   Minor periods: moonrise + moonset (~1h each)
// Moon position computed via simplified orbital elements (Meeus, Ch.48).

struct SolunarService {
    func calculate(coordinate: CLLocationCoordinate2D, date: Date = Date()) -> SolunarData {
        let jd = julianDay(from: date)
        let moonPos = moonPosition(jd: jd)
        let sunPos  = sunPosition(jd: jd)

        let moonIllum = illumination(moonLon: moonPos.longitude, sunLon: sunPos.longitude)
        let moonPhase = moonPhaseValue(jd: jd)

        let (moonrise, moonset) = riseSet(
            declination: moonPos.declination,
            ra: moonPos.rightAscension,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            jd: jd,
            isMoon: true
        )

        let (sunrise, sunset) = riseSet(
            declination: sunPos.declination,
            ra: sunPos.rightAscension,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude,
            jd: jd,
            isMoon: false
        )

        // Major periods: ±1h around moon transit (upper/lower culmination)
        let transit = moonTransit(ra: moonPos.rightAscension, longitude: coordinate.longitude, jd: jd)
        let antiTransit = transit.addingTimeInterval(6 * 3600)

        // Minor periods: around moonrise/moonset
        let nextMajor: Date? = [transit, antiTransit]
            .filter { $0 > date.addingTimeInterval(-3600) }
            .min()

        let nextMinor: Date? = [moonrise, moonset]
            .compactMap { $0 }
            .filter { $0 > date.addingTimeInterval(-1800) }
            .min()

        let rating = activityRating(phase: moonPhase, illum: moonIllum, nextMajor: nextMajor, date: date)

        return SolunarData(
            moonPhase:        moonPhase,
            moonIllumination: moonIllum,
            moonrise:         moonrise,
            moonset:          moonset,
            sunrise:          sunrise,
            sunset:           sunset,
            nextMajorPeriod:  nextMajor,
            nextMinorPeriod:  nextMinor,
            activityRating:   rating,
            fetchedAt:        Date()
        )
    }

    // MARK: - Julian Day

    private func julianDay(from date: Date) -> Double {
        date.timeIntervalSince1970 / 86400.0 + 2440587.5
    }

    // MARK: - Moon position (low-precision, Meeus simplified)

    private struct CelestialPosition {
        let longitude: Double      // degrees
        let rightAscension: Double // hours
        let declination: Double    // degrees
    }

    private func moonPosition(jd: Double) -> CelestialPosition {
        let T = (jd - 2451545.0) / 36525.0
        let L0 = (218.316 + 13.176396 * (jd - 2451545.0)).truncatingRemainder(dividingBy: 360)
        let M  = (134.963 + 13.064993 * (jd - 2451545.0)).truncatingRemainder(dividingBy: 360)
        let F  = (93.272  + 13.229350 * (jd - 2451545.0)).truncatingRemainder(dividingBy: 360)

        let lon = L0 + 6.289 * sin(M * .pi / 180)
                     - 1.274 * sin((2 * F - M) * .pi / 180)
                     + 0.658 * sin(2 * F * .pi / 180)
                     - 0.214 * sin(2 * M * .pi / 180)
                     - 0.186 * sin((M - 0.0003 * T) * .pi / 180)

        let lat = 5.128 * sin(F * .pi / 180)

        // Equatorial coords
        let e = 23.439 - 0.0000004 * (jd - 2451545.0)
        let ra = atan2(
            sin(lon * .pi / 180) * cos(e * .pi / 180) - tan(lat * .pi / 180) * sin(e * .pi / 180),
            cos(lon * .pi / 180)
        ) * 180 / .pi / 15  // hours

        let dec = asin(
            sin(lat * .pi / 180) * cos(e * .pi / 180)
            + cos(lat * .pi / 180) * sin(e * .pi / 180) * sin(lon * .pi / 180)
        ) * 180 / .pi

        return CelestialPosition(longitude: lon.truncatingRemainder(dividingBy: 360),
                                  rightAscension: (ra + 24).truncatingRemainder(dividingBy: 24),
                                  declination: dec)
    }

    private func sunPosition(jd: Double) -> CelestialPosition {
        let D  = jd - 2451545.0
        let g  = (357.529 + 0.98560028 * D).truncatingRemainder(dividingBy: 360)
        let q  = (280.459 + 0.98564736 * D).truncatingRemainder(dividingBy: 360)
        let lon = q + 1.915 * sin(g * .pi / 180) + 0.020 * sin(2 * g * .pi / 180)
        let e  = 23.439 - 0.00000036 * D
        let ra = atan2(cos(e * .pi / 180) * sin(lon * .pi / 180), cos(lon * .pi / 180)) * 180 / .pi / 15
        let dec = asin(sin(e * .pi / 180) * sin(lon * .pi / 180)) * 180 / .pi
        return CelestialPosition(longitude: lon, rightAscension: (ra + 24).truncatingRemainder(dividingBy: 24), declination: dec)
    }

    // MARK: - Rise / Set times (approximate)

    private func riseSet(declination: Double, ra: Double, latitude: Double,
                         longitude: Double, jd: Double, isMoon: Bool) -> (rise: Date?, set: Date?) {
        let latR = latitude * .pi / 180
        let decR = declination * .pi / 180
        let h0 = isMoon ? -0.833 : -0.833  // standard refraction
        let cosH = (sin(h0 * .pi / 180) - sin(latR) * sin(decR)) / (cos(latR) * cos(decR))
        guard abs(cosH) <= 1 else { return (nil, nil) }  // circumpolar / never rises

        let H = acos(cosH) * 180 / .pi
        let noon = (ra - longitude / 15).truncatingRemainder(dividingBy: 24)
        let riseH = noon - H / 15
        let setH  = noon + H / 15

        func toDate(_ hours: Double) -> Date {
            let midnight = (jd - 0.5).rounded(.down) - 2440587.5  // unix midnight
            return Date(timeIntervalSince1970: midnight * 86400 + hours * 3600)
        }

        return (toDate(riseH), toDate(setH))
    }

    // MARK: - Moon transit

    private func moonTransit(ra: Double, longitude: Double, jd: Double) -> Date {
        let D = jd - 2451545.0
        let GMST = (280.46061837 + 360.98564736629 * D).truncatingRemainder(dividingBy: 360)
        let LST = (GMST + longitude).truncatingRemainder(dividingBy: 360)
        var hourAngle = ra * 15 - LST
        if hourAngle < 0 { hourAngle += 360 }
        let hoursUntilTransit = (360 - hourAngle) / 15.041  // sidereal rate
        return Date().addingTimeInterval(hoursUntilTransit * 3600)
    }

    // MARK: - Illumination

    private func illumination(moonLon: Double, sunLon: Double) -> Double {
        let angle = abs(moonLon - sunLon).truncatingRemainder(dividingBy: 360)
        return (1 - cos(angle * .pi / 180)) / 2
    }

    private func moonPhaseValue(jd: Double) -> Double {
        let D = jd - 2451545.0
        let raw = (D / 29.53058868).truncatingRemainder(dividingBy: 1)
        return raw < 0 ? raw + 1 : raw
    }

    // MARK: - Activity rating

    private func activityRating(phase: Double, illum: Double, nextMajor: Date?, date: Date) -> String {
        // Score 0–1 based on lunar phase (near full/new = best) + proximity to major period
        let phaseScore = cos(2 * .pi * phase) * 0.5 + 0.5  // 1 at new/full, 0 at quarters
        var timeScore: Double = 0.5
        if let major = nextMajor {
            let mins = abs(major.timeIntervalSince(date)) / 60
            timeScore = mins < 60 ? 1.0 : mins < 180 ? 0.7 : 0.4
        }
        let total = phaseScore * 0.5 + timeScore * 0.5
        switch total {
        case 0.75...: return "Excellent"
        case 0.55...: return "Good"
        case 0.35...: return "Fair"
        default:      return "Poor"
        }
    }
}
