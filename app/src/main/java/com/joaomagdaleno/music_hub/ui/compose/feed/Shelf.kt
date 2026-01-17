package com.joaomagdaleno.music_hub.ui.compose.feed

import com.joaomagdaleno.music_hub.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.common.models.Shelf
import com.joaomagdaleno.music_hub.ui.compose.components.CategoryCard
import com.joaomagdaleno.music_hub.ui.compose.components.MediaItemCard
import com.joaomagdaleno.music_hub.ui.compose.components.TrackItem
import kotlin.math.abs

@Composable
fun ShelfRow(
    shelf: Shelf.Lists<out Any>,
    onItemClick: (EchoMediaItem) -> Unit
) {
    val scrollState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shelf.title,
                    style = MaterialTheme.typography.titleLarge
                )
                shelf.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (shelf.more != null) {
                // IconButton(onClick = { /* TODO: Open More */ }) {
                //     Icon(painterResource(R.drawable.ic_arrow_forward_24dp), contentDescription = "More")
                // }
            }
        }

        if (false) { // Grid layout disabled due to compilation error in custom component
            // Grid layout disabled due to compilation error in custom component
            // LazyHorizontalGrid(
            //     rows = GridCells.Fixed(2),
            //     contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            //     modifier = Modifier.height(240.dp) 
            // ) {
            //     items(shelf.list) { item ->
            //         when (item) {
            //             is EchoMediaItem -> MediaItemCard(item = item, onClick = onItemClick)
            //             is Shelf.Category -> CategoryCard(category = item, onClick = { /* TODO */ })
            //         }
            //     }
            // }
        } else {
            LazyRow(
                state = scrollState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                if (shelf is Shelf.Lists.Tracks) {
                    val chunks = shelf.list.chunked(3)
                    itemsIndexed(chunks) { index, chunk ->
                        Column(
                            modifier = Modifier
                                .width(320.dp)
                                .graphicsLayer {
                                    // Animation disabled due to compilation error
                                }
                        ) {
                            chunk.forEach { track ->
                                TrackItem(track = track, onClick = { onItemClick(track) })
                            }
                        }
                    }
                } else {
                    itemsIndexed(shelf.list) { index, item ->
                        Box(
                            modifier = Modifier.graphicsLayer {
                                // Animation disabled due to compilation error
                            }
                        ) {
                            when (item) {
                                is com.joaomagdaleno.music_hub.common.models.Track -> {
                                    TrackItem(track = item, onClick = { onItemClick(item) })
                                }
                                is EchoMediaItem -> {
                                    MediaItemCard(item = item, onClick = onItemClick)
                                }
                                is Shelf.Category -> {
                                    CategoryCard(category = item, onClick = { /* TODO */ })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryShelf(
    shelf: Shelf.Lists.Categories,
    onClick: (Shelf.Category) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = shelf.title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
        ) {
            items(shelf.list) { category ->
                CategoryCard(category = category, onClick = { onClick(category) })
            }
        }
    }
}
