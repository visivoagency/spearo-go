package com.spearotracker.spearogo.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold

/**
 * One page of the pager: a vertically scrolling, horizontally centred column
 * that never puts text under the system clock, on the round screen's top or
 * bottom arc, or under the pager indicator.
 *
 * Every data page used a fixed 12.dp inset inside a full-screen scroll. That
 * is fine while the content is short, because the column centres it in the
 * middle of the circle where the screen is widest. Once the wearer picks a
 * larger text size the content outgrows the viewport, the scroll puts the
 * first line at 12.dp from the top, and that line lands under the clock on a
 * chord barely 100.dp wide; the last line is cut by the bottom arc. Google
 * Play rejected 2.1.0 (15) with a screenshot of exactly that: "CONDITIONS"
 * clipped to "CO…NS" and the swell caption cut by the bottom edge.
 *
 * So the viewport is inset, not the content. The top 10% and bottom 12% of
 * the screen are outside the scroll entirely: the clock lives in the top
 * band, the pager indicator in the bottom one, and neither arc can cut a
 * line. At rest the first line sits at 15% so it reads clear of the time
 * text. When the content is taller than the viewport its edges fade, which is
 * the scroll cue every Wear list gives, instead of a hard cut; the fade is
 * deep enough that a line wider than the chord at the viewport's edge has
 * already gone before the circle would clip it. At the end of the scroll the
 * fade lifts, so a page that overflows gets extra room under its last line to
 * keep it up in the wide part of the circle. Content that fits is still
 * centred, so the default look is unchanged.
 *
 * Insets are percentages of the screen, as the Wear guidelines ask, so a
 * 192.dp Pixel Watch and a 227.dp Galaxy Watch get the same geometry.
 */
@Composable
fun ScrollingPage(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val insets = roundScreenInsets()

    ScreenScaffold(scrollState = scrollState, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets.viewport)
                .fadeScrollEdges(scrollState)
                .verticalScroll(scrollState)
                .roomBelowWhenOverflowing(insets.scrollEnd)
                .padding(top = insets.clock),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * A list with the same geometry as [ScrollingPage]: viewport inset from the
 * clock, the pager indicator and both arcs, edges fading while there is more
 * to scroll, and room under the last row. Lists are top-aligned rather than
 * centred, so the room below is unconditional; it is never visible on a list
 * that fits.
 */
@Composable
fun ScrollingList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val insets = roundScreenInsets()

    ScreenScaffold(scrollState = state, modifier = modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(insets.viewport)
                .fadeScrollEdges(state),
            contentPadding = PaddingValues(top = insets.clock, bottom = insets.scrollEnd),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** The geometry, as dp for this screen. */
private class RoundScreenInsets(val viewport: PaddingValues, val clock: Dp, val scrollEnd: Dp)

@Composable
private fun roundScreenInsets(): RoundScreenInsets {
    val config = LocalConfiguration.current
    val h = config.screenHeightDp
    val w = config.screenWidthDp
    return RoundScreenInsets(
        viewport = PaddingValues(
            start = (w * SIDE_FRACTION).dp,
            end = (w * SIDE_FRACTION).dp,
            top = (h * TOP_FRACTION).dp,
            bottom = (h * BOTTOM_FRACTION).dp
        ),
        clock = (h * CLOCK_FRACTION).dp,
        scrollEnd = (h * SCROLL_END_FRACTION).dp
    )
}

/**
 * Adds [room] under the content only when the content is taller than the
 * viewport it scrolls in. Decided at measure time from the content's own
 * height, not from the scroll state: ScrollState reports an unbounded maximum
 * before its first layout, and a spacer keyed on it made every page scroll.
 * The viewport height arrives as the minimum height constraint, which the
 * scroll modifier passes through unchanged.
 */
private fun Modifier.roomBelowWhenOverflowing(room: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val viewport = constraints.minHeight
    val extra = if (viewport > 0 && placeable.height > viewport) room.roundToPx() else 0
    layout(placeable.width, placeable.height + extra) {
        placeable.place(0, 0)
    }
}

/**
 * Fades the top and bottom of a scrolling viewport while there is more
 * content in that direction. Nothing is drawn differently when the content
 * fits, so a page that does not scroll looks exactly as it did.
 */
private fun Modifier.fadeScrollEdges(state: ScrollableState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = size.height * FADE_FRACTION
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent, 1f to Color.Black,
                    startY = 0f, endY = fade
                ),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn
            )
        }
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black, 1f to Color.Transparent,
                    startY = size.height - fade, endY = size.height
                ),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Band at the top of the screen kept outside the viewport; the clock lives here. */
private const val TOP_FRACTION = 0.10f
/** Band at the bottom kept outside the viewport; the pager indicator lives here. */
private const val BOTTOM_FRACTION = 0.12f
/** Side inset. Wider than Wear Material's 5.2% so a wrapped caption stays inside the chord. */
private const val SIDE_FRACTION = 0.08f
/** Extra space above the first line, inside the scroll, so it starts below the clock. */
private const val CLOCK_FRACTION = 0.05f
/** Share of the viewport height that fades at each end while scrollable. */
private const val FADE_FRACTION = 0.20f
/** Space under the last line, only while scrollable, so it ends above the bottom arc. */
private const val SCROLL_END_FRACTION = 0.08f
