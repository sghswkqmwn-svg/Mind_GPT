package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.model.ChatMessage
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    fun formatChatAsText(title: String, messages: List<ChatMessage>): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val sb = StringBuilder()
        sb.appendLine("MindGPT - The Ultimate Minecraft AI Assistant")
        sb.appendLine("Created by Loyal LP")
        sb.appendLine("Chat: $title")
        sb.appendLine("Exported: $dateStr")
        sb.appendLine("=".repeat(48))
        sb.appendLine()
        messages.forEach { msg ->
            val speaker = if (msg.role == "user") "YOU" else "MINDGPT"
            sb.appendLine("[$speaker]")
            sb.appendLine(msg.content)
            sb.appendLine()
            sb.appendLine("-".repeat(32))
            sb.appendLine()
        }
        return sb.toString()
    }

    fun writeTxtToUri(context: Context, uri: Uri, title: String, messages: List<ChatMessage>): Result<Unit> =
        runCatching {
            val content = formatChatAsText(title, messages)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Could not open destination file")
        }

    fun writePdfToUri(context: Context, uri: Uri, title: String, messages: List<ChatMessage>): Result<Unit> =
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                renderChatPdf(title, messages, out)
            } ?: error("Could not open destination PDF file")
        }

    fun shareChatAsTxt(context: Context, title: String, messages: List<ChatMessage>) {
        val content = formatChatAsText(title, messages)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "mindgpt_chat" }
        val file = File(dir, "$safeName.txt")
        file.writeText(content, Charsets.UTF_8)
        shareFile(context, file, "text/plain", "Export MindGPT Chat (TXT)")
    }

    fun shareChatAsPdf(context: Context, title: String, messages: List<ChatMessage>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32).ifBlank { "mindgpt_chat" }
        val file = File(dir, "$safeName.pdf")
        file.outputStream().use { out ->
            renderChatPdf(title, messages, out)
        }
        shareFile(context, file, "application/pdf", "Export MindGPT Chat (PDF)")
    }

    fun sharePlainText(context: Context, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun renderChatPdf(title: String, messages: List<ChatMessage>, out: OutputStream) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 44
        val contentWidth = pageWidth - (margin * 2)

        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val headerPaint = Paint().apply {
            color = Color.rgb(5, 150, 105)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = Typeface.SANS_SERIF
        }
        val rolePaint = TextPaint().apply {
            color = Color.rgb(4, 120, 87)
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.SANS_SERIF
        }

        var y = margin.toFloat()
        canvas.drawText("MindGPT — $title", margin.toFloat(), y + 14f, headerPaint)
        y += 26f
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("The Ultimate Minecraft AI Assistant • Created by Loyal LP • $dateStr", margin.toFloat(), y, metaPaint)
        y += 22f

        fun nextPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin.toFloat()
        }

        for (msg in messages) {
            val speaker = if (msg.role == "user") "YOU" else "MINDGPT"
            if (y > pageHeight - margin - 40) nextPage()
            rolePaint.color = if (msg.role == "user") Color.rgb(8, 145, 178) else Color.rgb(5, 150, 105)
            canvas.drawText(speaker, margin.toFloat(), y + 12f, rolePaint)
            y += 18f

            val layout = StaticLayout.Builder.obtain(
                msg.content.ifBlank { " " },
                0,
                msg.content.length.coerceAtLeast(1),
                bodyPaint,
                contentWidth
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()

            for (lineIdx in 0 until layout.lineCount) {
                val lineHeight = layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)
                if (y + lineHeight > pageHeight - margin) {
                    nextPage()
                }
                val start = layout.getLineStart(lineIdx)
                val end = layout.getLineEnd(lineIdx)
                val lineText = layout.text.subSequence(start, end).toString().trimEnd('\n')
                canvas.drawText(lineText, margin.toFloat(), y + lineHeight - 3f, bodyPaint)
                y += lineHeight
            }
            y += 14f
        }

        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }
}
