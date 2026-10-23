import AuthenticationServices
import CryptoKit
import Foundation

// ─────────────────────────────────────────────────────────────────────────────
// AppleSignIn.swift — Sign in with Apple on the watch, exchanged for a Firebase
// session through AuthClient ("apple.com"). The Wear twin is RemoteAuthSignIn.kt.
//
// Apple gets the SHA-256 of a random nonce; Firebase gets the raw nonce, and
// checks one against the other inside the ID token.
//
// No fallback to the Apple user ID: without an identity token there is no
// Firebase account, so the result is .failed — never a pretend sign-in.
// ─────────────────────────────────────────────────────────────────────────────

@MainActor
final class AppleSignIn: NSObject {
    private let auth: AuthClient
    private var continuation: CheckedContinuation<ASAuthorization?, Error>?
    private var controller: ASAuthorizationController?

    init(auth: AuthClient) {
        self.auth = auth
    }

    func signIn() async -> SignInResult {
        guard continuation == nil else { return .failed("Sign-in already in progress") }

        let nonce = randomNonceString()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.email]
        request.nonce = sha256(nonce)

        let authorization: ASAuthorization?
        do {
            authorization = try await withCheckedThrowingContinuation { cont in
                continuation = cont
                let controller = ASAuthorizationController(authorizationRequests: [request])
                controller.delegate = self
                self.controller = controller
                controller.performRequests()
            }
        } catch let error as ASAuthorizationError where error.code == .canceled {
            return .cancelled
        } catch {
            print("[AppleSignIn] authorization failed (\((error as NSError).code))")
            return .failed("Apple sign-in didn't complete")
        }

        guard let credential = authorization?.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8),
              !idToken.isEmpty
        else {
            return .failed("Apple didn't return a sign-in token")
        }
        return await auth.signInWithIdp(providerId: "apple.com", idToken: idToken, nonce: nonce)
    }

    private func finish(_ result: Result<ASAuthorization?, Error>) {
        let cont = continuation
        continuation = nil
        controller = nil
        cont?.resume(with: result)
    }

    // MARK: - Nonce (from Spearo Apnea's AuthManager)

    private func randomNonceString(length: Int = 32) -> String {
        var randomBytes = [UInt8](repeating: 0, count: length)
        let result = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        guard result == errSecSuccess else { return UUID().uuidString }
        return randomBytes.map { String(format: "%02x", $0) }.joined()
    }

    private func sha256(_ input: String) -> String {
        let data = Data(input.utf8)
        let hash = SHA256.hash(data: data)
        return hash.compactMap { String(format: "%02x", $0) }.joined()
    }
}

extension AppleSignIn: ASAuthorizationControllerDelegate {
    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization authorization: ASAuthorization
    ) {
        Task { @MainActor in self.finish(.success(authorization)) }
    }

    nonisolated func authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError error: Error
    ) {
        Task { @MainActor in self.finish(.failure(error)) }
    }
}
