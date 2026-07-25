package com.utility.pdf.pdfcompressor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

object CompressoPDF {

    /**
     * Compress PDF by downsampling images and removing metadata.
     *
     * @param inputPath  Path to the original PDF
     * @param outputPath Path to save compressed PDF
     * @param dpi        Target DPI (use 72–300 for standard quality)
     * @param quality    JPEG quality (0–100)
     */
    suspend fun compressPdf(
        inputPath: String,
        outputPath: String,
        dpi: Int,
        quality: Int
    ) = withContext(Dispatchers.IO) {

        var document: PDDocument? = null

        try {
            document = PDDocument.load(File(inputPath))

            // 🔐 Handle encrypted PDFs safely
            if (document.isEncrypted) {
                document.setAllSecurityToBeRemoved(true)
            }

            // Remove metadata
            document.documentInformation?.cosObject?.clear()

            for (page in document.pages) {
                val resources: PDResources = page.resources
                val xObjectNames = resources.xObjectNames

                for (xObjectName in xObjectNames) {
                    val xObject: PDXObject = resources.getXObject(xObjectName) ?: continue

                    if (xObject is PDImageXObject) {
                        val image = xObject

                        // Skip non-JPEG images (prevents black images)
                        if (image.suffix != "jpg" && image.suffix != "jpeg") {
                            continue
                        }
                        if (image.isStencil) {
                            continue
                        }

                        // Load bitmap safely with sampling to reduce memory usage
                        val bitmap = image.image
                        if (bitmap.width < 50 || bitmap.height < 50) continue

                        // Apply DPI scaling
                        val scaleFactor = dpi / 72f // 72 = default PDF DPI
                        val newWidth = (bitmap.width * scaleFactor).toInt()
                        val newHeight = (bitmap.height * scaleFactor).toInt()

                        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

                        // Compress to JPEG with quality
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

                        // Replace original image
                        val compressedImage = PDImageXObject.createFromByteArray(
                            document,
                            baos.toByteArray(),
                            "compressed"
                        )
                        resources.put(xObjectName, compressedImage)

                        // Free memory
                        scaledBitmap.recycle()
                    }
                }
            }

            // Save the compressed PDF
            document.save(outputPath)
            Log.d("CompressoPDF", "PDF compressed successfully: $outputPath")

        } catch (e: InvalidPasswordException) {
            Log.e("CompressoPDF", "Password protected PDF", e)
            throw e   // Let UI handle message

        } catch (e: IOException) {
            Log.e("CompressoPDF", "Compression failed", e)
            throw e
        } finally {
            document?.close()
        }
    }
}
