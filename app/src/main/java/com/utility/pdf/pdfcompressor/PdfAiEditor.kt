package com.utility.pdf.pdfcompressor

import android.graphics.*
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.max

object PdfAiEditor {

    /**
     * findReplaceMap:
     *
     */
    suspend fun replaceTextImageMode(
        inputPath: String,
        outputPath: String,
        findReplaceMap: Map<String, String>
    ): Int {

        var replacements = 0

        PDDocument.load(File(inputPath)).use { document ->

            val renderer = PDFRenderer(document)
            val newDoc = PDDocument()

            val recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            for (i in 0 until document.numberOfPages) {

                Log.d("PdfAiEditor", "Rendering page $i")

                val bitmap =
                    renderer.renderImageWithDPI(i, 300f, ImageType.RGB)

                val canvas = Canvas(bitmap)

                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(image).await()

                for (block in result.textBlocks.toList()) {
                    for (line in block.lines.toList()) {
                        for (element in line.elements.toList()) {

                            val originalText = element.text

                            val replaceWith = findReplaceMap.entries
                                .firstOrNull { originalText.contains(it.key) }
                                ?.value ?: continue

                            val box = element.boundingBox ?: continue

                            // -------------------------
                            // AUTO FONT SIZE
                            // -------------------------
                            val fontSize =
                                max(18f, box.height() * 0.9f)

                            // -------------------------
                            // SAMPLE ORIGINAL COLOR
                            // -------------------------
                            val sampleX = box.centerX()
                            val sampleY = box.centerY()

//                            val sampledColor =
//                                bitmap.getPixel(sampleX, sampleY)

                            val sampledColor = sampleAverageColor(bitmap, box)
                            val bold = isProbablyBold(bitmap, box)


                            // -------------------------
                            // ERASE AREA
                            // -------------------------
                            val erasePaint = Paint().apply {
                                color = Color.WHITE
                                style = Paint.Style.FILL
                            }

                            canvas.drawRect(box, erasePaint)

                            // -------------------------
                            // DRAW NEW TEXT  ✅ FIXED
                            // -------------------------
//                            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
////                                color = sampledColor
//                                color = if (sampledColor == Color.WHITE) Color.BLACK else sampledColor
//                                textSize = fontSize
//                                style = Paint.Style.FILL
//                                textAlign = Paint.Align.LEFT
//
//                                // smoother text
//                                isDither = true
//                                isSubpixelText = true
//                                typeface = Typeface.DEFAULT
//                            }
                            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (sampledColor == Color.WHITE) Color.BLACK else sampledColor
                                textSize = fontSize
                                isDither = true
                                isSubpixelText = true
                                isLinearText = true
                                hinting = Paint.HINTING_ON


//                                typeface =
//                                    if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
//                                    else Typeface.DEFAULT

                                typeface = detectTypeface(bitmap, box, bold)


                                style = if (bold) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL
                                strokeWidth = if (bold) 1.2f else 0f
                            }



//                            textPaint.typeface =
//                                if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
//                                else Typeface.DEFAULT
//
//                            textPaint.strokeWidth = if (bold) 1.2f else 0f
//                            textPaint.style = if (bold) Paint.Style.FILL_AND_STROKE else Paint.Style.FILL



                            // ⭐ baseline correction (CRITICAL FIX)
                            val baselineY = box.bottom.toFloat() - textPaint.descent()

//                            canvas.drawText(
//                                replaceWith,
////                                box.left.toFloat(),
//                                box.left.toFloat() + 2f, // small padding for nicer alignment
//                                baselineY,
//                                textPaint
//                            )

                            val originalWidth = textPaint.measureText(originalText)
                            val newWidth = textPaint.measureText(replaceWith)

                            // shrink if longer
                            if (newWidth > originalWidth) {
                                textPaint.textScaleX = originalWidth / newWidth
                            } else {
                                textPaint.textScaleX = 1f
                            }

                            // center alignment
                            val drawX = box.left + (originalWidth - newWidth) / 2f

                            val letterSpacing =
                                computeLetterSpacing(textPaint, replaceWith, originalWidth)

//                            textPaint.letterSpacing = letterSpacing

                            canvas.drawText(replaceWith, drawX, baselineY, textPaint)






                            replacements++
                        }
                    }
                }

                // add bitmap page to new PDF
                val page = PDPage()
                newDoc.addPage(page)

                val imageX =
                    LosslessFactory.createFromImage(newDoc, bitmap)

                val stream = PDPageContentStream(newDoc, page)

                stream.drawImage(
                    imageX,
                    0f,
                    0f,
                    page.mediaBox.width,
                    page.mediaBox.height
                )

                stream.close()
            }

            if (replacements > 0) {
                newDoc.save(outputPath)
            }
            newDoc.close()
        }

