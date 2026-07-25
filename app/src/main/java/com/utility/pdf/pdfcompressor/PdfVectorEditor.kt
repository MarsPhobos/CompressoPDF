package com.utility.pdf.pdfcompressor

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.OutputStream

object PdfVectorEditor {

    /**
     * Extract REAL selectable text (fast, no OCR)
     */
    fun extractText(path: String): String {
        PDDocument.load(File(path)).use { doc ->
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            return stripper.getText(doc)
        }
    }

    /**
     * Smart vector replace (pixel perfect)
     */
    fun replaceTextVector(
        inputPath: String,
        outputStream: OutputStream,
        original: String,
        edited: String,
        cacheDir: File
    ): Int {

        var count = 0

        val wordsOld = original.split("\\s+".toRegex())
        val wordsNew = edited.split("\\s+".toRegex())

        val replaceMap = mutableMapOf<String, String>()

        val min = minOf(wordsOld.size, wordsNew.size)

        for (i in 0 until min) {

            if (wordsOld[i] != wordsNew[i]) {

                val old = wordsOld[i]
                val new = wordsNew[i]

                // ⭐ keep SAME LENGTH (required for vector editing)
                val padded =
                    if (new.length < old.length)
                        new.padEnd(old.length, ' ')
                    else
                        new.take(old.length)

                replaceMap[old] = padded
            }
        }

        if (replaceMap.isEmpty()) return 0

        val tempFile = File(cacheDir, "vector_tmp.pdf")

        var workingInput = inputPath

        replaceMap.forEach { (find, replace) ->
            count += PdfTextEditor.replaceTextSafely(
                workingInput,
                tempFile.absolutePath,
                find,
                replace
            )
            workingInput = tempFile.absolutePath
        }

        // ⭐ SAFETY: avoid crash if nothing written
        if (!File(workingInput).exists()) return 0

        File(workingInput).inputStream().use { it.copyTo(outputStream) }

        tempFile.delete()

        return count
    }


}
