package com.utility.pdf.pdfcompressor

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfFullAiEditor {

    // --------------------------------
    // 1️⃣ FAST TEXT EXTRACTION
    // --------------------------------
    fun extractAllText(pdfPath: String): String {
        PDDocument.load(File(pdfPath)).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return stripper.getText(doc)
        }
    }


    // --------------------------------
    // 2️⃣ REBUILD PDF WITH NEW TEXT
    // --------------------------------
    fun rebuildPdfWithText(
        originalPath: String,
        outputPath: String,
        newText: String
    ) {

        PDDocument.load(File(originalPath)).use { doc ->

            val lines = newText.split("\n")

            val font = PDType1Font.HELVETICA
            val fontSize = 11f

            var lineIndex = 0

            for (page in doc.pages) {

                val content = PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.OVERWRITE,
                    false
                )

                content.beginText()
                content.setFont(font, fontSize)

                val marginX = 40f
                var y = page.mediaBox.height - 40f
                val lineHeight = fontSize + 4f

                while (lineIndex < lines.size && y > 40f) {
                    content.newLineAtOffset(marginX, y)
                    content.showText(lines[lineIndex])
                    content.newLineAtOffset(-marginX, -y)

                    y -= lineHeight
                    lineIndex++
                }

                content.endText()
                content.close()
            }

            doc.save(outputPath)
        }

//        Log.d("PdfFullAiEditor", "Saved edited PDF → $outputPath")
    }
}
