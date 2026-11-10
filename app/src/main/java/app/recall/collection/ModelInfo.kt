package app.recall.collection

/**
 * A note type from `col.models`. FRAGILE: this JSON exists only in the legacy schema
 * (genanki writes it). Modern Anki stores note types in the `notetypes`, `fields` and
 * `templates` tables instead.
 */
data class ModelInfo(
    val id: Long,
    val name: String,
    val fields: List<FieldInfo>,
    val templates: List<TemplateInfo>,
    val css: String,
) {
    data class FieldInfo(val name: String, val ord: Int)

    data class TemplateInfo(val name: String, val ord: Int, val qfmt: String, val afmt: String)

    fun fieldIndex(name: String): Int = fields.indexOfFirst { it.name == name }
}
