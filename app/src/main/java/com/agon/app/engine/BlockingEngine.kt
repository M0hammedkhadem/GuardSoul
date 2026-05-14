package com.agon.app.engine

import com.agon.app.engine.filter.FilterEngine
import com.agon.app.engine.filter.KeywordMatcher
import com.agon.app.engine.filter.DomainMatcher
import com.agon.app.engine.filter.AppMatcher
import com.agon.app.engine.safe.SafeSearchEngine
import com.agon.app.engine.safe.PornBlockerEngine
import com.agon.app.engine.safe.ContentScanner
import com.agon.app.engine.social.*

class BlockingEngine(
    val filterEngine: FilterEngine = FilterEngine(),
    val socialBlocker: SocialBlocker = SocialBlocker(),
    val pornBlocker: PornBlockerEngine = PornBlockerEngine(),
    val safeSearch: SafeSearchEngine = SafeSearchEngine(),
    val contentScanner: ContentScanner = ContentScanner(),
    val keywordMatcher: KeywordMatcher = KeywordMatcher(),
    val domainMatcher: DomainMatcher = DomainMatcher(),
    val appMatcher: AppMatcher = AppMatcher()
) {
    data class EvaluationResult(
        val shouldBlock: Boolean = false,
        val action: BlockAction = BlockAction.ALLOW,
        val match: BlockMatch? = null,
        val allMatches: List<BlockMatch> = emptyList()
    )

    fun evaluate(ctx: FilterContext): EvaluationResult {
        val allMatches = mutableListOf<BlockMatch>()

        val socialMatch = socialBlocker.evaluate(ctx)
        if (socialMatch != null) allMatches.add(socialMatch)

        val filterMatch = filterEngine.evaluate(ctx)
        if (filterMatch != null) allMatches.add(filterMatch)

        val pornMatch = pornBlocker.evaluate(ctx)
        if (pornMatch != null) allMatches.add(pornMatch)

        if (allMatches.isEmpty()) {
            return EvaluationResult(false, BlockAction.ALLOW, null)
        }

        val bestMatch = allMatches.maxByOrNull { it.priority }
        val shouldBlock = bestMatch?.action != BlockAction.ALLOW

        return EvaluationResult(
            shouldBlock = shouldBlock,
            action = bestMatch?.action ?: BlockAction.ALLOW,
            match = bestMatch,
            allMatches = allMatches.sortedByDescending { it.priority }
        )
    }

    fun evaluateApp(packageName: String, appName: String? = null): EvaluationResult {
        return evaluate(FilterContext(packageName = packageName, appName = appName))
    }

    fun evaluateUrl(url: String, fromPackage: String? = null): EvaluationResult {
        return evaluate(FilterContext(url = url, packageName = fromPackage))
    }

    fun evaluateText(text: String, isSearch: Boolean = false): EvaluationResult {
        return evaluate(FilterContext(visibleText = text, isSearchQuery = isSearch))
    }

    fun getEffectiveBlockAction(packageName: String, url: String?): BlockAction {
        val ctx = FilterContext(packageName = packageName, url = url)
        val result = evaluate(ctx)
        return result.action
    }

    fun isWhitelisted(packageName: String): Boolean {
        return appMatcher.evaluate(packageName).apply { !matched }.let { !it.matched }
    }

    fun isFullyBlocked(packageName: String): Boolean {
        val appResult = appMatcher.evaluate(packageName)
        if (appResult.matched) return true
        val socialResult = socialBlocker.evaluate(FilterContext(packageName = packageName))
        return socialResult?.action == BlockAction.BLOCK_FULL
    }

    fun resetAll() {
        contentScanner.resetAll()
    }

    companion object {
        fun builder(): Builder = Builder()
    }

    class Builder {
        private var filterEngine: FilterEngine = FilterEngine()
        private var socialBlocker: SocialBlocker = SocialBlocker()
        private var pornBlocker: PornBlockerEngine = PornBlockerEngine()
        private var safeSearch: SafeSearchEngine = SafeSearchEngine()
        private var contentScanner: ContentScanner = ContentScanner()
        private var keywordMatcher: KeywordMatcher = KeywordMatcher()
        private var domainMatcher: DomainMatcher = DomainMatcher()
        private var appMatcher: AppMatcher = AppMatcher()

        fun withFilterEngine(engine: FilterEngine) = apply { this.filterEngine = engine }
        fun withSocialBlocker(blocker: SocialBlocker) = apply { this.socialBlocker = blocker }
        fun withPornBlocker(blocker: PornBlockerEngine) = apply { this.pornBlocker = blocker }
        fun withSafeSearch(safeSearch: SafeSearchEngine) = apply { this.safeSearch = safeSearch }
        fun withContentScanner(scanner: ContentScanner) = apply { this.contentScanner = scanner }
        fun withKeywordMatcher(matcher: KeywordMatcher) = apply { this.keywordMatcher = matcher }
        fun withDomainMatcher(matcher: DomainMatcher) = apply { this.domainMatcher = matcher }
        fun withAppMatcher(matcher: AppMatcher) = apply { this.appMatcher = matcher }

        fun build() = BlockingEngine(
            filterEngine = filterEngine,
            socialBlocker = socialBlocker,
            pornBlocker = pornBlocker,
            safeSearch = safeSearch,
            contentScanner = contentScanner,
            keywordMatcher = keywordMatcher,
            domainMatcher = domainMatcher,
            appMatcher = appMatcher
        )
    }
}
