package app.recall.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRendererTest {

    private val renderer = CardRenderer()
    private val fields = mapOf(
        "term" to "alpha",
        "definition" to "first letter",
        "audio" to "[sound:alpha.mp3]",
        "mem" to "",
        "example" to "Alpha starts the list.",
    )

    @Test
    fun substitutesFields() {
        assertEquals("first letter", renderer.render("{{definition}}", fields, ""))
    }

    @Test
    fun unknownFieldRendersEmpty() {
        assertEquals("[]", renderer.render("[{{nope}}]", fields, ""))
    }

    @Test
    fun frontSideIsInserted() {
        assertEquals("front|alpha", renderer.render("{{FrontSide}}|{{term}}", fields, "front"))
    }

    @Test
    fun conditionalKeepsBlockOnlyWhenFieldNonEmpty() {
        assertEquals("yes", renderer.render("{{#example}}yes{{/example}}", fields, ""))
        assertEquals("", renderer.render("{{#mem}}yes{{/mem}}", fields, ""))
    }

    @Test
    fun invertedConditionalKeepsBlockOnlyWhenFieldEmpty() {
        assertEquals("no mem", renderer.render("{{^mem}}no mem{{/mem}}", fields, ""))
        assertEquals("", renderer.render("{{^example}}x{{/example}}", fields, ""))
    }

    @Test
    fun soundTagBecomesBridgeCall() {
        val html = renderer.render("{{audio}}", fields, "")
        assertTrue(html.contains("Recall.playAudio('alpha.mp3')"))
        assertFalse(html.contains("[sound:"))
    }

    @Test
    fun unsupportedDirectivesAreRemoved() {
        assertEquals("ab", renderer.render("a{{type:term}}b", fields, ""))
    }
}
