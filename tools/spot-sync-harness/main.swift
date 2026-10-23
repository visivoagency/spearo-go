import Foundation

// Runs the Swift planSync against fixtures/spot-sync/merge-cases.json (path in argv[1]).
// Build (explicit list; every SpearoGo file here must stay Foundation-only):
//   swiftc -o <out> SpearoGo/Sync/SpotMerge.swift SpearoGo/Sync/SpotCodec.swift SpearoGo/Sync/AuthClient.swift \
//     SpearoGo/Sync/FirestoreClient.swift tools/spot-sync-harness/AuthHarness.swift \
//     tools/spot-sync-harness/FirestoreHarness.swift tools/spot-sync-harness/main.swift
// Modes: `<out> <merge-cases.json>` (or `merge <file>`), `<out> codec <codec-cases.json>`,
// `<out> auth` (AuthClient against a URLProtocol stub; mirrors AuthClientTest.kt)
// and `<out> firestore` (FirestoreClient against the same stub; mirrors FirestoreClientTest.kt).
// Same newId rule as the Kotlin test: returns "NEW", compared as "new:<cloudId>".

struct Expect: Decodable {
    let upsertLocal: [String], deleteLocal: [String], pushUpsert: [String], pushDelete: [String]
}
struct Case: Decodable {
    let name: String
    let local: [LocalSpot]
    let remote: [RemoteSpot]
    let expect: Expect
}

guard CommandLine.arguments.count > 1 else {
    FileHandle.standardError.write(Data("usage: spot-harness [merge] <merge-cases.json> | codec <codec-cases.json> | auth | firestore\n".utf8))
    exit(2)
}
if CommandLine.arguments[1] == "auth" { runAuth() }
if CommandLine.arguments[1] == "firestore" { runFirestore() }

// MARK: codec mode. Docs live in <cases dir>/docs/<file>.json; dates compare at millisecond precision.
struct CodecExpect: Decodable {
    let docId: String, origin: SpotOrigin, name: String, latitude: Double, longitude: Double
    let createdAt: Int64?, lastModifiedAt: Int64?, isDeleted: Bool, camelCase: Bool
}
struct CodecCase: Decodable { let file: String; let origin: SpotOrigin; let expect: CodecExpect? }

func ms(_ d: Date?) -> Int64? { d.map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) } }

