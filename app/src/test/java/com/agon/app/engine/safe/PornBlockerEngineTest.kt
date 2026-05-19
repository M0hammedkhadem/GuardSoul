package com.agon.app.engine.safe

import com.agon.app.engine.BlockAction
import com.agon.app.engine.FilterContext
import org.junit.Assert.*
import org.junit.Test

class PornBlockerEngineTest {

    private val engine = PornBlockerEngine(active = true)

    @Test
    fun `keyword sex must NOT block middlesex edu`() {
        val result = engine.evaluate(FilterContext(
            url = "https://www.middlesex.edu/page",
            pageTitle = "Middlesex University - Home",
            visibleText = "Welcome to Middlesex University"
        ))
        assertNull("middlesex.edu should not be blocked by 'sex' keyword", result)
    }

    @Test
    fun `keyword 18plus must NOT match 18800 or 1899`() {
        val result1 = engine.evaluate(FilterContext(
            url = "https://18800.example.com",
            pageTitle = "18800 Page",
            visibleText = "Welcome to 18800"
        ))
        assertNull("18800 should not match '18+' keyword", result1)

        val result2 = engine.evaluate(FilterContext(
            url = "https://1899.example.com",
            pageTitle = "1899 Service",
            visibleText = "1899 support"
        ))
        assertNull("1899 should not match '18+' keyword", result2)
    }

    @Test
    fun knownAdultURLMustBeBlocked() {
        val result = engine.evaluate(FilterContext(
            url = "https://www.pornhub.com/video/123",
            pageTitle = "Pornhub Video",
            visibleText = "Check out this hot video"
        ))
        assertNotNull("pornhub.com should be blocked", result)
        assertEquals(BlockAction.BLOCK_FULL, result?.action)
    }

    @Test
    fun allowlistedDomainSkipsBlocking() {
        val result = engine.evaluate(FilterContext(
            url = "https://www.1800contacts.com/order",
            pageTitle = "1800 Contacts",
            visibleText = "Buy contact lenses"
        ))
        assertNull("1800contacts.com should not be blocked", result)
    }

    @Test
    fun customAllowlistedDomainSkipsBlocking() {
        val eng = PornBlockerEngine(
            active = true,
            customAllowlistedDomains = setOf("mytrusted.site.com")
        )
        val result = eng.evaluate(FilterContext(
            url = "https://mytrusted.site.com/page",
            pageTitle = "Trusted Site",
            visibleText = "Some content here"
        ))
        assertNull("Custom allowlisted domain should not be blocked", result)
    }

    @Test
    fun pornDomainBlockedEvenWithInnocentText() {
        val result = engine.evaluate(FilterContext(
            url = "https://www.xvideos.com/category",
            pageTitle = "Category Page",
            visibleText = "Just a category"
        ))
        assertNotNull("xvideos.com should be blocked by domain", result)
        assertEquals(BlockAction.BLOCK_FULL, result?.action)
    }

    @Test
    fun `word bounded keyword does not match substring inside word`() {
        val result = engine.evaluate(FilterContext(
            url = "https://example.com/analysis",
            pageTitle = "Data Analysis",
            visibleText = "Data analysis is a powerful tool for researchers"
        ))
        assertNull("'anal' inside 'analysis' should not trigger word-bounded match", result)
    }
}
