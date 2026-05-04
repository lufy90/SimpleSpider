package com.simplespider.dy.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.simplespider.dy.data.DyAuthorDto

@Stable
class AuthorsListHoistedState(
    val listState: LazyListState,
) {
    var search by mutableStateOf("")
    var page by mutableIntStateOf(1)
    val items: SnapshotStateList<DyAuthorDto> = mutableStateListOf()
    var total by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var rateSavingId by mutableStateOf<Int?>(null)
    var showSearchBar by mutableStateOf(false)
}

@Composable
fun rememberAuthorsListHoistedState(): AuthorsListHoistedState {
    val listState = rememberLazyListState()
    return remember(listState) { AuthorsListHoistedState(listState) }
}
