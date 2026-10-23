import Foundation

// `auth` mode: replays wear/.../sync/AuthClientTest against SpearoGo/Sync/AuthClient.swift.
// HTTP goes through StubProtocol (a URLProtocol), the store is in memory and the clock is fixed,
// so every case is the Kotlin case with the same inputs and the same expectations.

final class MemoryStore: TokenStore {
    private let lock = NSLock()
    private var stored: Session?
    init(_ s: Session? = nil) { stored = s }
    func load() -> Session? { lock.withLock { stored } }
    func save(_ session: Session) { lock.withLock { stored = session } }
    func clear() { lock.withLock { stored = nil } }
}

enum StubReply {
    case http(Int, String)
    case network
}

/// Every request goes to `handler`, which may be async (to act mid-request) and may delay.
final class StubProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest, Data) async -> StubReply)?
    nonisolated(unsafe) static var requests: [(URLRequest, Data)] = []
    static let lock = NSLock()

    static func reset(_ h: @escaping (URLRequest, Data) async -> StubReply) {
        lock.withLock { handler = h; requests = [] }
    }
    static var count: Int { lock.withLock { requests.count } }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let body = Self.readBody(request)
        let handler = Self.lock.withLock { () -> ((URLRequest, Data) async -> StubReply)? in
            Self.requests.append((request, body)); return Self.handler
        }
        let request = self.request
        Task {
            let reply = await handler?(request, body) ?? .network
            switch reply {
            case .network:
                self.client?.urlProtocol(self, didFailWithError: URLError(.networkConnectionLost))
            case let .http(code, text):
                let resp = HTTPURLResponse(url: request.url!, statusCode: code, httpVersion: "HTTP/1.1",
                                           headerFields: ["Content-Type": "application/json"])!
                self.client?.urlProtocol(self, didReceive: resp, cacheStoragePolicy: .notAllowed)
                self.client?.urlProtocol(self, didLoad: Data(text.utf8))
                self.client?.urlProtocolDidFinishLoading(self)
            }
        }
    }

    override func stopLoading() {}

    private static func readBody(_ r: URLRequest) -> Data {
        if let d = r.httpBody { return d }
        guard let stream = r.httpBodyStream else { return Data() }
        stream.open(); defer { stream.close() }
        var data = Data()
        var buf = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let n = stream.read(&buf, maxLength: buf.count)
            if n <= 0 { break }
            data.append(buf, count: n)
        }
        return data
    }
}

private func formFields(_ s: String) -> [String: String] {
    var out: [String: String] = [:]
    for pair in s.split(separator: "&") {
        let kv = pair.split(separator: "=", maxSplits: 1).map(String.init)
        guard kv.count == 2 else { continue }
        out[kv[0]] = kv[1].replacingOccurrences(of: "+", with: " ").removingPercentEncoding
    }
    return out
}

