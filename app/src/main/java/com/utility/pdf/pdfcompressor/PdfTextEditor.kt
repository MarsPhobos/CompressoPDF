package com.utility.pdf.pdfcompressor

import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

enum class EditResult {
    NOT_EDITABLE,
    TEXT_NOT_FOUND,
    SUCCESS
}
object PdfTextEditor {

//    fun replaceTextSafely(
//        inputFile: String,
//        outputFile: String,
//        findText: String,
//        replaceText: String
//    ): Int {
//
//        var replacements = 0
//
//        PDDocument.load(File(inputFile)).use { document ->
//
//            // 🔐 Reject encrypted PDFs
//            if (document.isEncrypted) return replacements
//
//            // 🔒 Layout safety rule
//            if (findText.length != replaceText.length) return replacements
//
//            for (page in document.pages) {
//
//                val input: InputStream = try {
//                    page.contents ?: continue
//                } catch (e: Exception) {
//                    continue
//                }
//
//                val bytes = try {
//                    input.readBytes()
//                } catch (e: Exception) {
//                    continue
//                }
//
//                val contentStr = String(bytes, Charset.forName("ISO-8859-1"))
//
//                // Only simple text operators
//                if (!contentStr.contains("Tj")) continue
//                if (!contentStr.contains(findText)) continue
//
//                val newContent = contentStr.replace(findText, replaceText)
//                replacements += countMatches(contentStr, findText)
//
//                val newStream = PDStream(
//                    document,
//                    ByteArrayInputStream(newContent.toByteArray(Charsets.ISO_8859_1))
//                )
//
//                // ✅ Correct way to update page contents
//                page.setContents(newStream)
//            }
//
//            document.save(outputFile)
//        }
//
//        return replacements
//    }

    fun replaceTextSafely(
        inputFile: String,
        outputFile: String,
        findText: String,
        replaceText: String
    ): Int {

        var replacements = 0
        var editable = false

        PDDocument.load(File(inputFile)).use { document ->

            if (document.isEncrypted) {
                Log.w("PdfTextEditor", "PDF is encrypted → not editable")
                return 0
            }

            if (findText.length != replaceText.length) {
                Log.w("PdfTextEditor", "Length mismatch → unsafe edit")
                return 0
            }

            for (page in document.pages) {

                val input = try {
                    page.contents ?: continue
                } catch (e: Exception) {
                    Log.w("PdfTextEditor", "Failed to read page contents", e)
                    continue
                }

                val bytes = try {
                    input.readBytes()
                } catch (e: Exception) {
                    Log.w("PdfTextEditor", "Failed to read stream bytes", e)
                    continue
                }

                val contentStr = String(bytes, Charsets.ISO_8859_1)
                // DEBUG
//                val preview = contentStr.take(2000)
//                Log.d("PdfDebug", "----- PAGE CONTENT START -----")
//                Log.d("PdfDebug", preview)
//                Log.d("PdfDebug", "----- PAGE CONTENT END -----")

                if (!contentStr.contains("Tj")) {
                    continue
                }

                editable = true

                if (!contentStr.contains(findText)) {
                    continue
                }

                val newContent = contentStr.replace(findText, replaceText)
                replacements += countMatches(contentStr, findText)

                val newStream = PDStream(
                    document,
                    ByteArrayInputStream(newContent.toByteArray(Charsets.ISO_8859_1))
                )

                page.setContents(newStream)
            }

            if (!editable) {
                Log.i("PdfTextEditor", "PDF not editable (no simple Tj operators)")
                return 0
            }

            if (replacements == 0) {
                Log.i("PdfTextEditor", "PDF editable but text NOT found: \"$findText\"")
                return 0
            }

            document.save(outputFile)
            Log.i("PdfTextEditor", "Edit success, replacements=$replacements")
        }

        return replacements
    }


    fun canEditSafely(pdfFile: File): Boolean {
        var editable = false

        PDDocument.load(pdfFile).use { document ->
            if (document.isEncrypted) return editable

            for (page in document.pages) {

                val input: InputStream = try {
                    page.contents ?: continue
                } catch (e: Exception) {
                    continue
                }

                val bytes = try {
                    input.readBytes()
                } catch (e: Exception) {
                    continue
                }

                val contentStr = String(bytes, Charsets.ISO_8859_1)
                // DEBUG
//                val preview = contentStr.take(2000)
//                Log.d("PdfDebug", "----- PAGE CONTENT START -----")
//                Log.d("PdfDebug", preview)
//                Log.d("PdfDebug", "----- PAGE CONTENT END -----")

                if (contentStr.contains("Tj")) {
                    editable = true
                    break
                }
            }
        }

        return editable
    }

    private fun countMatches(text: String, pattern: String): Int {
        return Regex(Regex.escape(pattern)).findAll(text).count()
    }
}
