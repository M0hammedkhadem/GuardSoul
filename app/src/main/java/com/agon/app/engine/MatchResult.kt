package com.agon.app.engine

enum class BlockAction {
    ALLOW,
    BLOCK_FULL,
    BLOCK_PARTIAL,
    REDIRECT_HOME,
    HIDE_ELEMENT,
    SAFE_SEARCH_ONLY
}

data class BlockMatch(
    val action: BlockAction,
    val reason: String,
    val source: MatchSource,
    val priority: Int = 0
)

enum class MatchSource {
    BLACKLIST_KEYWORD,
    BLACKLIST_WEBSITE,
    BLACKLIST_APP,
    SOCIAL_FULL_BLOCK,
    SOCIAL_PARTIAL_BLOCK,
    PORN_CONTENT,
    DNS_FILTER,
    SAFE_SEARCH,
    AI_SCANNER,
    NONE
}

data class FilterContext(
    val url: String? = null,
    val packageName: String? = null,
    val pageTitle: String? = null,
    val visibleText: String? = null,
    val appName: String? = null,
    val isSearchQuery: Boolean = false,
    val searchEngine: String? = null
)