func runAuth() -> Never {
    let now: Int64 = 1_000_000_000_000
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [StubProtocol.self]
    let http = URLSession(configuration: config)

    let okSignIn = #"{"localId":"uid-1","idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600","email":"diver@example.com"}"#
    let okRefresh = #"{"id_token":"new-id","refresh_token":"refresh-2","expires_in":"3600","user_id":"uid-1"}"#
    func stored(_ expiresAt: Int64) -> Session {
        Session(uid: "uid-1", idToken: "old-id", refreshToken: "refresh-1", expiresAt: expiresAt, email: nil)
    }
    func client(_ store: MemoryStore) -> AuthClient {
        AuthClient(http: http,
                   baseIdentity: URL(string: "https://stub.test/identity")!,
                   baseSecureToken: URL(string: "https://stub.test/securetoken")!,
                   store: store, clock: { now }, apiKey: "test-key",
                   appHeaders: ["X-Ios-Bundle-Identifier": "agency.example.go"], log: { _ in })
    }

    typealias Check = () async -> String?   // nil = pass, else the failure
    var cases: [(String, Check)] = []

    cases.append(("successStoresSession", {
        let store = MemoryStore()
        StubProtocol.reset { _, _ in .http(200, okSignIn) }
        let r = await client(store).signInWithIdp(providerId: "google.com", idToken: "google-id-token", nonce: nil)
        let want = Session(uid: "uid-1", idToken: "id-1", refreshToken: "refresh-1",
                           expiresAt: now + 3_600_000, email: "diver@example.com")
        if r != .success(want) { return "result \(r)" }
        return store.load() == want ? nil : "store \(String(describing: store.load()))"
    }))

    cases.append(("signInRequestShape", {
        StubProtocol.reset { _, _ in .http(200, okSignIn) }
        _ = await client(MemoryStore()).signInWithIdp(providerId: "apple.com", idToken: "apple+token/=", nonce: "raw-nonce")
        guard let (req, body) = StubProtocol.lock.withLock({ StubProtocol.requests.first }) else { return "no request" }
        if req.httpMethod != "POST" { return "method" }
        if req.url?.path != "/identity/v1/accounts:signInWithIdp" || req.url?.query != "key=test-key" {
            return "url \(req.url?.absoluteString ?? "")"
        }
        if req.value(forHTTPHeaderField: "X-Ios-Bundle-Identifier") != "agency.example.go" { return "header" }
        guard let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] else { return "body" }
        if json["requestUri"] as? String != "http://localhost" { return "requestUri" }
        if json["returnSecureToken"] as? Bool != true || json["returnIdpCredential"] as? Bool != true { return "flags" }
        let post = formFields(json["postBody"] as? String ?? "")
        return post == ["id_token": "apple+token/=", "providerId": "apple.com", "nonce": "raw-nonce"] ? nil : "postBody \(post)"
    }))

    cases.append(("needConfirmationIsAccountExists", {
        let store = MemoryStore()
        StubProtocol.reset { _, _ in .http(200, #"{"needConfirmation":true,"email":"diver@example.com"}"#) }
        let r = await client(store).signInWithIdp(providerId: "google.com", idToken: "t", nonce: nil)
        if r != .accountExistsWithDifferentMethod { return "result \(r)" }
        return store.load() == nil ? nil : "stored a session"
    }))

    cases.append(("emailExistsIsAccountExists", {
        let store = MemoryStore()
        StubProtocol.reset { _, _ in .http(400, #"{"error":{"code":400,"message":"EMAIL_EXISTS"}}"#) }
        let r1 = await client(store).signInWithIdp(providerId: "apple.com", idToken: "t", nonce: "n")
        StubProtocol.reset { _, _ in .http(200, #"{"errorMessage":"FEDERATED_USER_ID_ALREADY_LINKED"}"#) }
        let r2 = await client(store).signInWithIdp(providerId: "apple.com", idToken: "t", nonce: "n")
        if r1 != .accountExistsWithDifferentMethod || r2 != .accountExistsWithDifferentMethod { return "\(r1) / \(r2)" }
        return store.load() == nil ? nil : "stored a session"
    }))

    cases.append(("otherErrorIsFailedAndKeepsNoSession", {
        let store = MemoryStore()
        let plain = SignInResult.failed("Sign-in failed. Try again.")
        StubProtocol.reset { _, _ in .http(400, #"{"error":{"code":400,"message":"INVALID_IDP_RESPONSE : Invalid id_token"}}"#) }
        let r = await client(store).signInWithIdp(providerId: "google.com", idToken: "t", nonce: nil)
        if r != plain { return "result \(r)" }   // plain copy, never the raw Firebase code
        if store.load() != nil { return "stored a session" }
        StubProtocol.reset { _, _ in .http(200, #"{"errorMessage":"INVALID_IDP_RESPONSE : bad"}"#) }
        let r2 = await client(store).signInWithIdp(providerId: "google.com", idToken: "t", nonce: nil)
        if r2 != plain { return "errorMessage result \(r2)" }
        StubProtocol.reset { _, _ in .http(503, "") }
        let r3 = await client(store).signInWithIdp(providerId: "google.com", idToken: "t", nonce: nil)
        return r3 == plain ? nil : "503 result \(r3)"
    }))

    cases.append(("freshTokenNeedsNoNetwork", {
        StubProtocol.reset { _, _ in .network }
        let token = await client(MemoryStore(stored(now + 6 * 60_000))).currentIdToken()
        if token != "old-id" { return "token \(String(describing: token))" }
        return StubProtocol.count == 0 ? nil : "made \(StubProtocol.count) requests"
    }))

    cases.append(("refreshesWhenExpiring", {
        let store = MemoryStore(stored(now + 60_000))
        StubProtocol.reset { _, _ in .http(200, okRefresh) }
        let token = await client(store).currentIdToken()
        if token != "new-id" { return "token \(String(describing: token))" }
        if StubProtocol.count != 1 { return "requests \(StubProtocol.count)" }
        let (req, body) = StubProtocol.lock.withLock { StubProtocol.requests[0] }
        if req.url?.path != "/securetoken/v1/token" || req.url?.query != "key=test-key" { return "url" }
        if req.value(forHTTPHeaderField: "X-Ios-Bundle-Identifier") != "agency.example.go" { return "header" }
        let form = formFields(String(decoding: body, as: UTF8.self))
        if form["grant_type"] != "refresh_token" || form["refresh_token"] != "refresh-1" { return "form \(form)" }
        let want = Session(uid: "uid-1", idToken: "new-id", refreshToken: "refresh-2", expiresAt: now + 3_600_000, email: nil)
        return store.load() == want ? nil : "store \(String(describing: store.load()))"
    }))

    cases.append(("refreshFailureSignsOut", {
        let c = client(MemoryStore(stored(now + 60_000)))
        StubProtocol.reset { _, _ in .http(400, #"{"error":{"code":400,"message":"TOKEN_EXPIRED"}}"#) }
        if let t = await c.currentIdToken() { return "token \(t)" }
        return c.session() == nil ? nil : "still signed in"
    }))

    cases.append(("networkErrorKeepsSession", {
        let c = client(MemoryStore(stored(now + 60_000)))
        StubProtocol.reset { _, _ in .network }
        if let t = await c.currentIdToken() { return "token \(t)" }
        return c.session() != nil ? nil : "signed out"
    }))

    cases.append(("serverErrorKeepsSession", {
        let c = client(MemoryStore(stored(now + 60_000)))
        StubProtocol.reset { _, _ in .http(503, #"{"error":{"code":503,"message":"UNAVAILABLE"}}"#) }
        if let t = await c.currentIdToken() { return "token \(t)" }
        return c.session() != nil ? nil : "signed out"
    }))

    cases.append(("signOutDuringRefreshStaysSignedOut", {
        let c = client(MemoryStore(stored(now + 60_000)))
        StubProtocol.reset { _, _ in c.signOut(); return .http(200, okRefresh) }
        if let t = await c.currentIdToken() { return "token \(t)" }
        return c.session() == nil ? nil : "sign-out undone: \(String(describing: c.session()))"
    }))

    cases.append(("signInDuringRejectedRefreshSurvives", {
        let c = client(MemoryStore(stored(now + 60_000)))
        let newSignIn = #"{"localId":"uid-2","idToken":"id-new","refreshToken":"refresh-new","expiresIn":"3600"}"#
        StubProtocol.reset { req, _ in
            if req.url!.path.hasPrefix("/identity") { return .http(200, newSignIn) }
            // A sign-in completes while the refresh is on the wire; then the old grant is rejected.
            _ = await c.signInWithIdp(providerId: "google.com", idToken: "t", nonce: nil)
            return .http(400, #"{"error":{"code":400,"message":"TOKEN_EXPIRED"}}"#)
        }
        let token = await c.currentIdToken()
        if token != "id-new" { return "token \(String(describing: token))" }
        let want = Session(uid: "uid-2", idToken: "id-new", refreshToken: "refresh-new", expiresAt: now + 3_600_000, email: nil)
        return c.session() == want ? nil : "session \(String(describing: c.session()))"
    }))

    cases.append(("concurrentCallersShareOneRefresh", {
        let c = client(MemoryStore(stored(now + 60_000)))
        StubProtocol.reset { _, _ in try? await Task.sleep(nanoseconds: 200_000_000); return .http(200, okRefresh) }
        async let a = c.currentIdToken()
        async let b = c.currentIdToken()
        let tokens = await [a, b]
        if tokens != ["new-id", "new-id"] { return "tokens \(tokens)" }
        return StubProtocol.count == 1 ? nil : "requests \(StubProtocol.count)"
    }))

    cases.append(("concurrentCallersShareOneFailedRefresh", {
        let c = client(MemoryStore(stored(now + 60_000)))
        StubProtocol.reset { _, _ in try? await Task.sleep(nanoseconds: 200_000_000); return .network }
        async let a = c.currentIdToken()
        async let b = c.currentIdToken()
        let tokens = await [a, b]
        if tokens != [nil, nil] { return "tokens \(tokens)" }
        if StubProtocol.count != 1 { return "requests \(StubProtocol.count)" }
        return c.session() != nil ? nil : "signed out"
    }))

    cases.append(("signOutClearsStore", {
        let c = client(MemoryStore(stored(now + 60_000)))
        c.signOut()
        return c.session() == nil ? nil : "still signed in"
    }))

    let done = DispatchSemaphore(value: 0)
    var failures = 0
    Task.detached {
        for (name, check) in cases {
            if let why = await check() { failures += 1; print("FAIL \(name): \(why)") }
            else { print("PASS \(name)") }
        }
        done.signal()
    }
    done.wait()
    print("\(cases.count - failures)/\(cases.count) passed")
    exit(failures == 0 ? 0 : 1)
}
