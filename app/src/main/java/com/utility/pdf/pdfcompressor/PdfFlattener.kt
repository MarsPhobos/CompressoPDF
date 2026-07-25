package com.utility.pdf.pdfcompressor

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File

object PdfFlattener {

    /**
     * Converts ANY PDF → image-based PDF (safe editable copy)
     */
    fun flattenToImagePdf(inputPath: String, outputPath: String) {

        PDDocument.load(File(inputPath)).use { srcDoc ->

            val renderer = PDFRenderer(srcDoc)

            PDDocument().use { newDoc ->

                for (i in 0 until srcDoc.numberOfPages) {

//                    Log.d("PdfFlattener", "Rendering page $i")

                    // 1️⃣ Render page to bitmap (300dpi good quality)
                    val bitmap: Bitmap =
                        renderer.renderImageWithDPI(i, 120f)

                    // 2️⃣ Convert bitmap → JPEG
                    val image = JPEGFactory.createFromImage(newDoc, bitmap, 0.9f)

                    // 3️⃣ Create new page with same size
                    val pageSize: PDRectangle =
                        srcDoc.getPage(i).mediaBox

                    val page = PDPage(pageSize)
                    newDoc.addPage(page)

                    // 4️⃣ Draw image to page
                    val content = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(
                        newDoc,
                        page
                    )

                    content.drawImage(
                        image,
                        0f,
                        0f,
                        pageSize.width,
                        pageSize.height
                    )

                    content.close()

                    bitmap.recycle()
                }

                newDoc.save(outputPath)
            }
        }
    }
}
