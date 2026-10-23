import Foundation

// ─────────────────────────────────────────────────────────────────────────────
// FirestoreClient.swift — Firestore over REST (the Firestore SDK has no watchOS).
// Foundation-only: tools/spot-sync-harness compiles it (mode `firestore`).
// Twin of wear/.../sync/FirestoreClient.kt; FirestoreClientTest pins the same cases.
//
// `baseURL` is the `.../databases/(default)/documents` root, injected so the
// harness can stub it. Every request carries the ID token and the same API-key
// restriction headers as AuthClient (`AuthClient.appHeaders()`).
// Errors carry status codes only, never paths (they contain the uid).
// ─────────────────────────────────────────────────────────────────────────────

/// A Firestore call that did not succeed. `code` is the HTTP status, or nil for no answer / an unreadable one.
struct FirestoreError: Error, CustomStringConvertible {
    let code: Int?
    let description: String
}

final class FirestoreClient: @unchecked Sendable {
    static let pageSize = 300
    /// `updateMask.fieldPaths` for the "Go connected" marker on `users/{uid}`.
    static let markerPath = "apps.go.last_seen_at"

    private let http: URLSession
    private let baseURL: URL
    private let appHeaders: [String: String]

    init(http: URLSession = .shared, baseURL: URL, appHeaders: [String: String] = [:]) {
        self.http = http
        self.baseURL = baseURL
        self.appHeaders = appHeaders
    }

    /// `users/{uid}/spots`, every page (pageSize=300, following nextPageToken).
    func listUserSpots(uid: String, token: String) async throws -> [[String: Any]] {
        var out: [[String: Any]] = []
        var pageToken: String?
        repeat {
            var items = [URLQueryItem(name: "pageSize", value: String(Self.pageSize))]
            if let pageToken { items.append(URLQueryItem(name: "pageToken", value: pageToken)) }
            let request = makeRequest(url: docURL("users/\(uid)/spots", query: items), method: "GET", token: token)
            guard let page = try await send(request) as? [String: Any] else {
                throw FirestoreError(code: nil, description: "list: unreadable response")
            }
            for d in (page["documents"] as? [Any]) ?? [] { if let doc = d as? [String: Any] { out.append(doc) } }
            pageToken = (page["nextPageToken"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        } while pageToken != nil
        return out
    }

    /// Vision's top-level `spots` where `userId == uid`, via `:runQuery`.
    func queryVisionSpots(uid: String, token: String) async throws -> [[String: Any]] {
        let body: [String: Any] = [
            "structuredQuery": [
                "from": [["collectionId": "spots"]],
                "where": ["fieldFilter": [
                    "field": ["fieldPath": "userId"],
                    "op": "EQUAL",
                    "value": ["stringValue": uid],
                ]],
            ],
        ]
        let url = URL(string: baseURL.absoluteString + ":runQuery")!
        var request = makeRequest(url: url, method: "POST", token: token)
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        guard let results = try await send(request) as? [Any] else {
            throw FirestoreError(code: nil, description: "runQuery: unreadable response")
        }
        return results.compactMap { ($0 as? [String: Any])?["document"] as? [String: Any] }
    }

    /// PATCH `{base}/{path}` with `fields`; with a `mask`, only those field paths are written.
    /// `mustExist` adds `currentDocument.exists=true`: the write fails (404 / FAILED_PRECONDITION)
    /// instead of creating the doc.
    func patch(path: String, fields: [String: Any], mask: [String]?, token: String, mustExist: Bool = false) async throws {
        var items = (mask ?? []).map { URLQueryItem(name: "updateMask.fieldPaths", value: $0) }
        if mustExist { items.append(URLQueryItem(name: "currentDocument.exists", value: "true")) }
        var request = makeRequest(url: docURL(path, query: items), method: "PATCH", token: token)
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["fields": fields])
        _ = try await send(request, parse: false)
    }

    /// The "Go connected" marker. Only ever UPDATES `users/{uid}`: the website creates that doc with
    /// the profile fields its rules require, and a marker-only doc would block that creation.
    func patchMarker(uid: String, nowIso: String, token: String) async throws {
        try await patch(path: "users/\(uid)", fields: Self.markerFields(nowIso: nowIso), mask: [Self.markerPath],
                        token: token, mustExist: true)
    }

    /// `fields` for PATCH users/{uid}?updateMask.fieldPaths=`markerPath`.
    static func markerFields(nowIso: String) -> [String: Any] {
        ["apps": ["mapValue": ["fields": [
            "go": ["mapValue": ["fields": [
                "last_seen_at": ["timestampValue": nowIso],
            ]]],
        ]]]]
    }

    // MARK: - HTTP

    private func docURL(_ path: String, query: [URLQueryItem]) -> URL {
        var url = baseURL
        for segment in path.split(separator: "/") { url.appendPathComponent(String(segment)) }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        // Percent-encode values ourselves: URLComponents leaves "+" bare, which servers read as a space.
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "+&=")
        if !query.isEmpty {
            components.percentEncodedQueryItems = query.map {
                URLQueryItem(name: $0.name, value: $0.value?.addingPercentEncoding(withAllowedCharacters: allowed))
            }
        }
        return components.url!
    }

    private func makeRequest(url: URL, method: String, token: String) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        for (name, value) in appHeaders { request.setValue(value, forHTTPHeaderField: name) }
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if method != "GET" { request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type") }
        return request
    }

    private func send(_ request: URLRequest, parse: Bool = true) async throws -> Any? {
        let data: Data, response: URLResponse
        do { (data, response) = try await http.data(for: request) }
        catch { throw FirestoreError(code: nil, description: "network error") }
        guard let http = response as? HTTPURLResponse else { throw FirestoreError(code: nil, description: "no HTTP answer") }
        guard (200...299).contains(http.statusCode) else {
            throw FirestoreError(code: http.statusCode, description: "HTTP \(http.statusCode)")
        }
        return parse ? try? JSONSerialization.jsonObject(with: data) : nil
    }
}
