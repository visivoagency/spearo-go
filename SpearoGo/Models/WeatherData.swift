import Foundation

struct WeatherData {
    let windSpeed: Double         // knots
    let windDirection: Double     // degrees
    let windGusts: Double         // knots
    let visibility: Double        // km
    let cloudCover: Int           // %
    let fetchedAt: Date
}
