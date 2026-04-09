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

        // Convert m/s → knots
        let windKnots   = (current.wind_speed_10m ?? 0) * 1.94384
        let gustsKnots  = (current.wind_gusts_10m ?? 0) * 1.94384

        return WeatherData(
            windSpeed:     windKnots,
            windDirection: current.wind_direction_10m ?? 0,
            windGusts:     gustsKnots,
            visibility:    (current.visibility ?? 10000) / 1000,
            cloudCover:    current.cloud_cover ?? 0,
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
            .init(name: "current",          value: "wind_speed_10m,wind_direction_10m,wind_gusts_10m,cloud_cover,visibility"),
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
}

private struct CurrentWeather: Decodable {
    let wind_speed_10m:      Double?
    let wind_direction_10m:  Double?
    let wind_gusts_10m:      Double?
    let cloud_cover:         Int?
    let visibility:          Double?
}

enum ServiceError: Error {
    case badResponse
    case missingData
    case invalidCoordinate
    case invalidURL
}
