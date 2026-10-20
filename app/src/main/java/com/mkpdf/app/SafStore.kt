package com.mkpdf.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.IOException
import java.util.UUID

/** Same as the normal file picker, but also asks Android for permission to write back to the chosen file. */
class OpenPdfWithWrite : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
}

class SafStore(private val context: Context) {
    val root = File(context.filesDir,"workspace").apply { mkdirs() }
    val recovery get() = File(root,"recovery.pdf")
    private val resolver get() = context.contentResolver
    private val prefs = context.getSharedPreferences("documents",Context.MODE_PRIVATE)
    fun persistRead(uri: Uri) {
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Try read+write first; some providers only allow read, and some do not support persistence at all.
        try { resolver.takePersistableUriPermission(uri,readWrite) }
        catch (_: SecurityException) {
            try { resolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) { /* This provider remains usable for this session. */ }
        }
        val previous=prefs.getString("recent",null)?.let(Uri::parse)
        if(previous!=null && previous!=uri) {
            try {resolver.releasePersistableUriPermission(previous,readWrite)} catch (_:SecurityException) {}
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