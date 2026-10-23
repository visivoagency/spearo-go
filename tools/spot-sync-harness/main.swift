import Foundation

// Runs the Swift planSync against fixtures/spot-sync/merge-cases.json (path in argv[1]).
// Build: swiftc -o <out> SpearoGo/Sync/SpotMerge.swift tools/spot-sync-harness/main.swift
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
    FileHandle.standardError.write(Data("usage: spot-harness <merge-cases.json>\n".utf8))
    exit(2)
}
let decoder = JSONDecoder()
decoder.dateDecodingStrategy = .millisecondsSince1970
let cases: [Case]
do {
    let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
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
