import Foundation

enum PersonalityCopy {
    // MARK: - Verdict messages

    private static let goMessages: [String] = [
        "GET IN THE WATER!",
        "Fish are waiting. Go get 'em.",
        "Perfect day. No excuses.",
        "Why are you still reading this? GO!",
        "The ocean is calling.",
        "Conditions are chef's kiss.",
        "Today's the day. Suit up.",
        "Send it!"
    ]

    private static let maybeMessages: [String] = [
        "Could be worse. Could be better.",
        "Eh, you've dove in worse.",
        "Decent. Just don't be a hero.",
        "Your call, chief.",
        "Not ideal, but fishable.",
        "The ocean shrugs at you.",
        "Proceed with mild enthusiasm."
    ]

    private static let sketchyMessages: [String] = [
        "Think twice, dive once.",
        "Your wetsuit will earn its keep today.",
        "Spicy conditions. You sure?",
        "Only if you're feeling brave.",
        "The ocean is in a mood.",
        "Experienced divers only.",
        "Tell someone where you're going.",
        "Check your insurance first."
    ]

    private static let noGoMessages: [String] = [
        "Nope. Netflix day.",
        "The ocean said no.",
        "Stay dry. Stay alive.",
        "Not today, friend.",
        "Hard pass.",
        "Your couch misses you anyway.",
        "Train your breath hold instead.",
        "Even the fish are hiding."
    ]

    private static let loadingMessages: [String] = [
        "Asking the ocean...",
        "Checking the vibes...",
        "Consulting the fish...",
        "Reading the waves..."
    ]

    // MARK: - Public interface

    static func message(for verdict: Verdict) -> String {
        switch verdict {
        case .go:      return goMessages.randomElement() ?? "GO!"
        case .maybe:   return maybeMessages.randomElement() ?? "Your call."
        case .sketchy: return sketchyMessages.randomElement() ?? "Be careful."
        case .noGo:    return noGoMessages.randomElement() ?? "Not today."
        }
    }

    static func loading() -> String {
        loadingMessages.randomElement() ?? "Loading..."
    }
}
