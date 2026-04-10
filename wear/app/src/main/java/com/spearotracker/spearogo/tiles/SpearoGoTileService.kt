package com.spearotracker.spearogo.tiles

import androidx.wear.tiles.*
import androidx.wear.tiles.material.*
import androidx.wear.tiles.material.layouts.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

// Tile service placeholder — provides a basic dive verdict tile
// Full implementation requires TileService API which needs the tile to be
// registered in AndroidManifest.xml (already done)
class SpearoGoTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(
                                        LayoutElementBuilders.Text.Builder()
                                            .setText("Spearo Go")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
    }
}
