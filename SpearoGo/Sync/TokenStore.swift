import Foundation
import Security

// ─────────────────────────────────────────────────────────────────────────────
// TokenStore.swift — the Firebase session in the Keychain. Twin of
// wear/.../sync/TokenStore.kt (SharedPreferences "auth", excluded from backup).
//
// ThisDeviceOnly: the refresh token is a credential, so it never migrates to
// another device through a backup. AfterFirstUnlock: background sync can read it.
// ─────────────────────────────────────────────────────────────────────────────

final class KeychainTokenStore: TokenStore {
    private let service = "agency.visivo.SpearoGo.auth"
    private let account = "session"

    private var baseQuery: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func load() -> Session? {
        var query = baseQuery
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return try? JSONDecoder().decode(Session.self, from: data)
    }

    func save(_ session: Session) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let status = SecItemUpdate(baseQuery as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            var add = baseQuery
            add.merge(attributes) { _, new in new }
            SecItemAdd(add as CFDictionary, nil)
        }
    }

    func clear() {
        SecItemDelete(baseQuery as CFDictionary)
    }
}
