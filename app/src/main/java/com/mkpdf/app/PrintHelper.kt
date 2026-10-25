package com.mkpdf.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Printing and sharing of a temporary copy of the open document, including its unsaved edits. */
object PrintHelper {
    /** Copies [source] into the app cache (the folder declared in file_paths.xml) and returns the copy. */
    fun copyForShare(context: Context, source: File, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // Remove copies older than an hour so the cache does not grow.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 60L * 60 * 1000 }?.forEach { it.delete() }
        var safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Document.pdf" }
        if (!safe.endsWith(".pdf", ignoreCase = true)) safe += ".pdf"
        val target = File(dir, safe)
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output, 64 * 1024) } }
        return target
    }

    fun print(context: Context, file: File, jobName: String, pageCount: Int) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(jobName, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pageCount)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Send"))
    }
}