func runCodec(_ path: String) -> Never {
    let url = URL(fileURLWithPath: path)
    let cases: [CodecCase]
    do { cases = try JSONDecoder().decode([CodecCase].self, from: Data(contentsOf: url)) }
    catch { print("FAIL decode: \(error)"); exit(2) }
    if cases.isEmpty { print("FAIL no cases"); exit(1) }
    var failures = 0
    for c in cases {
        let docURL = url.deletingLastPathComponent().appendingPathComponent("docs/\(c.file).json")
        guard let data = try? Data(contentsOf: docURL),
              let doc = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            failures += 1; print("FAIL \(c.file): cannot read doc"); continue
        }
        let got = SpotCodec.decode(doc, origin: c.origin)
        var diffs: [String] = []
        if let e = c.expect {
            if let g = got {
                if g.docId != e.docId { diffs.append("docId \(g.docId)") }
                if g.origin != e.origin { diffs.append("origin") }
                if g.name != e.name { diffs.append("name \(g.name)") }
                if g.latitude != e.latitude { diffs.append("latitude \(g.latitude)") }
                if g.longitude != e.longitude { diffs.append("longitude \(g.longitude)") }
                if ms(g.createdAt) != e.createdAt { diffs.append("createdAt \(String(describing: ms(g.createdAt)))") }
                if ms(g.lastModifiedAt) != e.lastModifiedAt { diffs.append("lastModifiedAt \(String(describing: ms(g.lastModifiedAt)))") }
                if g.isDeleted != e.isDeleted { diffs.append("isDeleted \(g.isDeleted)") }
                if g.camelCase != e.camelCase { diffs.append("camelCase \(g.camelCase)") }
            } else { diffs.append("got nil") }
        } else if got != nil { diffs.append("want nil") }
        if diffs.isEmpty { print("PASS \(c.file)") }
        else { failures += 1; print("FAIL \(c.file): \(diffs.joined(separator: "; "))") }
    }
    // Encode checks: same expectations as SpotCodecTest.
    let spot = LocalSpot(id: "g1", cloudId: nil, name: "Reef", latitude: 37.5, longitude: -8.25,
        createdAt: Date(timeIntervalSince1970: 1_700_000_000), lastModifiedAt: Date(timeIntervalSince1970: 1_700_000_100),
        isDeleted: false, pendingSync: true, origin: .go)
    let enc = SpotCodec.encodeUpsert(spot, uid: "U1", nowIso: "2026-10-23T12:00:00Z")
    let wantKeys: Set<String> = ["user_id", "name", "latitude", "longitude", "is_private", "is_deleted",
        "created_at", "last_modified_at", "last_synced_at", "cloud_id", "source", "sync_status"]
    func sv(_ k: String) -> String? { (enc[k] as? [String: Any])?["stringValue"] as? String }
    let encOK = Set(enc.keys) == wantKeys && sv("created_at") == "2023-11-14T22:13:20Z"
        && sv("last_modified_at") == "2023-11-14T22:15:00Z" && sv("cloud_id") == "g1" && sv("source") == "go"
    print(encOK ? "PASS encodeUpsert" : "FAIL encodeUpsert"); if !encOK { failures += 1 }
    func rem(_ camel: Bool) -> RemoteSpot {
        RemoteSpot(docId: "d", origin: .account, name: "n", latitude: 1, longitude: 2,
                   createdAt: nil, lastModifiedAt: nil, isDeleted: false, camelCase: camel)
    }
    let c1 = SpotCodec.encodeSoftDelete(rem(true), nowIso: "T"), s1 = SpotCodec.encodeSoftDelete(rem(false), nowIso: "T")
    let delOK = c1.mask == ["isDeleted", "lastModifiedAt"] && Set(c1.fields.keys) == Set(c1.mask)
        && s1.mask == ["is_deleted", "last_modified_at"] && Set(s1.fields.keys) == Set(s1.mask)
    print(delOK ? "PASS encodeSoftDelete" : "FAIL encodeSoftDelete"); if !delOK { failures += 1 }
    print("\(cases.count + 2 - failures)/\(cases.count + 2) passed")
    exit(failures == 0 ? 0 : 1)
}
if CommandLine.arguments[1] == "codec" {
    guard CommandLine.arguments.count > 2 else { print("usage: codec <codec-cases.json>"); exit(2) }
    runCodec(CommandLine.arguments[2])
}
let mergePath = CommandLine.arguments[1] == "merge" && CommandLine.arguments.count > 2
    ? CommandLine.arguments[2] : CommandLine.arguments[1]
let decoder = JSONDecoder()
decoder.dateDecodingStrategy = .millisecondsSince1970
let cases: [Case]
do {
    let data = try Data(contentsOf: URL(fileURLWithPath: mergePath))
    cases = try decoder.decode([Case].self, from: data)
} catch {
    print("FAIL decode: \(error)")
    exit(2)
}
if cases.isEmpty { print("FAIL no cases"); exit(1) }

var failures = 0
for c in cases {
    let plan = planSync(local: c.local, remote: c.remote, newId: { "NEW" })
    let up = plan.upsertLocal.map { $0.id == "NEW" ? "new:\($0.cloudId ?? "")" : $0.id }
    var diffs: [String] = []
    func check(_ label: String, _ want: [String], _ got: [String]) {
        if Set(want) != Set(got) || want.count != got.count {
            diffs.append("\(label) want \(want.sorted()) got \(got.sorted())")
        }
    }
    check("upsertLocal", c.expect.upsertLocal, up)
    check("deleteLocal", c.expect.deleteLocal, plan.deleteLocal)
    check("pushUpsert", c.expect.pushUpsert, plan.pushUpsert.map { $0.id })
    check("pushDelete", c.expect.pushDelete, plan.pushDelete.map { $0.docId })
    if diffs.isEmpty { print("PASS \(c.name)") }
    else { failures += 1; print("FAIL \(c.name): \(diffs.joined(separator: "; "))") }
}
print("\(cases.count - failures)/\(cases.count) passed")
exit(failures == 0 ? 0 : 1)
