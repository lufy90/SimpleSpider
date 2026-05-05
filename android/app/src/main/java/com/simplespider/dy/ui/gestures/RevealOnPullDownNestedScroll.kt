package com.simplespider.dy.ui.gestures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

private const val RevealPullAccumulationPx = 120f
private const val HidePullAccumulationPx = 40f
private const val DoubleUpChunkPx = 95f

private fun NestedScrollSource.isUserScroll(): Boolean = when (this) {
    NestedScrollSource.UserInput -> true
    NestedScrollSource.SideEffect -> false
    else -> {
        @Suppress("DEPRECATION")
        this == NestedScrollSource.Drag
    }
}

@Composable
fun rememberSearchBarSwipeNestedConnection(
    enabled: () -> Boolean,
    searchVisible: () -> Boolean,
    onReveal: () -> Unit,
    onHide: () -> Unit,
): NestedScrollConnection {
    val latestEnabled = rememberUpdatedState(enabled)
    val latestVisible = rememberUpdatedState(searchVisible)
    val latestReveal = rememberUpdatedState(onReveal)
    val latestHide = rememberUpdatedState(onHide)
    return remember {
        object : NestedScrollConnection {
            private var pullDownPx = 0f
            private var pullUpPx = 0f
            private var upImpulses = 0

            private fun reset() {
                pullDownPx = 0f
                pullUpPx = 0f
            }

            private fun react(dy: Float) {
                if (!latestEnabled.value.invoke()) return
                if (kotlin.math.abs(dy) < 2f) return

                if (latestVisible.value.invoke()) {
                    if (dy < 0f) {
                        pullUpPx += -dy
                        pullDownPx = 0f
                        if (pullUpPx > HidePullAccumulationPx) {
                            latestHide.value.invoke()
                            reset()
                            upImpulses = 0
                        }
                    } else {
                        pullUpPx = 0f
                    }
                    return
                }

                if (dy > 0f) {
                    pullDownPx += dy
                    pullUpPx = 0f
                    upImpulses = 0
                    if (pullDownPx > RevealPullAccumulationPx) {
                        latestReveal.value.invoke()
                        reset()
                    }
                } else {
                    pullUpPx += -dy
                    pullDownPx = 0f
                    if (pullUpPx > DoubleUpChunkPx) {
                        upImpulses++
                        pullUpPx = 0f
                        if (upImpulses >= 2) {
                            latestReveal.value.invoke()
                            upImpulses = 0
                        }
                    }
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!source.isUserScroll()) return Offset.Zero
                react(consumed.y + available.y)
                return Offset.Zero
            }
        }
    }
}
