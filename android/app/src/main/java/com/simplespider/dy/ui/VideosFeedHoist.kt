package com.simplespider.dy.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simplespider.dy.data.DyVideoDto

/**
 * Holds main-tab video grid list + scroll so they survive navigation to the player.
 */
class VideosFeedHoist {
    var search by mutableStateOf("")
    var page by mutableIntStateOf(1)
    val items = mutableStateListOf<DyVideoDto>()
    var total by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var gridColumns by mutableIntStateOf(2)
    var showSearchBar by mutableStateOf(false)
    var useRandomList by mutableStateOf(false)
    val gridState = LazyGridState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
}
