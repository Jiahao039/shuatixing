package com.shutixing.app.parser

import android.content.Context
import android.net.Uri
import com.shutixing.app.parser.model.TextLine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.StringWriter
import kotlin.coroutines.coroutineContext

class PdfImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

class PdfTextExtractor(private val context: Context) {

    suspend fun extract(
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<TextLine> = withContext(Dispatchers.IO) {
        PDFBoxResourceLoader.init(context)
        val lines = mutableListOf<TextLine>()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PdfImportException("无法打开文件")
        input.use { stream ->
            val document = try {
                PDDocument.load(stream)
            } catch (e: InvalidPasswordException) {
                throw PdfImportException("暂不支持加密 PDF", e)
            } catch (e: Exception) {
                throw PdfImportException("PDF 读取失败", e)
            }
            document.use { doc ->
                val total = doc.numberOfPages
                if (total == 0) throw PdfImportException("PDF 没有页面")
                for (page in 1..total) {
                    coroutineContext.ensureActive()
                    val stripper = PositionalStripper().apply {
                        startPage = page
                        endPage = page
                        pageIndex = page - 1
                        sortByPosition = true
                    }
                    stripper.writeText(doc, StringWriter())
                    lines.addAll(stripper.collected)
                    onProgress(page, total)
                }
            }
        }
        lines
    }

    private class PositionalStripper : PDFTextStripper() {
        val collected = mutableListOf<TextLine>()
        var pageIndex: Int = 0

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val cleaned = text.replace("\r", "").trim()
            if (cleaned.isEmpty()) return
            val top = textPositions.firstOrNull()?.yDirAdj ?: 0f
            collected.add(TextLine(text = cleaned, page = pageIndex, top = top))
        }
    }
}
