import Foundation

/// Place-name search, for adding a dive spot the diver is not standing at.
///
/// Open-Meteo rather than `CLGeocoder`, even though CoreLocation would work.
/// The two platforms' native geocoders are different services with different
/// results, so the Wear and watchOS apps would disagree about what "Lagos"
/// means. One provider answers both identically and can be pinned in a test
/// fixture. No key, and the same provider already used for weather.
struct GeocodingService {
    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.timeoutIntervalForResource = 30
        return URLSession(configuration: config)
    }()

    /// An empty result is a normal answer — "no places found", not a failure.
    func search(_ query: String) async throws -> [GeocodedPlace] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        guard var components = URLComponents(string: Constants.API.geocodingBase) else {
            throw ServiceError.invalidURL
        }
        components.queryItems = [
            .init(name: "name", value: trimmed),
            .init(name: "count", value: "8"),
            .init(name: "language", value: "en"),
            .init(name: "format", value: "json"),
        ]
        guard let url = components.url else { throw ServiceError.invalidURL }

        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw ServiceError.badResponse
        }
        return Self.parse(try JSONDecoder().decode(GeocodingResponse.self, from: data))
    }

    /// Split out so it can be tested against a recorded response.
    static func parse(_ response: GeocodingResponse) -> [GeocodedPlace] {
        (response.results ?? []).compactMap { r in
            guard let lat = r.latitude, let lon = r.longitude,
                  let name = r.name, !name.isEmpty else { return nil }
            // 0,0 is a real place in the Atlantic. A missing coordinate must
            // never become one — the same rule the tide and marine parsing
            // follow.
            return GeocodedPlace(
                id: "\(name)-\(lat)-\(lon)",
                name: name,
                region: r.admin1?.isEmpty == false ? r.admin1 : nil,
                country: r.country_code?.isEmpty == false ? r.country_code : nil,
                latitude: lat,
                longitude: lon
            )
        }
    }
}

struct GeocodingResponse: Decodable {
    let results: [GeocodingResult]?
}

struct GeocodingResult: Decodable {
    let name: String?
    let admin1: String?
    let country_code: String?
    let latitude: Double?
    let longitude: Double?
}
