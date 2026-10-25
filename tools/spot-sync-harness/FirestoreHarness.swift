import Foundation

// `firestore` mode: replays wear/.../sync/FirestoreClientTest against SpearoGo/Sync/FirestoreClient.swift,
// through the same StubProtocol as `auth` mode. Same inputs, same expectations.

private func query(_ req: URLRequest, _ name: String) -> [String] {
    URLComponents(url: req.url!, resolvingAgainstBaseURL: false)?.queryItems?
        .filter { $0.name == name }.compactMap { $0.value } ?? []
}

func runFirestore() -> Never {
    let config = URLSessionConfiguration.ephemeral
    config.protocolClasses = [StubProtocol.self]
    let http = URLSession(configuration: config)
    let base = URL(string: "https://stub.test/v1/projects/p/databases/(default)/documents")!
    let client = FirestoreClient(http: http, baseURL: base, appHeaders: ["X-Ios-Bundle-Identifier": "agency.example.go"])
    func names(_ docs: [[String: Any]]) -> [String] { docs.compactMap { $0["name"] as? String } }

    typealias Check = () async -> String?   // nil = pass, else the failure
    var cases: [(String, Check)] = []

    cases.append(("listFollowsNextPageToken", {
        StubProtocol.reset { req, _ in
            query(req, "pageToken").isEmpty
                ? .http(200, #"{"documents":[{"name":"a/1"},{"name":"a/2"}],"nextPageToken":"tok+2 /="}"#)
                : .http(200, #"{"documents":[{"name":"a/3"}]}"#)
        }
        guard let docs = try? await client.listUserSpots(uid: "U1", token: "T") else { return "threw" }
        if names(docs) != ["a/1", "a/2", "a/3"] { return "docs \(names(docs))" }
        let reqs = StubProtocol.lock.withLock { StubProtocol.requests.map { $0.0 } }
        if reqs.count != 2 { return "requests \(reqs.count)" }
        let first = reqs[0]
        if first.httpMethod != "GET" { return "method" }
        if !(first.url!.path.hasSuffix("/documents/users/U1/spots")) { return "path \(first.url!.path)" }
        if query(first, "pageSize") != ["300"] || !query(first, "pageToken").isEmpty { return "first query" }
        if first.value(forHTTPHeaderField: "Authorization") != "Bearer T" { return "auth header" }
        if first.value(forHTTPHeaderField: "X-Ios-Bundle-Identifier") != "agency.example.go" { return "app header" }
        // "+" must arrive encoded, or the server reads it as a space.
        if !(reqs[1].url!.absoluteString.contains("pageToken=tok%2B2")) { return "raw token \(reqs[1].url!.absoluteString)" }
        return query(reqs[1], "pageToken") == ["tok+2 /="] ? nil : "second token \(query(reqs[1], "pageToken"))"
    }))

    cases.append(("emptyCollectionIsEmptyList", {
        StubProtocol.reset { _, _ in .http(200, "{}") }
        guard let docs = try? await client.listUserSpots(uid: "U1", token: "T") else { return "threw" }
        return docs.isEmpty ? nil : "docs \(docs.count)"
    }))

    cases.append(("runQueryShapeAndSkipsElementsWithoutDocument", {
        StubProtocol.reset { _, _ in
            .http(200, #"[{"document":{"name":"s/1"},"readTime":"x"},{"readTime":"x"},{"document":{"name":"s/2"}}]"#)
        }
        guard let docs = try? await client.queryVisionSpots(uid: "U1", token: "T") else { return "threw" }
        if names(docs) != ["s/1", "s/2"] { return "docs \(names(docs))" }
        guard let (req, body) = StubProtocol.lock.withLock({ StubProtocol.requests.first }) else { return "no request" }
        if req.httpMethod != "POST" || !req.url!.absoluteString.hasSuffix("/documents:runQuery") { return "url \(req.url!)" }
        if req.value(forHTTPHeaderField: "Authorization") != "Bearer T" { return "auth header" }
        guard let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
              let q = json["structuredQuery"] as? [String: Any],
              let from = q["from"] as? [[String: Any]],
              let f = (q["where"] as? [String: Any])?["fieldFilter"] as? [String: Any] else { return "body shape" }
        if from.count != 1 || from[0]["collectionId"] as? String != "spots" { return "from" }
        if (f["field"] as? [String: Any])?["fieldPath"] as? String != "userId" { return "fieldPath" }
        if f["op"] as? String != "EQUAL" { return "op" }
        return (f["value"] as? [String: Any])?["stringValue"] as? String == "U1" ? nil : "value"
    }))

    cases.append(("patchSendsFieldsAndMask", {
        StubProtocol.reset { _, _ in .http(200, "{}") }
        let fields: [String: Any] = ["is_deleted": ["booleanValue": true]]
        do { try await client.patch(path: "users/U1/spots/D1", fields: fields, mask: ["is_deleted", "last_modified_at"], token: "T") }
        catch { return "threw \(error)" }
        guard let (req, body) = StubProtocol.lock.withLock({ StubProtocol.requests.first }) else { return "no request" }
        if req.httpMethod != "PATCH" || !req.url!.path.hasSuffix("/documents/users/U1/spots/D1") { return "url \(req.url!)" }
        if query(req, "updateMask.fieldPaths") != ["is_deleted", "last_modified_at"] { return "mask \(query(req, "updateMask.fieldPaths"))" }
        if req.value(forHTTPHeaderField: "X-Ios-Bundle-Identifier") != "agency.example.go" { return "app header" }
        guard let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
              let sent = json["fields"] as? NSDictionary else { return "body" }
        return sent.isEqual(to: fields) ? nil : "fields \(sent)"
    }))

    // Mirrors markerPatchOnlyUpdatesAnExistingDoc: the marker must never create users/{uid}.
    cases.append(("markerPatchOnlyUpdatesAnExistingDoc", {
        StubProtocol.reset { _, _ in .http(200, "{}") }
        do {
            try await client.patchMarker(uid: "U1", nowIso: "2026-10-23T12:00:00Z", token: "T")
        } catch { return "threw \(error)" }
        guard let (req, body) = StubProtocol.lock.withLock({ StubProtocol.requests.first }) else { return "no request" }
        if req.httpMethod != "PATCH" { return "method" }
        if !req.url!.path.hasSuffix("/documents/users/U1") { return "path \(req.url!.path)" }
        if query(req, "updateMask.fieldPaths") != ["apps.go.last_seen_at"] { return "mask" }
        if query(req, "currentDocument.exists") != ["true"] { return "precondition \(query(req, "currentDocument.exists"))" }
        let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any]
        let apps = (json?["fields"] as? [String: Any])?["apps"] as? [String: Any]
        let go = ((apps?["mapValue"] as? [String: Any])?["fields"] as? [String: Any])?["go"] as? [String: Any]
        let seen = ((go?["mapValue"] as? [String: Any])?["fields"] as? [String: Any])?["last_seen_at"] as? [String: Any]
        return seen?["timestampValue"] as? String == "2026-10-23T12:00:00Z" ? nil : "body \(String(describing: json))"
    }))

    cases.append(("spotPatchHasNoExistsPrecondition", {
        StubProtocol.reset { _, _ in .http(200, "{}") }
        do { try await client.patch(path: "users/U1/spots/D1", fields: [:], mask: ["name"], token: "T") }
        catch { return "threw \(error)" }
        guard let (req, _) = StubProtocol.lock.withLock({ StubProtocol.requests.first }) else { return "no request" }
        return query(req, "currentDocument.exists").isEmpty ? nil : "precondition sent"
    }))

    cases.append(("displayNameReadsOnlyThatField", {
        StubProtocol.reset { _, _ in .http(200, #"{"fields":{"display_name":{"stringValue":"  Kevin G "}}}"#) }
        let name: String?
        do { name = try await client.getDisplayName(uid: "U1", token: "T") } catch { return "threw \(error)" }
        guard let (req, _) = StubProtocol.lock.withLock({ StubProtocol.requests.first }) else { return "no request" }
        if req.httpMethod != "GET" || !req.url!.path.hasSuffix("/documents/users/U1") { return "url \(req.url!)" }
        if query(req, "mask.fieldPaths") != ["display_name"] { return "mask \(query(req, "mask.fieldPaths"))" }
        return name == "Kevin G" ? nil : "name \(String(describing: name))"
    }))

    cases.append(("displayNameAbsentOrBlankIsNil", {
        let blank: [String: Any] = ["fields": ["display_name": ["stringValue": "  "]]]
        let wrongType: [String: Any] = ["fields": ["display_name": ["integerValue": "3"]]]
        let none: [String: Any] = ["fields": [:] as [String: Any]]
        for doc in [blank, wrongType, none] where FirestoreClient.displayName(from: doc) != nil { return "not nil for \(doc)" }
        return FirestoreClient.displayName(from: nil) == nil ? nil : "not nil for nil"
    }))

    cases.append(("httpErrorThrows", {
        StubProtocol.reset { _, _ in .http(403, #"{"error":{"code":403}}"#) }
        do { _ = try await client.listUserSpots(uid: "U1", token: "T"); return "did not throw" }
        catch let e as FirestoreError { return e.code == 403 ? nil : "code \(String(describing: e.code))" }
        catch { return "wrong error \(error)" }
    }))

    cases.append(("networkErrorThrows", {
        StubProtocol.reset { _, _ in .network }
        do { _ = try await client.queryVisionSpots(uid: "U1", token: "T"); return "did not throw" }
        catch let e as FirestoreError { return e.code == nil ? nil : "code \(String(describing: e.code))" }
        catch { return "wrong error \(error)" }
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
