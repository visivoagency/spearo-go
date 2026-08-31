import Foundation
import CoreLocation

struct WeatherService {
    // Open-Meteo weather API — free, no key, global coverage
    // Docs: https://open-meteo.com/en/docs

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        return URLSession(configuration: config)
    }()

    func fetch(coordinate: CLLocationCoordinate2D) async throws -> WeatherData {
        let url = try buildURL(coordinate: coordinate)
        let (data, response) = try await session.data(from: url)

        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw ServiceError.badResponse
        }

        let decoded = try JSONDecoder().decode(OpenMeteoWeatherResponse.self, from: data)
        guard let current = decoded.current else { throw ServiceError.missingData }

        // Wind is the one reading this app cannot do without, and a missing
        // wind speed must not become a flat calm — a 0 kn default reads as
        // ideal conditions. Everything else is carried through as optional and
        // rendered as "—" rather than filled in.
        guard let windMS = current.wind_speed_10m,
              let windDir = current.wind_direction_10m else {
            throw ServiceError.missingData
        }

        // Convert m/s → knots
        let windKnots  = windMS * 1.94384
        let gustsKnots = current.wind_gusts_10m.map { $0 * 1.94384 }

        let daily = decoded.daily

        return WeatherData(
            windSpeed:     windKnots,
            windDirection: windDir,
            windGusts:     gustsKnots ?? windKnots,
            visibility:    current.visibility.map { $0 / 1000 },
            cloudCover:    current.cloud_cover,
            airTemp:       current.temperature_2m,
            tempMax:       daily?.temperature_2m_max?.first ?? nil,
            tempMin:       daily?.temperature_2m_min?.first ?? nil,
            precipitationChance: daily?.precipitation_probability_max?.first.flatMap { $0 },
            weatherCode:   daily?.weather_code?.first ?? current.weather_code,
            fetchedAt:     Date()
        )
    }

    private func buildURL(coordinate: CLLocationCoordinate2D) throws -> URL {
        guard var components = URLComponents(string: Constants.API.weatherBase) else {
            throw ServiceError.invalidURL
        }
        components.queryItems = [
            .init(name: "latitude",         value: String(coordinate.latitude)),
            .init(name: "longitude",        value: String(coordinate.longitude)),
            .init(name: "current",          value: "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility,temperature_2m,weather_code"),
            .init(name: "daily",            value: "temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code"),
            .init(name: "wind_speed_unit",  value: "ms"),
            .init(name: "timezone",         value: "auto"),
            .init(name: "forecast_days",    value: "1")
        ]
        guard let url = components.url else {
            throw ServiceError.invalidURL
        }
        return url
    }
}

// MARK: - Response shapes

private struct OpenMeteoWeatherResponse: Decodable {
    let current: CurrentWeather?
    let daily: DailyWeather?
}

private struct CurrentWeather: Decodable {
    let wind_speed_10m:      Double?
    let wind_direction_10m:  Double?
    let wind_gusts_10m:      Double?
    let cloud_cover:         Int?
    let visibility:          Double?
    let temperature_2m:      Double?
    let weather_code:        Int?
}

private struct DailyWeather: Decodable {
    let temperature_2m_max:           [Double]?
    let temperature_2m_min:           [Double]?
    let precipitation_probability_max: [Int?]?
    let weather_code:                 [Int]?
}

enum ServiceError: Error {
    case badResponse
    case missingData
    /// The coordinate has no sea: the marine API answered, with nothing in it.
    case noMarineCoverage
    case invalidCoordinate
    case invalidURL
}
