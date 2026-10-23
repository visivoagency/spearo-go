import Foundation

// ─────────────────────────────────────────────────────────────────────────────
// AuthClient.swift — Firebase Auth over REST, no Firebase SDK. Foundation-only:
// tools/spot-sync-harness compiles it (mode `auth`), so keep it that way.
//
// Twin of wear/.../sync/AuthClient.kt. The rules are pinned by the Kotlin
// AuthClientTest and the harness `auth` mode, and must stay identical:
//  - the ID token is refreshed when it expires within 5 minutes;
//  - a refresh answered with HTTP 400 means the grant is dead: the store is cleared;
//  - any other refresh failure (network, 5xx, bad body) returns nil and keeps the session;
//  - needConfirmation, FEDERATED_USER_ID_ALREADY_LINKED or EMAIL_EXISTS on sign-in
//    is .accountExistsWithDifferentMethod;
//  - one refresh at a time: concurrent callers share its result, success or failure;
//  - a refresh only saves or clears if the store still holds the session it started
//    from, so it can never undo a sign-out or overwrite/clear a newer sign-in.
// Never logs tokens, uid or email.
// ─────────────────────────────────────────────────────────────────────────────

/// The Firebase session Go holds after sign-in. `expiresAt` is epoch milliseconds.
struct Session: Codable, Equatable {
    let uid: String
    let idToken: String
    let refreshToken: String
    let expiresAt: Int64
    let email: String?
}

enum SignInResult: Equatable {
    case success(Session)
    case accountExistsWithDifferentMethod
    case cancelled
    case failed(String)
}

/// Where the session lives between launches (Keychain in the app: TokenStore.swift).
protocol TokenStore: AnyObject {
    func load() -> Session?
    func save(_ session: Session)
    func clear()
}