        return replacements
    }

    suspend fun replaceLineImageMode(
        inputPath: String,
        outputPath: String,
        findReplaceMap: Map<String, String>
    ): Int {

        var replacements = 0

        PDDocument.load(File(inputPath)).use { document ->

            val renderer = PDFRenderer(document)
            val newDoc = PDDocument()

            val recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            for (pageIndex in 0 until document.numberOfPages) {

                Log.d("PdfAiEditor", "Rendering page $pageIndex")

                // ----------------------------------
                // Render page to bitmap (HIGH QUALITY)
                // ----------------------------------
                val bitmap =
                    renderer.renderImageWithDPI(pageIndex, 300f, ImageType.RGB)

                val canvas = Canvas(bitmap)

                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(image).await()

                // ----------------------------------
                // LINE LEVEL REPLACEMENT
                // ----------------------------------
                for (block in result.textBlocks) {

                    for (line in block.lines) {

                        val originalLine = line.text

                        // find if this line needs replacement
                        val entry = findReplaceMap.entries
                            .firstOrNull { originalLine.contains(it.key) }
                            ?: continue

                        val replacedLine =
                            originalLine.replace(entry.key, entry.value)

                        val box = line.boundingBox ?: continue

                        // ----------------------------------
                        // FONT SIZE ESTIMATION
                        // ----------------------------------
                        val fontSize = max(18f, box.height() * 0.9f)

                        // ----------------------------------
                        // COLOR DETECTION
                        // ----------------------------------
                        val sampledColor = sampleAverageColor(bitmap, box)

                        // ----------------------------------
                        // BOLD DETECTION
                        // ----------------------------------
                        val bold = isProbablyBold(bitmap, box)

                        // ----------------------------------
                        // ERASE ORIGINAL LINE
                        // ----------------------------------
                        val erasePaint = Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.FILL
                        }

                        canvas.drawRect(box, erasePaint)

                        // ----------------------------------
                        // TEXT PAINT
                        // ----------------------------------
                        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {

                            color =
                                if (sampledColor == Color.WHITE) Color.BLACK
                                else sampledColor

                            textSize = fontSize

                            isDither = true
                            isSubpixelText = true

                            typeface =
                                if (bold)
                                    Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                else Typeface.DEFAULT

                            style =
                                if (bold)
                                    Paint.Style.FILL_AND_STROKE
                                else Paint.Style.FILL

                            strokeWidth = if (bold) 1.2f else 0f
                            textAlign = Paint.Align.LEFT
                        }

                        // ----------------------------------
                        // LETTER SPACING AUTO-FIT
                        // ----------------------------------
                        val originalWidth = textPaint.measureText(originalLine)
                        val newWidth = textPaint.measureText(replacedLine)

                        if (newWidth > 0f) {
                            textPaint.textScaleX = originalWidth / newWidth
                        }

                        // ----------------------------------
                        // BASELINE FIX (CRITICAL)
                        // ----------------------------------
                        val baselineY =
                            box.bottom.toFloat() - textPaint.descent()

                        // ----------------------------------
                        // DRAW NEW LINE
                        // ----------------------------------
                        canvas.drawText(
                            replacedLine,
                            box.left.toFloat(),
                            baselineY,
                            textPaint
                        )

                        replacements++
                    }
                }

                // ----------------------------------
                // ADD PAGE TO NEW PDF
                // ----------------------------------
                val page = PDPage()
                newDoc.addPage(page)

                val imageX =
                    LosslessFactory.createFromImage(newDoc, bitmap)

                PDPageContentStream(newDoc, page).use { stream ->
                    stream.drawImage(
                        imageX,
                        0f,
                        0f,
                        page.mediaBox.width,
                        page.mediaBox.height
                    )
                }
            }

            // ----------------------------------
            // ONLY SAVE IF CHANGES MADE  ⭐ IMPORTANT
            // ----------------------------------
            if (replacements > 0) {
                newDoc.save(outputPath)
            } else {
                Log.d("PdfAiEditor", "No replacements → skipping file creation")
            }


            newDoc.close()
        }

        Log.d("PdfAiEditor", "Line replacements: $replacements")

        return replacements
    }


    fun sampleAverageColor(bitmap: Bitmap, rect: Rect): Int {
        var r = 0
        var g = 0
        var b = 0
        var count = 0

        val step = 3 // speed vs quality

        for (x in rect.left until rect.right step step) {
            for (y in rect.top until rect.bottom step step) {
                val c = bitmap.getPixel(x, y)

                // ignore near white background
                if (Color.red(c) > 240 &&
                    Color.green(c) > 240 &&
                    Color.blue(c) > 240) continue

                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                count++
            }
        }

        return if (count == 0) Color.BLACK
        else Color.rgb(r / count, g / count, b / count)
    }

    fun isProbablyBold(bitmap: Bitmap, rect: Rect): Boolean {
        var darkPixels = 0
        var total = 0

        val step = 2

        for (x in rect.left until rect.right step step) {
            for (y in rect.top until rect.bottom step step) {
                val c = bitmap.getPixel(x, y)

                val brightness =
                    (Color.red(c) + Color.green(c) + Color.blue(c)) / 3

                if (brightness < 140) darkPixels++
                total++
            }
        }

        val ratio = darkPixels.toFloat() / total
        return ratio > 0.35f // threshold
    }

    fun computeLetterSpacing(
        paint: Paint,
        text: String,
        targetWidth: Float
    ): Float {

        if (text.length <= 1) return 0f

        val naturalWidth = paint.measureText(text)
        val extra = targetWidth - naturalWidth

        if (extra <= 0f) return 0f

        // spacing per character (em based)
        return (extra / (text.length - 1)) / paint.textSize
    }

    fun detectTypeface(bitmap: Bitmap, rect: Rect, bold: Boolean): Typeface {

        var edgePixels = 0
        var total = 0

        val step = 2

        for (x in rect.left until rect.right step step) {
            for (y in rect.top until rect.bottom step step) {

                val c = bitmap.getPixel(x, y)
                val brightness =
                    (Color.red(c) + Color.green(c) + Color.blue(c)) / 3

                if (brightness < 120) {
                    edgePixels++
                }
                total++
            }
        }

        val density = edgePixels.toFloat() / total

        return when {
            density > 0.55f ->
                Typeface.MONOSPACE

            density < 0.32f ->
                Typeface.SERIF

            else ->
                Typeface.SANS_SERIF
        }.let {
            if (bold) Typeface.create(it, Typeface.BOLD) else it
        }
    }




}
