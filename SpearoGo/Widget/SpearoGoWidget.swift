import WidgetKit
import SwiftUI

// ─────────────────────────────────────────────────────────────────────────────
// SpearoGoWidget.swift — Smart Stack complication for Apple Watch.
//
// Displays the current dive verdict and score at a glance.
// Supports accessoryRectangular (verdict + score + time) and
// accessoryCircular (score gauge) families.
//
// NOTE: To add this widget to the project, create a Widget Extension target
// in Xcode (File → New → Target → Widget Extension) and point it at these
// source files. Both targets must share the App Group
// "group.agency.visivo.SpearoGo" in their entitlements.
// ─────────────────────────────────────────────────────────────────────────────

// MARK: - Timeline Entry

struct SpearoEntry: TimelineEntry {
    let date: Date
    let score: SharedScore?

    static var placeholder: SpearoEntry {
        SpearoEntry(date: Date(), score: .preview)
    }
}

// MARK: - Timeline Provider

struct SpearoTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> SpearoEntry {
        .placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (SpearoEntry) -> Void) {
        let entry = SpearoEntry(date: Date(), score: SharedScore.load() ?? .preview)
        completion(entry)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SpearoEntry>) -> Void) {
        let current = SharedScore.load()
        let entry = SpearoEntry(date: Date(), score: current)

        // Refresh every 30 minutes (aligned with app background refresh)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 30, to: Date())
            ?? Date().addingTimeInterval(1800)
        let timeline = Timeline(entries: [entry], policy: .after(nextUpdate))
        completion(timeline)
    }
}

// MARK: - Widget Configuration

struct SpearoGoWidget: Widget {
    let kind: String = "SpearoGoWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SpearoTimelineProvider()) { entry in
            SpearoWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Dive Verdict")
        .description("Current dive score and verdict at a glance.")
        #if os(watchOS)
        .supportedFamilies([.accessoryRectangular, .accessoryCircular, .accessoryCorner])
        #endif
    }
}

// MARK: - Entry View (dispatches by family)

struct SpearoWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: SpearoEntry

    var body: some View {
        switch family {
        case .accessoryRectangular:
            RectangularWidgetView(entry: entry)
        case .accessoryCircular:
            CircularWidgetView(entry: entry)
        case .accessoryCorner:
            CornerWidgetView(entry: entry)
        default:
            RectangularWidgetView(entry: entry)
        }
    }
}
