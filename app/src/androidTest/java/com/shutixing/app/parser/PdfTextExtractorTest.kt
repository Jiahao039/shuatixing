package com.shutixing.app.parser

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfTextExtractorTest {
    @Test
    fun extractsTextLinesFromPdf() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)

        val file = File(context.cacheDir, "sample.pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("1. What is 2 + 2?")
                cs.newLineAtOffset(0f, -20f)
                cs.showText("A. 3")
                cs.newLineAtOffset(0f, -20f)
                cs.showText("B. 4")
                cs.newLineAtOffset(0f, -20f)
                cs.showText("Answer: B")
                cs.endText()
            }
            doc.save(file)
        }

        val progress = mutableListOf<Pair<Int, Int>>()
        val lines = PdfTextExtractor(context).extract(Uri.fromFile(file)) { done, total ->
            progress.add(done to total)
        }

        assertTrue(lines.any { it.text.contains("What is 2 + 2") })
        assertTrue(lines.any { it.text.contains("Answer: B") })
        assertTrue(lines.all { it.page == 0 })
        assertTrue(progress.isNotEmpty())
    }
}
