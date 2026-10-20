package com.mkpdf.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number)
private fun digits(s: String) = s.filter { it.isDigit() }.take(5)

@Composable
private fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

/** Add Blank Page / Insert Page from PDF: Before or After a page number. Returns (after, 1-based page number). */
@Composable
fun PageInsertDialog(title: String, pageCount: Int, current: Int, confirmLabel: String, onDismiss: () -> Unit, onConfirm: (Boolean, Int) -> Unit) {
    var after by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf(current.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Choice(!after, "Before") { after = false }
                Choice(after, "After") { after = true }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Page No.", Modifier.padding(end = 12.dp))
                    OutlinedTextField(text, { text = digits(it); error = null }, singleLine = true,
                        keyboardOptions = numberKeys, modifier = Modifier.width(110.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Text("The document has $pageCount page${if (pageCount == 1) "" else "s"}.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = text.toIntOrNull()
                if (n == null || n < 1 || n > pageCount) error = "Please check the page number: it must be between 1 and $pageCount."
                else onConfirm(after, n)
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Delete Page: current page, or page numbers such as 3 or 2,4-6. Returns 0-based indices. */
@Composable
fun DeletePagesDialog(pageCount: Int, current: Int, onDismiss: () -> Unit, onConfirm: (List<Int>) -> Unit) {
    var useCurrent by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Page") },
        text = {
            Column {
                Choice(useCurrent, "Current page ($current)") { useCurrent = true; error = null }
                Choice(!useCurrent, "Page number(s)") { useCurrent = false; error = null }
                OutlinedTextField(text, { v -> text = v.filter { it.isDigit() || it == ',' || it == '-' }; useCurrent = false; error = null },
                    singleLine = true, label = { Text("e.g. 3 or 2,4-6") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                Text("The document has $pageCount page${if (pageCount == 1) "" else "s"}. Undo is available.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pages: List<Int>? = if (useCurrent) listOf(current - 1) else try { parsePageRange(text) } catch (e: Exception) { null }
                if (pages.isNullOrEmpty()) error = "Enter page numbers such as 3 or 2,4-6."
                else if (pages.any { it < 0 || it >= pageCount }) error = "Please check the page number: it must be between 1 and $pageCount."
                else if (pages.size >= pageCount) error = "You cannot delete all pages of the document."
                else onConfirm(pages.distinct().sorted())
            }) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Combine files: should the chosen files go before or after the current document? */
@Composable
fun CombinePositionDialog(onDismiss: () -> Unit, onConfirm: (Boolean) -> Unit) {
    var before by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Combine files") },
        text = {
            Column {
                Text("Where should the files you choose be placed?")
                Spacer(Modifier.height(8.dp))
                Choice(before, "Before the current file") { before = true }
                Choice(!before, "After the current file") { before = false }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(before) }) { Text("Choose files") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/**
 * Split files: ask for the number of files. If it equals the page count, every page becomes a file automatically;
 * otherwise ask for the from/to pages of each file. Returns 0-based inclusive ranges.
 */
@Composable
fun SplitDialog(pageCount: Int, onDismiss: () -> Unit, onConfirm: (List<IntRange>) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var countText by remember { mutableStateOf("") }
    val froms = remember { mutableStateListOf<String>() }
    val tos = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Split files") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (pageCount < 2) {
                    Text("This document has only one page, so it cannot be split.")
                } else if (step == 1) {
                    Text("The document has $pageCount pages.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(countText, { countText = digits(it); error = null }, singleLine = true,
                        label = { Text("Number of files") }, keyboardOptions = numberKeys, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Choose the pages of each file (1 to $pageCount).")
                    froms.indices.forEach { i ->
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("File ${i + 1}", Modifier.width(56.dp))
                            OutlinedTextField(froms[i], { froms[i] = digits(it); error = null }, singleLine = true, label = { Text("From") },
                                keyboardOptions = numberKeys, modifier = Modifier.weight(1f))
                            OutlinedTextField(tos[i], { tos[i] = digits(it); error = null }, singleLine = true, label = { Text("To") },
                                keyboardOptions = numberKeys, modifier = Modifier.weight(1f))
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            if (pageCount >= 2) TextButton(onClick = {
                if (step == 1) {
                    val n = countText.toIntOrNull()
                    if (n == null || n < 2 || n > pageCount) error = "Enter a number of files between 2 and $pageCount."
                    else if (n == pageCount) onConfirm((0 until pageCount).map { it..it })
                    else {
                        // Suggest an even split; the user can change every range.
                        froms.clear(); tos.clear()
                        val base = pageCount / n; val extra = pageCount % n; var start = 1
                        for (i in 0 until n) {
                            val size = base + if (i < extra) 1 else 0
                            froms += start.toString(); tos += (start + size - 1).toString(); start += size
                        }
                        step = 2; error = null
                    }
                } else {
                    val ranges = froms.indices.map { i -> (froms[i].toIntOrNull() ?: 0)..(tos[i].toIntOrNull() ?: 0) }
                    if (ranges.any { it.first < 1 || it.last > pageCount || it.first > it.last }) error = "Check the pages: each range must be within 1 to $pageCount, and 'From' cannot be after 'To'."
                    else onConfirm(ranges.map { (it.first - 1)..(it.last - 1) })
                }
            }) { Text(if (step == 1) "Next" else "Choose folder") }
        },
        dismissButton = {
            if (step == 2) TextButton(onClick = { step = 1; error = null }) { Text("Back") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        })
}