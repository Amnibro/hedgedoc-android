package org.hedgedoc.android

import org.hedgedoc.android.data.HedgeUrls
import org.hedgedoc.android.data.TextOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOperationTest {
    @Test
    fun replaceAllSwapsDocument() {
        val old = "hello"
        val neu = "world!!"
        val op = TextOperation.replaceAll(old, neu)
        assertEquals(old.length, op.baseLength())
        assertEquals(neu.length, op.targetLength())
        assertEquals(neu, op.apply(old))
    }

    @Test
    fun insertIntoEmpty() {
        val op = TextOperation.replaceAll("", "# hi")
        assertEquals(0, op.baseLength())
        assertEquals("# hi", op.apply(""))
    }

    @Test
    fun retainInsertDeleteRoundTrip() {
        val doc = "abcdef"
        val op = TextOperation().retain(2).delete(2).insert("XY").retain(2)
        assertEquals("abXYef", op.apply(doc))
    }
}

class HedgeUrlsTest {
    @Test
    fun normalizeAddsHttps() {
        val url = HedgeUrls.normalizeServer("demo.hedgedoc.org")
        assertEquals("https", url.scheme)
        assertEquals("demo.hedgedoc.org", url.host)
        assertTrue(url.encodedPath.endsWith("/"))
    }

    @Test
    fun noteIdFromSimplePath() {
        assertEquals("abc123", HedgeUrls.noteIdFromPath("/abc123"))
        assertNull(HedgeUrls.noteIdFromPath("/history"))
        assertNull(HedgeUrls.noteIdFromPath("/new"))
        assertEquals("published", HedgeUrls.noteIdFromPath("/s/published"))
    }

    @Test
    fun noteIdFromFullUrl() {
        val server = HedgeUrls.normalizeServer("https://demo.hedgedoc.org")
        assertEquals(
            "abc123",
            HedgeUrls.noteIdFromUrl("https://demo.hedgedoc.org/abc123", server),
        )
    }

    @Test
    fun titleFromMarkdownUsesFirstHeading() {
        assertEquals("Garden notes", HedgeUrls.titleFromMarkdown("# Garden notes\n\nHello"))
        assertEquals("Untitled", HedgeUrls.titleFromMarkdown("   \n"))
        assertEquals("Front", HedgeUrls.titleFromMarkdown("---\ntitle: Front\n---\n# Other"))
    }
}
