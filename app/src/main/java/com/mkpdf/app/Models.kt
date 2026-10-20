package com.mkpdf.app

import org.json.JSONArray
import org.json.JSONObject

data class Page(val width: Float, val height: Float, val rotation: Int, val label: String)
data class Document(val handle: Long, val name: String, val generation: Long, val dirty: Boolean,
    val undo: Boolean, val redo: Boolean, val pages: List<Page>, val notices: List<String>)
data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class Hit(val page: Int, val start: Int, val rects: List<PdfRect>)
data class TextLine(val index: Int, val text: String, val editable: Boolean)
data class Option(val value: String, val label: String)
data class Widget(val page: Int, val rect: PdfRect, val hidden: Boolean)
data class FormField(val name: String, val kind: String, val values: List<String>, val options: List<Option>,
    val readOnly: Boolean, val multi: Boolean, val editableChoice: Boolean, val maxLength: Int?, val widgets: List<Widget>)
data class UiState(val doc: Document? = null, val busy: Boolean = false, val error: String? = null,
    val message: String? = null, val query: String = "", val hits: List<Hit> = emptyList(),
    val searching: Boolean = false, val selectedHit: Int = -1, val fields: List<FormField> = emptyList(),
    val lines: List<TextLine> = emptyList(), val recoveryAvailable: Boolean = false)
fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply {
    values.forEach { (k,v) -> put(k, when(v) { null -> JSONObject.NULL; is List<*> -> JSONArray(v); else -> v }) }
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings() = (0 until length()).map { getString(it) }
fun JSONArray.rect() = PdfRect(getDouble(0).toFloat(),getDouble(1).toFloat(),getDouble(2).toFloat(),getDouble(3).toFloat())
fun JSONObject.document() = Document(getLong("handle"),getString("name"),getLong("generation"),getBoolean("dirty"),
    getBoolean("undo"),getBoolean("redo"),getJSONArray("pages").objects().map {
        Page(it.getDouble("width").toFloat(),it.getDouble("height").toFloat(),it.getInt("rotation"),it.getString("label"))
    }, getJSONArray("notices").strings() + getJSONArray("warnings").strings())
fun JSONObject.formFields() = getJSONArray("fields").objects().map { f ->
    FormField(f.getString("name"),f.getString("kind"),f.getJSONArray("values").strings(),
        f.getJSONArray("options").objects().map { Option(it.getString("value"),it.getString("label")) }.distinctBy { it.value },
        f.getBoolean("readonly"),f.getBoolean("multi"),f.getBoolean("editableChoice"),
        if(f.isNull("maxLength")) null else f.getInt("maxLength"),
        f.getJSONArray("widgets").objects().filter { !it.isNull("page") && !it.isNull("rect") }.map {
            Widget(it.getInt("page"),it.getJSONArray("rect").rect(),it.getBoolean("hidden"))
        })
}
