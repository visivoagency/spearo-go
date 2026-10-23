import Foundation

// Firestore REST `Document` <-> merge types. Foundation only: compiled standalone by
// tools/spot-sync-harness. Twin of wear/.../sync/SpotCodec.kt. Keep the two in lockstep.
// Reads tolerate every convention (camel/snake keys, timestampValue/ISO stringValue,
// doubleValue/integerValue); writes are the website's snake_case shape only.

enum SpotCodec {
    private static let camelMarkers: Set<String> = ["isDeleted", "lastModifiedAt", "createdAt", "userId"]

    private static func value(_ fields: [String: Any], _ keys: [String]) -> [String: Any]? {
        for k in keys { if let v = fields[k] as? [String: Any] { return v } }
        return nil
    }

    private static func string(_ fields: [String: Any], _ keys: [String]) -> String? {
        value(fields, keys)?["stringValue"] as? String
    }

    private static func number(_ fields: [String: Any], _ keys: [String]) -> Double? {
        guard let v = value(fields, keys), let raw = v["doubleValue"] ?? v["integerValue"] else { return nil }
        let d: Double?
        if let s = raw as? String { d = Double(s) }
        else if let n = raw as? NSNumber { d = n.doubleValue }
        else { d = nil }
        guard let out = d, out.isFinite else { return nil }
        return out
    }

    private static func parseInstant(_ s: String) -> Date? {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = withFraction.date(from: s) { return d }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        return plain.date(from: s)
    }

    private static func instant(_ fields: [String: Any], _ keys: [String]) -> Date? {
        guard let v = value(fields, keys),
              let raw = (v["timestampValue"] as? String) ?? (v["stringValue"] as? String) else { return nil }
        return parseInstant(raw)
    }

    /// The document's id (last segment of its resource name), whether or not it decodes.
    static func docId(_ document: [String: Any]) -> String? {
        guard let path = document["name"] as? String,
              let id = path.split(separator: "/", omittingEmptySubsequences: false).last.map(String.init),
              !id.isEmpty else { return nil }
        return id
    }

    /// nil when name, latitude or longitude is absent — never defaulted.
    static func decode(_ document: [String: Any], origin: SpotOrigin) -> RemoteSpot? {
        guard let docId = docId(document),
              let fields = document["fields"] as? [String: Any],
              let name = string(fields, ["name"]),
              let lat = number(fields, ["latitude"]),
              let lon = number(fields, ["longitude"]) else { return nil }
        let deleted = (value(fields, ["is_deleted", "isDeleted"])?["booleanValue"] as? Bool) ?? false
        return RemoteSpot(
            docId: docId, origin: origin, name: name, latitude: lat, longitude: lon,
            createdAt: instant(fields, ["created_at", "createdAt"]),
            lastModifiedAt: instant(fields, ["last_modified_at", "lastModifiedAt", "updatedAt"]),
            isDeleted: deleted,
            camelCase: fields.keys.contains { camelMarkers.contains($0) })
    }

    private static func str(_ v: String) -> [String: Any] { ["stringValue": v] }
    private static func bool(_ v: Bool) -> [String: Any] { ["booleanValue": v] }

    /// Same output as Java's ISO_INSTANT: fractional seconds only when non-zero, in groups of three.
    static func iso(_ d: Date) -> String {
        let ms = Int((d.timeIntervalSince1970 * 1000).rounded())
        let f = ISO8601DateFormatter()
        f.formatOptions = ms % 1000 == 0
            ? [.withInternetDateTime] : [.withInternetDateTime, .withFractionalSeconds]
        return f.string(from: Date(timeIntervalSince1970: Double(ms) / 1000))
    }

    /// Full `fields` map for PATCH users/{uid}/spots/{cloudId}; snake_case only.
    static func encodeUpsert(_ spot: LocalSpot, uid: String, nowIso: String) -> [String: Any] {
        return [
            "user_id": str(uid),
            "name": str(spot.name),
            "latitude": ["doubleValue": spot.latitude],
            "longitude": ["doubleValue": spot.longitude],
            "is_private": bool(true),
            "is_deleted": bool(spot.isDeleted),
            "created_at": str(iso(spot.createdAt)),
            "last_modified_at": str(iso(spot.lastModifiedAt)),
            "last_synced_at": str(nowIso),
            "cloud_id": str(spot.cloudId ?? spot.id),
            "source": str("go"),
            "sync_status": str("synced"),
        ]
    }

    /// `fields` + the updateMask field paths for a soft delete, in the doc's own casing.
    static func encodeSoftDelete(_ remote: RemoteSpot, nowIso: String) -> (fields: [String: Any], mask: [String]) {
        if remote.camelCase {
            return (["isDeleted": bool(true), "lastModifiedAt": ["timestampValue": nowIso]],
                    ["isDeleted", "lastModifiedAt"])
        }
        return (["is_deleted": bool(true), "last_modified_at": str(nowIso)],
                ["is_deleted", "last_modified_at"])
    }
}
