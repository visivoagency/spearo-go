import Foundation

struct TideData {
    let currentHeight: Double     // metres (relative)
    let isRising: Bool
    let phase: String             // e.g. "Flood", "Ebb", "Slack"
    let nextHighTime: Date
    let nextHighHeight: Double
    let nextLowTime: Date
    let nextLowHeight: Double
    let fetchedAt: Date
}
