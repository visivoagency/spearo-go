package com.spearotracker.spearogo.sync

import com.google.gson.JsonObject
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Firestore REST `Document` <-> merge types. Reads tolerate every convention in the wild
 * (camel or snake keys, timestampValue or ISO stringValue, doubleValue or integerValue);
 * writes are the website's snake_case shape only. Twin of SpearoGo/Sync/SpotCodec.swift.
 */
object SpotCodec {
    private val CAMEL_MARKERS = setOf("isDeleted", "lastModifiedAt", "createdAt", "userId")

    private fun JsonObject.value(vararg keys: String): JsonObject? {
        val fields = getAsJsonObject("fields") ?: return null
        for (k in keys) fields.get(k)?.takeIf { it.isJsonObject }?.let { return it.asJsonObject }
        return null
    }

    private fun JsonObject.string(vararg keys: String): String? =
        value(*keys)?.get("stringValue")?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.number(vararg keys: String): Double? {
        val v = value(*keys) ?: return null
        val raw = v.get("doubleValue") ?: v.get("integerValue") ?: return null
        if (!raw.isJsonPrimitive) return null
        return raw.asString.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun JsonObject.instantMs(vararg keys: String): Long? {
        val v = value(*keys) ?: return null
        val raw = (v.get("timestampValue") ?: v.get("stringValue"))
            ?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        return try { Instant.parse(raw).toEpochMilli() } catch (e: Exception) { null }
    }

    /** The document's id (last segment of its resource name), whether or not it decodes. */
    fun docId(document: JsonObject): String? =
        document.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            ?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    /** null when name, latitude or longitude is absent — never defaulted. */
    fun decode(document: JsonObject, origin: SpotOrigin): RemoteSpot? {
        val docId = docId(document) ?: return null
        val name = document.string("name") ?: return null
        val lat = document.number("latitude") ?: return null
        val lon = document.number("longitude") ?: return null
        val fields = document.getAsJsonObject("fields") ?: return null
        val deleted = document.value("is_deleted", "isDeleted")
            ?.get("booleanValue")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        return RemoteSpot(
            docId = docId, origin = origin, name = name, latitude = lat, longitude = lon,
            createdAt = document.instantMs("created_at", "createdAt"),
            lastModifiedAt = document.instantMs("last_modified_at", "lastModifiedAt", "updatedAt"),
            isDeleted = deleted,
            camelCase = fields.keySet().any { it in CAMEL_MARKERS })
    }

    private fun str(v: String) = JsonObject().apply { addProperty("stringValue", v) }
    private fun dbl(v: Double) = JsonObject().apply { addProperty("doubleValue", v) }
    private fun bool(v: Boolean) = JsonObject().apply { addProperty("booleanValue", v) }
    private fun iso(ms: Long) = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms))

    /** Full `fields` map for PATCH users/{uid}/spots/{cloudId}; snake_case only. */
    fun encodeUpsert(spot: LocalSpot, uid: String, nowIso: String): JsonObject = JsonObject().apply {
        add("user_id", str(uid))
        add("name", str(spot.name))
        add("latitude", dbl(spot.latitude))
        add("longitude", dbl(spot.longitude))
        add("is_private", bool(true))
        add("is_deleted", bool(spot.isDeleted))
        add("created_at", str(iso(spot.createdAt)))
        add("last_modified_at", str(iso(spot.lastModifiedAt)))
        add("last_synced_at", str(nowIso))
        add("cloud_id", str(spot.cloudId ?: spot.id))
        add("source", str("go"))
        add("sync_status", str("synced"))
    }

    /** `fields` + the updateMask field paths for a soft delete, in the doc's own casing. */
    fun encodeSoftDelete(remote: RemoteSpot, nowIso: String): Pair<JsonObject, List<String>> =
        if (remote.camelCase) {
            JsonObject().apply {
                add("isDeleted", bool(true))
                add("lastModifiedAt", JsonObject().apply { addProperty("timestampValue", nowIso) })
            } to listOf("isDeleted", "lastModifiedAt")
        } else {
            JsonObject().apply {
                add("is_deleted", bool(true))
                add("last_modified_at", str(nowIso))
            } to listOf("is_deleted", "last_modified_at")
        }
}
