import Foundation
import CoreLocation

struct MarineService {
    // Open-Meteo marine API — free, no key, global coverage
    // Docs: https://marine-api.open-meteo.com/v1/marine

    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        return URLSession(configuration: config)
    }()

    func fetch(coordinate: CLLocationCoordinate2D) async throws -> MarineData {
        let url = try buildURL(coordinate: coordinate)
        let (data, response) = try await session.data(from: url)

        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw ServiceError.badResponse
        }

        let decoded = try JSONDecoder().decode(OpenMeteoMarineResponse.self, from: data)
        guard let current = decoded.current else { throw ServiceError.missingData }

        return MarineData(
            waveHeight:    current.wave_height ?? 0,
            wavePeriod:    current.wave_period ?? 0,
            waveDirection: current.wave_direction ?? 0,
            seaSurfaceTemp: current.sea_surface_temperature ?? 20,
            fetchedAt:     Date()
        )
    }

    private func buildURL(coordinate: CLLocationCoordinate2D) throws -> URL {
        guard var components = URLComponents(string: Constants.API.marineBase) else {
            throw ServiceError.invalidURL
        }
        components.queryItems = [
            .init(name: "latitude",  value: String(coordinate.latitude)),
            .init(name: "longitude", value: String(coordinate.longitude)),
            .init(name: "current",   value: "wave_height,wave_period,wave_direction,sea_surface_temperature"),
            .init(name: "timezone",  value: "auto")
        ]
        guard let url = components.url else {
            throw ServiceError.invalidURL
        }
        return url
    }
}

// MARK: - Response shapes

private struct OpenMeteoMarineResponse: Decodable {
    let current: CurrentMarine?
}

private struct CurrentMarine: Decodable {
    let wave_height:             Double?
    let wave_period:             Double?
    let wave_direction:          Double?
    let sea_surface_temperature: Double?
}
