package com.mkpdf.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

class SafStore(private val context: Context) {
    val root = File(context.filesDir,"workspace").apply { mkdirs() }
    val recovery get() = File(root,"recovery.pdf")
    private val resolver get() = context.contentResolver
    private val prefs = context.getSharedPreferences("documents",Context.MODE_PRIVATE)
    fun persistRead(uri: Uri) {
        // OpenDocument grants read access. Some providers do not support persistence.
        try { resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { /* This provider remains usable for this session. */ }
        val previous=prefs.getString("recent",null)?.let(Uri::parse)
        if(previous!=null && previous!=uri) {
            try {resolver.releasePersistableUriPermission(previous,Intent.FLAG_GRANT_READ_URI_PERMISSION)} catch (_:SecurityException) {}
        }
        prefs.edit().putString("recent",uri.toString()).apply()
    }
    fun recent(): Uri? = prefs.getString("recent",null)?.let(Uri::parse)
    fun name(uri: Uri): String = resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c ->
        if(c.moveToFirst()) c.getString(0) else null
    } ?: "Document.pdf"
    fun stage(uri: Uri): Pair<String,File> {
        val name=name(uri);val file=File(root,"input-${UUID.randomUUID()}.bin")
        try {
            val input=resolver.openInputStream(uri) ?: throw IOException("Cannot open selected document")
            input.use { src -> file.outputStream().use { dst ->
                val buf=ByteArray(64*1024);var count=0L
                while(true) {val n=src.read(buf);if(n<0)break; count+=n
                    if(count>128L*1024*1024) throw IOException("Input exceeds 128 MiB")
                    dst.write(buf,0,n)
                }
            } }
            return name to file
        } catch(e:Exception) {file.delete();throw e}
    }
    fun write(uri: Uri, file: File) {
        // SAF cannot promise atomic replacement across providers. Save As preserves the source.
        val output=resolver.openOutputStream(uri,"wt") ?: throw IOException("Cannot write destination")
        output.use { dst -> file.inputStream().use { it.copyTo(dst,64*1024) }; dst.flush() }
    }
    fun child(tree: Uri, name: String): Uri {
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver,parent,"application/pdf",name)
            ?: throw IOException("Provider could not create $name")
    }
    fun cleanInputs() {root.listFiles()?.filter { it.name.startsWith("input-") }?.forEach {it.delete()} }
}
