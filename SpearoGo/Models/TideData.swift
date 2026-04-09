import Foundation

enum TidePhase: String {
    case slack = "Slack"
    case flood = "Flood"
    case ebb   = "Ebb"
}

struct TideData {
    let currentHeight: Double     // metres (relative)
    let isRising: Bool
    let phase: TidePhase
    let nextHighTime: Date
    let nextHighHeight: Double
    let nextLowTime: Date
    let nextLowHeight: Double
    let fetchedAt: Date
}
