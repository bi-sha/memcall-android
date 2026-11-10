package app.recall.render

import app.recall.collection.ModelInfo

/**
 * Turns a note type's templates into standalone HTML documents for a WebView.
 *
 * Supported Anki syntax: {{field}}, {{FrontSide}}, {{#field}}..{{/field}} and
 * {{^field}}..{{/field}}, and [sound:file] (turned into a call to the native bridge).
 * {{type:}}, {{hint:}}, {{cloze:}} and {{tts:}} are removed; the templates do not use them.
 *
 * The pages load recall_bridge.js from the app's assets, which defines window.Recall.
 * FRAGILE: the template language is Anki's; new directives need a new branch here.
 */
class CardRenderer {

    fun front(model: ModelInfo, template: ModelInfo.TemplateInfo, fields: Map<String, String>, mem: String?): String =
        page(model.css, render(template.qfmt, fields, frontSide = ""), mem)

    fun back(model: ModelInfo, template: ModelInfo.TemplateInfo, fields: Map<String, String>, mem: String?): String {
        val frontBody = render(template.qfmt, fields, frontSide = "")
        return page(model.css, render(template.afmt, fields, frontSide = frontBody), mem)
    }

    internal fun render(template: String, fields: Map<String, String>, frontSide: String): String {
        var out = template
        out = IF_FIELD.replace(out) { m ->
            if (fields[m.groupValues[1].trim()].isNullOrBlank()) "" else m.groupValues[2]
        }
        out = IF_NOT_FIELD.replace(out) { m ->
            if (fields[m.groupValues[1].trim()].isNullOrBlank()) m.groupValues[2] else ""
        }
        out = out.replace("{{FrontSide}}", frontSide)
        out = UNSUPPORTED.replace(out, "")
        out = FIELD.replace(out) { m -> fieldHtml(fields[m.groupValues[1].trim()].orEmpty()) }
        return out
    }

    private fun fieldHtml(value: String): String =
        SOUND.replace(value) { m ->
            val name = m.groupValues[1].replace("\\", "\\\\").replace("'", "\\'").replace("\"", "&quot;")
            "<a href=\"#\" class=\"recall-play\" onclick=\"Recall.playAudio('$name');return false;\">&#9654;</a>"
        }

    private fun page(css: String, body: String, mem: String?): String {
        val memBlock = if (mem.isNullOrBlank()) "" else "<div class=\"mem\">$mem</div>"
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<script src=\"recall_bridge.js\"></script>" +
            "<style>$css</style></head><body><div class=\"card-root\">$body$memBlock</div></body></html>"
    }

    companion object {
        private val IF_FIELD = Regex("\\{\\{#([^{}]+)\\}\\}(.*?)\\{\\{/\\1\\}\\}", RegexOption.DOT_MATCHES_ALL)
        private val IF_NOT_FIELD = Regex("\\{\\{\\^([^{}]+)\\}\\}(.*?)\\{\\{/\\1\\}\\}", RegexOption.DOT_MATCHES_ALL)
        private val UNSUPPORTED = Regex("\\{\\{(type|hint|cloze|tts):[^{}]*\\}\\}")
        private val FIELD = Regex("\\{\\{([^{}#/^]+)\\}\\}")
        private val SOUND = Regex("\\[sound:([^\\]]+)\\]")
    }
}
