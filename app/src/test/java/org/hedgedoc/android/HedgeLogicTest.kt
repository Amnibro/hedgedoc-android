package org.hedgedoc.android

import org.hedgedoc.android.data.HedgeEdition
import org.hedgedoc.android.data.HedgeUrls
import org.hedgedoc.android.data.HedgeV2Parse
import org.hedgedoc.android.data.TextOperation
import org.hedgedoc.android.ui.theme.ScientPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals("garden", HedgeUrls.noteIdFromPath("/n/garden"))
        assertNull(HedgeUrls.noteIdFromPath("/n"))
    }

    @Test
    fun noteUrlUsesNPrefixOnV2() {
        val server = HedgeUrls.normalizeServer("https://md.example.com")
        assertEquals("https://md.example.com/abc123", HedgeUrls.noteUrl(server, "abc123"))
        assertEquals(
            "https://md.example.com/n/abc123",
            HedgeUrls.noteUrl(server, "abc123", HedgeEdition.V2),
        )
    }

    @Test
    fun noteIdFromFullUrl() {
        val server = HedgeUrls.normalizeServer("https://demo.hedgedoc.org")
        assertEquals(
            "abc123",
            HedgeUrls.noteIdFromUrl("https://demo.hedgedoc.org/abc123", server),
        )
        assertEquals(
            "garden",
            HedgeUrls.noteIdFromUrl("https://demo.hedgedoc.org/n/garden", server),
        )
    }

    @Test
    fun titleFromMarkdownUsesFirstHeading() {
        assertEquals("Garden notes", HedgeUrls.titleFromMarkdown("# Garden notes\n\nHello"))
        assertEquals("Untitled", HedgeUrls.titleFromMarkdown("   \n"))
        assertEquals("Front", HedgeUrls.titleFromMarkdown("---\ntitle: Front\n---\n# Other"))
    }
}

class ScientPalettesTest {
    @Test
    fun twelveAmniScientThemes() {
        assertEquals(12, ScientPalettes.All.size)
        assertEquals("scient", ScientPalettes.byId("nope").id)
        assertTrue(ScientPalettes.Light.lightBars)
        assertFalse(ScientPalettes.Scient.lightBars)
        assertEquals("Scient", ScientPalettes.Scient.label)
        assertEquals("amni", ScientPalettes.byId("amni").id)
    }
}

class HedgeV2ParseTest {
    @Test
    fun configWithAuthProvidersIsV2() {
        val body = """{"authProviders":[{"type":"local"}],"version":{"major":2,"minor":0,"patch":0}}"""
        assertEquals(HedgeEdition.V2, HedgeV2Parse.editionFromConfig(200, body))
        assertEquals(HedgeEdition.V1, HedgeV2Parse.editionFromConfig(404, body))
        assertEquals(HedgeEdition.V1, HedgeV2Parse.editionFromConfig(200, "<html>nope</html>"))
    }

    @Test
    fun historyFromIdentifierArray() {
        val body = """
            [{"identifier":"garden","title":"Tomatoes","tags":["food"],"pinStatus":true,"lastVisitedAt":"2020-12-01T12:23:34.000Z"}]
        """.trimIndent()
        val notes = HedgeV2Parse.history(body)
        assertEquals(1, notes.size)
        assertEquals("garden", notes[0].id)
        assertEquals("Tomatoes", notes[0].title)
        assertTrue(notes[0].pinned)
        assertEquals(listOf("food"), notes[0].tags)
        assertTrue(notes[0].time > 0)
    }

    @Test
    fun noteIdFromMetadataAliases() {
        val body = """
            {"content":"# Hi","metadata":{"title":"Hi","aliases":[{"name":"hi-note","primary":true}]}}
        """.trimIndent()
        assertEquals("hi-note", HedgeV2Parse.noteIdFromDto(body))
        assertEquals("# Hi", HedgeV2Parse.noteContent(body))
    }

    @Test
    fun profilePrefersDisplayName() {
        val profile = HedgeV2Parse.profile("""{"username":"ada","displayName":"Ada Lovelace","photoUrl":""}""")
        assertEquals("Ada Lovelace", profile?.name)
        assertEquals("ada", profile?.id)
        assertFalse(profile!!.guest)
    }

    @Test
    fun visibilityFromPublicFlag() {
        assertEquals("public", HedgeV2Parse.visibility("""{"publiclyVisible":true}"""))
        assertEquals("private", HedgeV2Parse.visibility("""{"permissions":{"publiclyVisible":false}}"""))
    }
}
