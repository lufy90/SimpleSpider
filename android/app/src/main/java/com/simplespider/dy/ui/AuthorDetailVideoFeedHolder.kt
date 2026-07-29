package com.simplespider.dy.ui

/**
 * Single-slot author-detail video feed retained only across player navigation.
 * Call [retainForPlayer] before opening the player; [clear] when leaving the detail page.
 */
class AuthorDetailVideoFeedHolder {
    private var authorId: Int? = null
    private var feed: VideosFeedHoist? = null

    fun restoreOrNull(forAuthorId: Int): VideosFeedHoist? {
        val cached = feed ?: return null
        return if (authorId == forAuthorId) cached else null
    }

    fun retainForPlayer(forAuthorId: Int, feed: VideosFeedHoist) {
        this.authorId = forAuthorId
        this.feed = feed
    }

    fun clear() {
        authorId = null
        feed = null
    }
}
