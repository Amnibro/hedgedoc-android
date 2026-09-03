package org.hedgedoc.android

import org.hedgedoc.android.data.HedgeEdition
import org.hedgedoc.android.data.HedgeUrls
import org.hedgedoc.android.data.HedgeV2Parse
import org.hedgedoc.android.data.MarkdownTasks
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

    @Test
    fun diffTouchesOnlyTheChangedRun() {
        val old = "- [ ] water the tomatoes\n- [ ] pick basil\n"
        val new = "- [x] water the tomatoes\n- [ ] pick basil\n"
        val op = TextOperation.diff(old, new)
        assertEquals(new, op.apply(old))
        assertEquals(listOf<Any>(3, -1, "x", 38), op.toJsonList())
    }

    @Test
    fun diffHandlesEmptyEnds() {
        assertEquals("hello", TextOperation.diff("", "hello").apply(""))
        assertEquals("", TextOperation.diff("hello", "").apply("hello"))
        assertTrue(TextOperation.diff("same", "same").isNoop())
    }

    @Test
    fun composeMatchesSequentialApply() {
        val doc = "the quick brown fox"
        val first = TextOperation.diff(doc, "the quick red fox")
        val middle = first.apply(doc)
        val second = TextOperation.diff(middle, "the very quick red fox")
        val composed = first.compose(second)
        assertEquals(second.apply(middle), composed.apply(doc))
        assertEquals(doc.length, composed.baseLength())
    }

    @Test
    fun transformConvergesOnConcurrentEdits() {
        val doc = "hedgedoc"
        val mine = TextOperation().retain(8).insert(" rocks")
        val theirs = TextOperation().insert("the ").retain(8)
        val (minePrime, theirsPrime) = TextOperation.transform(mine, theirs)
        assertEquals(
            theirs.compose(minePrime).apply(doc),
            mine.compose(theirsPrime).apply(doc),
        )
        assertEquals("the hedgedoc rocks", mine.compose(theirsPrime).apply(doc))
    }

    @Test
    fun transformHandlesOverlappingDeletes() {
        val doc = "abcdefgh"
        val mine = TextOperation().retain(2).delete(3).retain(3)
        val theirs = TextOperation().retain(3).delete(3).retain(2)
        val (minePrime, theirsPrime) = TextOperation.transform(mine, theirs)
        assertEquals(
            theirs.compose(minePrime).apply(doc),
            mine.compose(theirsPrime).apply(doc),
        )
        assertEquals("abgh", mine.compose(theirsPrime).apply(doc))
    }

    @Test
    fun jsonRoundTrip() {
        val op = TextOperation().retain(2).delete(2).insert("XY").retain(2)
        val back = TextOperation.fromJsonList(op.toJsonList())
        assertEquals("abXYef", back.apply("abcdef"))
    }
}

class MarkdownTasksTest {
    private val note = """
        # Garden

        - [ ] water the tomatoes
        - [x] pick basil
        1. [ ] numbered task

        ```
        - [ ] not a task, this is code
        ```

        - [ ] last one
    """.trimIndent()

    @Test
    fun countsOnlyRenderedTasks() {
        assertEquals(4, MarkdownTasks.count(note))
    }

    @Test
    fun togglesTheRequestedBox() {
        val toggled = MarkdownTasks.toggle(note, 0)!!
        assertTrue(toggled.contains("- [x] water the tomatoes"))
        assertTrue(toggled.contains("- [x] pick basil"))
        assertTrue(MarkdownTasks.isChecked(toggled, 0))
    }

    @Test
    fun uncheckingWorksBothWays() {
        val toggled = MarkdownTasks.toggle(note, 1)!!
        assertTrue(toggled.contains("- [ ] pick basil"))
        assertFalse(MarkdownTasks.isChecked(toggled, 1))
    }

    @Test
    fun fencedCodeDoesNotShiftTheIndex() {
        val toggled = MarkdownTasks.toggle(note, 3)!!
        assertTrue(toggled.contains("- [x] last one"))
        assertTrue(toggled.contains("- [ ] not a task, this is code"))
    }

    @Test
    fun numberedTasksCount() {
        val toggled = MarkdownTasks.toggle(note, 2)!!
        assertTrue(toggled.contains("1. [x] numbered task"))
    }

    @Test
    fun outOfRangeIsNull() {
        assertNull(MarkdownTasks.toggle(note, 9))
        assertNull(MarkdownTasks.toggle(note, -1))
        assertNull(MarkdownTasks.toggle("no tasks here", 0))
    }

    @Test
    fun everythingElseSurvivesUntouched() {
        val toggled = MarkdownTasks.toggle(note, 0)!!
        assertEquals(note.lines().size, toggled.lines().size)
        assertEquals(note.length, toggled.length)
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