actor AuthClient {
    private static let refreshWindowMs: Int64 = 5 * 60_000

    private let http: URLSession
    private let baseIdentity: URL
    private let baseSecureToken: URL
    private nonisolated let store: TokenStore
    /// Guards every store write, so a refresh's check-then-write is atomic with
    /// sign-in and sign-out (signOut is nonisolated, so the actor alone can't).
    private nonisolated let gate = NSLock()
    private let clock: () -> Int64
    private let apiKey: String
    /// API-key restriction headers (`AuthClient.appHeaders()`); FirestoreClient sends the same.
    private let appHeaders: [String: String]
    private let log: (String) -> Void

    private var refreshInFlight: Task<String?, Never>?

    init(
        http: URLSession = .shared,
        baseIdentity: URL,
        baseSecureToken: URL,
        store: TokenStore,
        clock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        apiKey: String,
        appHeaders: [String: String] = [:],
        log: @escaping (String) -> Void = { print("[AuthClient] \($0)") }
    ) {
        self.http = http
        self.baseIdentity = baseIdentity
        self.baseSecureToken = baseSecureToken
        self.store = store
        self.clock = clock
        self.apiKey = apiKey
        self.appHeaders = appHeaders
        self.log = log
    }

    /// Headers that satisfy an iOS-restricted Google API key.
    static func appHeaders(bundle: Bundle = .main) -> [String: String] {
        guard let id = bundle.bundleIdentifier else { return [:] }
        return ["X-Ios-Bundle-Identifier": id]
    }

    // MARK: - Sign-in

    /// `providerId` is "google.com" or "apple.com". `nonce` is the raw (unhashed) nonce for Apple.
    func signInWithIdp(providerId: String, idToken: String, nonce: String?) async -> SignInResult {
        var postBody = "id_token=\(Self.formEncode(idToken))&providerId=\(Self.formEncode(providerId))"
        if let nonce { postBody += "&nonce=\(Self.formEncode(nonce))" }
        let payload: [String: Any] = [
            "postBody": postBody,
            "requestUri": "http://localhost",
            "returnSecureToken": true,
            "returnIdpCredential": true,
        ]
        var request = makeRequest(base: baseIdentity, path: "v1/accounts:signInWithIdp")
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        guard let (code, json) = await execute(request) else {
            log("signInWithIdp: network error")
            return .failed("No connection. Try again.")
        }

        if !(200...299).contains(code) {
            let message = Self.str(json?["error"] as? [String: Any], "message") ?? ""
            log("signInWithIdp: HTTP \(code) \(message.split(separator: " ").first ?? "")")
            if Self.isAccountExists(message) { return .accountExistsWithDifferentMethod }
            return .failed(message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Sign-in failed (HTTP \(code))" : message)
        }
        guard let json else { return .failed("Sign-in failed: unreadable response") }

        let errorMessage = Self.str(json, "errorMessage") ?? ""
        if Self.isTrue(json["needConfirmation"]) || Self.isAccountExists(errorMessage) {
            return .accountExistsWithDifferentMethod
        }
        if !errorMessage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return .failed(errorMessage) }

        guard let uid = Self.nonBlank(Self.str(json, "localId")),
              let firebaseIdToken = Self.nonBlank(Self.str(json, "idToken")),
              let refreshToken = Self.nonBlank(Self.str(json, "refreshToken")),
              let expiresIn = Self.str(json, "expiresIn").flatMap({ Int64($0) })
        else { return .failed("Sign-in failed: incomplete response") }

        let session = Session(
            uid: uid,
            idToken: firebaseIdToken,
            refreshToken: refreshToken,
            expiresAt: clock() + expiresIn * 1000,
            email: Self.nonBlank(Self.str(json, "email"))
        )
        gate.withLock { store.save(session) }
        return .success(session)
    }

    // MARK: - Session

    /// Valid ID token, refreshing if expiring within 5 min; nil and store cleared if refresh is rejected.
    func currentIdToken() async -> String? {
        if let inFlight = refreshInFlight { return await inFlight.value }
        guard let session = store.load() else { return nil }
        if isFresh(session) { return session.idToken }
        let task = Task { await self.refresh(session) }
        refreshInFlight = task
        let token = await task.value
        refreshInFlight = nil
        return token
    }

    nonisolated func session() -> Session? { store.load() }

    nonisolated func signOut() { gate.withLock { store.clear() } }

    private func isFresh(_ session: Session) -> Bool {
        clock() < session.expiresAt - Self.refreshWindowMs
    }

    /// Saves or clears only if the store still holds `started` (same refresh token).
    /// Otherwise someone signed out or in meanwhile: hand back their token if it is fresh.
    private func commitIfUnchanged(_ started: Session, _ write: () -> String?) -> String? {
        gate.withLock {
            let current = store.load()
            if current?.refreshToken != started.refreshToken {
                return current.flatMap { isFresh($0) ? $0.idToken : nil }
            }
            return write()
        }
    }

    private func refresh(_ session: Session) async -> String? {
        var request = makeRequest(base: baseSecureToken, path: "v1/token")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = Data(
            "grant_type=refresh_token&refresh_token=\(Self.formEncode(session.refreshToken))".utf8
        )

        guard let (code, json) = await execute(request) else {
            log("refresh: network error; keeping session")
            return nil
        }
        if code == 400 {
            log("refresh: rejected (HTTP 400); signing out")
            return commitIfUnchanged(session) { store.clear(); return nil }
        }
        if !(200...299).contains(code) {
            log("refresh: HTTP \(code); keeping session")
            return nil
        }
        guard let json,
              let idToken = Self.nonBlank(Self.str(json, "id_token")),
              let expiresIn = Self.str(json, "expires_in").flatMap({ Int64($0) })
        else { return nil }
        let refreshToken = Self.nonBlank(Self.str(json, "refresh_token")) ?? session.refreshToken
        let expiresAt = clock() + expiresIn * 1000
        return commitIfUnchanged(session) {
            store.save(Session(
                uid: session.uid,
                idToken: idToken,
                refreshToken: refreshToken,
                expiresAt: expiresAt,
                email: session.email
            ))
            return idToken
        }
    }

    // MARK: - HTTP

    private func makeRequest(base: URL, path: String) -> URLRequest {
        var components = URLComponents(url: base.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "key", value: apiKey)]
        var request = URLRequest(url: components.url!)
        request.httpMethod = "POST"
        for (name, value) in appHeaders { request.setValue(value, forHTTPHeaderField: name) }
        return request
    }

    /// (status, JSON object or nil). nil overall means the request never got an HTTP answer.
    private func execute(_ request: URLRequest) async -> (Int, [String: Any]?)? {
        do {
            let (data, response) = try await http.data(for: request)
            guard let http = response as? HTTPURLResponse else { return nil }
            let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            return (http.statusCode, json)
        } catch {
            return nil
        }
    }

    // MARK: - Helpers (lockstep with the Kotlin twin)

    /// application/x-www-form-urlencoded, as java.net.URLEncoder writes it.
    static func formEncode(_ value: String) -> String {
        let allowed = CharacterSet(charactersIn:
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._* ")
        return (value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value)
            .replacingOccurrences(of: " ", with: "+")
    }

    private static func isAccountExists(_ message: String) -> Bool {
        message.contains("FEDERATED_USER_ID_ALREADY_LINKED") || message.contains("EMAIL_EXISTS")
    }

    /// A JSON primitive as a string (Gson `asString`): strings as-is, numbers and booleans spelled out.
    private static func str(_ json: [String: Any]?, _ key: String) -> String? {
        guard let value = json?[key] else { return nil }
        if let s = value as? String { return s }
        if let n = value as? NSNumber {
            if CFGetTypeID(n) == CFBooleanGetTypeID() { return n.boolValue ? "true" : "false" }
            return n.stringValue
        }
        return nil
    }

    /// Only a real JSON `true` — not 1, not "true".
    private static func isTrue(_ value: Any?) -> Bool {
        guard let n = value as? NSNumber, CFGetTypeID(n) == CFBooleanGetTypeID() else { return false }
        return n.boolValue
    }

    private static func nonBlank(_ s: String?) -> String? {
        guard let s, !s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return s
    }
}
