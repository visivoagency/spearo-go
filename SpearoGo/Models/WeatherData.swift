import Foundation

struct WeatherData {
    let windSpeed: Double         // knots
    let windDirection: Double     // degrees
    let windGusts: Double         // knots

    // Optional because the API does not report them everywhere. They are shown
    // as "—" rather than substituted, and a missing value is skipped by the
    // score rather than scored as a neutral one.
    let visibility: Double?       // km
    let cloudCover: Int?          // %

    // Today
    let airTemp: Double?          // °C, now
    let tempMax: Double?          // °C, today's high
    let tempMin: Double?          // °C, today's low
    let precipitationChance: Int? // %, today's max
    let weatherCode: Int?         // WMO code

    let fetchedAt: Date

    /// Plain-language description of the WMO weather code.
    var conditionLabel: String? {
        guard let weatherCode else { return nil }
        switch weatherCode {
        case 0:          return "Clear"
        case 1, 2:       return "Partly cloudy"
        case 3:          return "Overcast"
        case 45, 48:     return "Fog"
        case 51, 53, 55: return "Drizzle"
        case 56, 57:     return "Freezing drizzle"
        case 61, 63, 65: return "Rain"
        case 66, 67:     return "Freezing rain"
        case 71, 73, 75: return "Snow"
        case 77:         return "Snow grains"
        case 80, 81, 82: return "Showers"
        case 85, 86:     return "Snow showers"
        case 95:         return "Thunderstorm"
        case 96, 99:     return "Thunderstorm, hail"
        default:         return nil
        }
    }
}
