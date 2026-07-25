package com.utility.pdf.pdfcompressor

import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.utility.pdf.pdfcompressor.ui.OpenSourceLicensesScreen
import com.utility.pdf.pdfcompressor.ui.theme.CompressoPDFTheme
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PdfActionMode {
    NONE,
    COMPRESS,
    EDIT_AND_COMPRESS,
    AI_EDIT,
    AI_LINE_EDIT,

    FULL_AI_EDIT,
    VECTOR_EDIT
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var pickPdfLauncher: ActivityResultLauncher<String>

    private var actionMode by mutableStateOf(PdfActionMode.NONE)
    private var selectedPdfFile by mutableStateOf<File?>(null)
    private var compressedPdfFile by mutableStateOf<File?>(null)
    private var isCompressing by mutableStateOf(false)
    private var compressionDone by mutableStateOf(false)

    private var originalSizeKb by mutableStateOf(0L)
    private var compressedSizeKb by mutableStateOf(0L)

    private var selectedPdfName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PDFBoxResourceLoader.init(applicationContext)
        MobileAds.initialize(this)
        RewardedAdManager.load(this)




        // MUST be registered here — BEFORE setContent
        pickPdfLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    val fileName = getFileName(it)
                    val inputFile = File(getExternalFilesDir(null), fileName)
                    contentResolver.openInputStream(it)?.use { input ->
                        inputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    selectedPdfFile = inputFile
                    selectedPdfName = fileName
                    originalSizeKb = inputFile.length() / 1024
                    actionMode = PdfActionMode.NONE
                    compressedPdfFile = null
                    compressionDone = false
                    Toast.makeText(this, "PDF Selected", Toast.LENGTH_SHORT).show()
                }
            }

        setContent {
            CompressoPDFTheme {

                /** STATE (SCREEN SWITCH) */
                var showLicenses by remember { mutableStateOf(false) }

                if (showLicenses) {

                    /** 🔹 LICENSE SCREEN */
                    OpenSourceLicensesScreen(
                        onBack = { showLicenses = false }
                    )

                } else {

                    /** 🔹 MAIN SCREEN */
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("CompressoPDF") },
                                actions = {
                                    IconButton(onClick = { showLicenses = true }) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Open Source Licenses"
                                        )
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->

                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PdfCompressorScreen(
                                onPickPdf = { pickPdfLauncher.launch("application/pdf") },
                                onCompress = { targetKb ->
                                    selectedPdfFile?.let { compressPdf(it, targetKb) }
                                }
                            )
                        }
                    }
                }
            }
        }
//        val paths = listOf(
//            "com/tom_roush/pdfbox/resources/glyphlist/glyphlist.txt",
//            "com/tom_roush/pdfbox/resources/glyphlist/zapfdingbats.txt",
//            "raw/additional_glyphlist.txt"
//        )
//
//        paths.forEach {
//            try {
//                assets.open(it)
//                Log.d("PDFBOX", "$it FOUND")
//            } catch (e: Exception) {
//                Log.e("PDFBOX", "$it MISSING")
//            }
//        }

    }

    private fun getFileName(uri: Uri): String {
        var name = "selected.pdf"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index != -1 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
        return name
    }


    // ----------------------------
    // Background compression
    // ----------------------------
    private fun compressPdf(inputFile: File, targetKb: Int) {
        isCompressing = true
        compressionDone = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // PUBLIC DOWNLOADS FOLDER
                val downloadsDir =
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CompressoPDF")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val outputFile = File(
                    downloadsDir,
                    selectedPdfName!!.substringBeforeLast(".") + "_compressed.pdf"
                )
                // Start with safe defaults
                var dpi = 200
                var quality = 80

                // Loop until target KB reached
                while (true) {
                    CompressoPDF.compressPdf(
                        inputFile.absolutePath,
                        outputFile.absolutePath,
                        dpi,
                        quality
                    )

                    val sizeKb = outputFile.length() / 1024
                    if (sizeKb <= targetKb || quality <= 30) break

                    // Reduce gradually
                    dpi -= 20
                    quality -= 10
                }
                compressedPdfFile = outputFile
                compressedSizeKb = outputFile.length() / 1024
                compressionDone = true
                actionMode = PdfActionMode.NONE
                // makes file visible in Downloads
                MediaScannerConnection.scanFile(
                    this@MainActivity,
                    arrayOf(outputFile.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Compression done!", Toast.LENGTH_LONG).show()
                    if (compressedSizeKb > targetKb) {
                        Toast.makeText(
                            this@MainActivity,
                            "Target size too low, best achieved: ${outputFile.length() / 1024} KB",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: InvalidPasswordException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "This PDF is password-protected and cannot be compressed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Compression failed", Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            } finally {
                isCompressing = false
            }
        }
    }

    // ----------------------------
    // UI
    // ----------------------------
    @Composable
    private fun PdfCompressorScreen(
        onPickPdf: () -> Unit,
        onCompress: (Int) -> Unit
    ) {
        var targetKb by remember { mutableStateOf("300") }
        val keyboardController = LocalSoftwareKeyboardController.current
        var showRewardDialog by remember { mutableStateOf(false) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }


        // ⭐ Rewarded Ad dialog
        if (showRewardDialog) {
            AlertDialog(
                onDismissRequest = { showRewardDialog = false },

                title = { Text("Watch Ad Required") },

                text = {
                    Text("Watch a short ad to unlock 1 Line Edit.")
                },

                confirmButton = {
                    TextButton(onClick = {
                        showRewardDialog = false

                        RewardedAdManager.show(
                            activity = this@MainActivity,
                            onReward = {
                                pendingAction?.invoke()
                            }
                        )
                    }) {
                        Text("Watch Ad")
                    }
                },

                dismissButton = {
                    TextButton(onClick = {
                        showRewardDialog = false
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }



        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text("CompressoPDF", fontSize = 26.sp)

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onPickPdf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select PDF")
            }

            selectedPdfFile?.let {
                Spacer(Modifier.height(16.dp))
                Text("Selected: $selectedPdfName", fontSize = 14.sp)
                if (originalSizeKb > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Original size: $originalSizeKb KB",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Comments start

//                Spacer(Modifier.height(8.dp))
//
//
//                OutlinedTextField(
//                    value = targetKb,
//                    onValueChange = { newValue ->
//                        // ✅ Allow only digits
//                        if (newValue.all { it.isDigit() }) {
//                            targetKb = newValue
//                        }
//                    },
//                    label = { Text("Target size (KB)") },
//                    keyboardOptions = KeyboardOptions(
//                        keyboardType = KeyboardType.Number
//                    ),
//                    modifier = Modifier.fillMaxWidth()
//                )
//
//                Spacer(Modifier.height(16.dp))
//                Button(
//                    onClick = {
//                        keyboardController?.hide()
//                        val kb = targetKb.toIntOrNull()
//                        val maxSizeMb = 150
//                        val maxPages = 300
//
//                        if (kb == null || kb <= 0) {
//                            Toast.makeText(
//                                this@MainActivity,
//                                "Please enter a valid target size in KB",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                            return@Button
//                        }
//
//                        // Large PDF protection (BEFORE compression starts)
//                        val fileSizeMb = (selectedPdfFile!!.length() / (1024 * 1024))
//                        if (kb >= originalSizeKb) {
//                            Toast.makeText(
//                                this@MainActivity,
//                                "Target size is greater than or equal to original size.\nCompression not required.",
//                                Toast.LENGTH_LONG
//                            ).show()
//                            return@Button
//                        }
//                        if (fileSizeMb > maxSizeMb) {
//                            Toast.makeText(
//                                this@MainActivity,
//                                "PDF too large (>${fileSizeMb} MB). Max allowed is 150 MB",
//                                Toast.LENGTH_LONG
//                            ).show()
//                            return@Button
//                        }
//
//                        val pageCount = PDDocument.load(selectedPdfFile!!).use {
//                            it.numberOfPages
//                        }
//                        if (pageCount > maxPages) {
//                            Toast.makeText(
//                                this@MainActivity,
//                                "PDF has $pageCount pages. Limit is $maxPages pages",
//                                Toast.LENGTH_LONG
//                            ).show()
//                            return@Button
//                        }
//
//                        onCompress(kb)
//                    },
//                    enabled = !isCompressing,
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text(if (isCompressing) "Compressing…" else "Compress PDF")
//                }

                // Comments end
                Spacer(Modifier.height(20.dp))

                if (actionMode == PdfActionMode.NONE) {

                    Text(
                        text = "What do you want to do?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { actionMode = PdfActionMode.COMPRESS },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compress PDF")
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val editable = try {
                                PdfTextEditor.canEditSafely(selectedPdfFile!!)
                            } catch (e: Exception) {
                                false
                            }

                            if (!editable) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "This PDF does not support safe text editing.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                actionMode = PdfActionMode.EDIT_AND_COMPRESS
                            }
                        },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Edit & Compress PDF")
                    }

//                    Spacer(Modifier.height(12.dp))
//
//                    var showFlattenInfo by remember { mutableStateOf(false) }
//
//                    Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//
//                        OutlinedButton(
//                            onClick = {
//                                lifecycleScope.launch(Dispatchers.IO) {
//
////                                    val flattened = File(
////                                        getExternalFilesDir(null),
////                                        selectedPdfName!!.substringBeforeLast(".") + "_flattened.pdf"
////                                    )
//
//                                    val downloadsDir =
//                                        File(
//                                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
//                                            "CompressoPDF"
//                                        )
//
//                                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
//
//                                    val flattened = File(
//                                        downloadsDir,
//                                        selectedPdfName!!.substringBeforeLast(".") + "_flattened.pdf"
//                                    )
//
//
//                                    PdfFlattener.flattenToImagePdf(
//                                        selectedPdfFile!!.absolutePath,
//                                        flattened.absolutePath
//                                    )
//
//                                    MediaScannerConnection.scanFile(
//                                        this@MainActivity,
//                                        arrayOf(flattened.absolutePath),
//                                        arrayOf("application/pdf"),
//                                        null
//                                    )
//
//
//                                    withContext(Dispatchers.Main) {
//                                        Toast.makeText(
//                                            this@MainActivity,
////                                            "Created safe editable copy (image PDF)",
//                                            "Saved to Downloads/CompressoPDF",
//                                            Toast.LENGTH_LONG
//                                        ).show()
//
//                                        selectedPdfFile = flattened
//                                    }
//                                }
//                            },
//                            enabled = !isCompressing,
//                            modifier = Modifier.weight(1f)
//                        ) {
//                            Text("Flatten for Safe Editing")
//                        }
//
//                        Spacer(Modifier.width(8.dp))
//
//                        IconButton(
//                            onClick = { showFlattenInfo = !showFlattenInfo }
//                        ) {
//                            Icon(
//                                imageVector = Icons.Default.Info,
//                                contentDescription = "Info"
//                            )
//                        }
//                    }
//
//                    if (showFlattenInfo) {
//                        Spacer(Modifier.height(6.dp))
//
//                        Text(
//                            text = "Use this if editing fails. Keeps layout but text becomes non-selectable.",
//                            fontSize = 12.sp,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }

//                    Spacer(Modifier.height(6.dp))
//
//                    Text(
//                        text = "Use this if editing fails. Keeps layout but text becomes non-selectable.",
//                        fontSize = 12.sp,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )

                    Spacer(Modifier.height(12.dp))


                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.AI_EDIT },   // ⭐ ONLY SWITCH MODE
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Photoshop Text Edit (OCR)")
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.AI_LINE_EDIT },
//                                onClick = { showAdDialog = true },
                                modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AI Line Edit (Better Layout)")
                    }


                    Spacer(Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.FULL_AI_EDIT },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Full Edit (Rebuild PDF) v1")
                    }

                    Spacer(Modifier.height(12.dp))

//                    OutlinedButton(
//                        onClick = { actionMode = PdfActionMode.VECTOR_EDIT },
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Text("Smart Edit (Perfect Layout)")
//                    }






                }
                if (actionMode == PdfActionMode.COMPRESS) {

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = targetKb,
                        onValueChange = { newValue ->
                            // ✅ Allow only digits
                            if (newValue.all { it.isDigit() }) {
                                targetKb = newValue
                            }
                        },
                        label = { Text("Target size (KB)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            val kb = targetKb.toIntOrNull()
                            val maxSizeMb = 150
                            val maxPages = 300

                            if (kb == null || kb <= 0) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please enter a valid target size in KB",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            // Large PDF protection (BEFORE compression starts)
                            val fileSizeMb = (selectedPdfFile!!.length() / (1024 * 1024))
                            if (kb >= originalSizeKb) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Target size is greater than or equal to original size.\nCompression not required.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            if (fileSizeMb > maxSizeMb) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "PDF too large (>${fileSizeMb} MB). Max allowed is 150 MB",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }

                            val pageCount = PDDocument.load(selectedPdfFile!!).use {
                                it.numberOfPages
                            }
                            if (pageCount > maxPages) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "PDF has $pageCount pages. Limit is $maxPages pages",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }

                            onCompress(kb)
                        },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCompressing) "Compressing…" else "Compress PDF")
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.NONE },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }
                }
                if (actionMode == PdfActionMode.EDIT_AND_COMPRESS) {
                    var findText by remember { mutableStateOf("") }
                    var replaceText by remember { mutableStateOf("") }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Edit & Compress Supports simple text corrections without breaking layout in basic PDFs. Complex PDFs may not be editable.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find text") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (findText.isBlank() || replaceText.isBlank()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Enter both find and replace text",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            lifecycleScope.launch {
                                isCompressing = true
                                try {
                                    val editedFile = File(
                                        getExternalFilesDir(null),
                                        selectedPdfName!!.substringBeforeLast(".") + "_edited.pdf"
                                    )

                                    val count = PdfTextEditor.replaceTextSafely(
                                        selectedPdfFile!!.absolutePath,
                                        editedFile.absolutePath,
                                        findText,
                                        replaceText
                                    )

                                    if (count == 0) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Text not found or edit not supported without breaking layout.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        // stay in EDIT_AND_COMPRESS
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "$count change(s) applied successfully.",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        selectedPdfFile = editedFile
                                        actionMode = PdfActionMode.COMPRESS
                                    }
                                } finally {
                                    // 🔑 THIS UNFREEZES THE UI
                                    isCompressing = false
                                }
                            }

                        },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCompressing) "Working…" else "Apply Edit")
                    }


                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.NONE },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }
                }
                if (actionMode == PdfActionMode.AI_EDIT) {

                    var findText by remember { mutableStateOf("") }
                    var replaceText by remember { mutableStateOf("") }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "AI Edit uses OCR. Text becomes image-based.\nLayout preserved but text won't be selectable.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        label = { Text("Find text") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        label = { Text("Replace with") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {

                            if (findText.isBlank() || replaceText.isBlank()) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Enter both values",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            lifecycleScope.launch(Dispatchers.IO) {

                                isCompressing = true

                                try {
                                    val output = File(
                                        Environment.getExternalStoragePublicDirectory(
                                            Environment.DIRECTORY_DOWNLOADS
                                        ),
                                        "CompressoPDF/${selectedPdfName!!.substringBeforeLast(".")}_ai_edit.pdf"
                                    )

                                    if (!output.parentFile!!.exists())
                                        output.parentFile!!.mkdirs()

                                    val map = mapOf(findText to replaceText)

                                    val count = PdfAiEditor.replaceTextImageMode(
                                        selectedPdfFile!!.absolutePath,
                                        output.absolutePath,
                                        map
                                    )





                                    withContext(Dispatchers.Main) {

                                        if (count > 0) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "AI replaced $count text(s). Saved to Downloads/CompressoPDF",
                                                Toast.LENGTH_LONG
                                            ).show()

                                            MediaScannerConnection.scanFile(
                                                this@MainActivity,
                                                arrayOf(output.absolutePath),
                                                arrayOf("application/pdf"),
                                                null
                                            )
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Text not found by OCR",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        actionMode = PdfActionMode.NONE
                                    }

                                } finally {
                                    isCompressing = false
                                }
                            }
                        },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCompressing) "Processing…" else "Apply AI Edit")
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.NONE },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }
                }
                if (actionMode == PdfActionMode.AI_LINE_EDIT){
                    var findLine by remember { mutableStateOf("") }
                    var replaceLine by remember { mutableStateOf("") }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Line Edit uses OCR. Text becomes image-based.\nLayout preserved but text won't be selectable.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = findLine,
                        onValueChange = { findLine = it },
                        label = { Text("Find Line") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = replaceLine,
                        onValueChange = { replaceLine = it },
                        label = { Text("Replace with") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {

                            if (findLine.isBlank() || replaceLine.isBlank()) {
                                Toast.makeText(this@MainActivity, "Enter both values", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val runEdit: () -> Unit = {

                                lifecycleScope.launch(Dispatchers.IO) {

                                    if (!selectedPdfFile!!.exists()) return@launch

                                    isCompressing = true

                                    try {
                                        val output = File(
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS
                                            ),
                                            "CompressoPDF/${selectedPdfName!!.substringBeforeLast(".")}_line_edit.pdf"
                                        )

                                        if (!output.parentFile!!.exists())
                                            output.parentFile!!.mkdirs()

                                        val map = mapOf(findLine to replaceLine)

                                        val count = PdfAiEditor.replaceLineImageMode(
                                            selectedPdfFile!!.absolutePath,
                                            output.absolutePath,
                                            map
                                        )

                                        withContext(Dispatchers.Main) {

                                            if (count > 0) {

                                                RewardedAdManager.consumeCredit()

                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "AI replaced $count line(s)",
                                                    Toast.LENGTH_LONG
                                                ).show()

                                                MediaScannerConnection.scanFile(
                                                    this@MainActivity,
                                                    arrayOf(output.absolutePath),
                                                    arrayOf("application/pdf"),
                                                    null
                                                )

                                                actionMode = PdfActionMode.NONE
                                            } else {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Text not found by OCR",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }

                                    } finally {
                                        isCompressing = false
                                    }
                                }
                            }

                            // ⭐ GATE WITH CONSENT
                            if (RewardedAdManager.hasCredit()) {
                                runEdit()
                            } else {
                                pendingAction = runEdit
                                showRewardDialog = true
                            }
                        },
                        enabled = !isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCompressing) "Processing…" else "Apply AI Edit")
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = { actionMode = PdfActionMode.NONE },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back")
                    }

                }

                if (actionMode == PdfActionMode.FULL_AI_EDIT) {

                    var extractedText by remember { mutableStateOf("") }
                    var editedText by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            extractedText =
                                PdfFullAiEditor.extractAllText(selectedPdfFile!!.absolutePath)
                        }
                        editedText = extractedText
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 32.dp)
                    ) {

                        Text(
                            "Edit full document text below",
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 350.dp),   // ⭐ better than fixed height
                            label = { Text("Edit text") }
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {

                                lifecycleScope.launch(Dispatchers.IO) {

                                    val downloadsDir =
                                        File(
                                            Environment.getExternalStoragePublicDirectory(
                                                Environment.DIRECTORY_DOWNLOADS
                                            ),
                                            "CompressoPDF"
                                        )

                                    if (!downloadsDir.exists()) downloadsDir.mkdirs()

                                    val output = File(
                                        downloadsDir,
                                        selectedPdfName!!.substringBeforeLast(".") + "_full_edit.pdf"
                                    )

                                    PdfFullAiEditor.rebuildPdfWithText(
                                        selectedPdfFile!!.absolutePath,
                                        output.absolutePath,
                                        editedText
                                    )

                                    MediaScannerConnection.scanFile(
                                        this@MainActivity,
                                        arrayOf(output.absolutePath),
                                        arrayOf("application/pdf"),
                                        null
                                    )

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Saved to Downloads/CompressoPDF",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        actionMode = PdfActionMode.NONE
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Edited PDF")
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { actionMode = PdfActionMode.NONE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back")
                        }
                    }
                }

                if (actionMode == PdfActionMode.VECTOR_EDIT) {

                    var originalText by remember { mutableStateOf("") }
                    var editedText by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        withContext(Dispatchers.IO) {
                            originalText =
                                PdfVectorEditor.extractText(selectedPdfFile!!.absolutePath)
                        }
                        editedText = originalText
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {

                        Text(
                            "Smart Vector Edit (Pixel Perfect)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(500.dp),
                            label = { Text("Edit text") }
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {

                                lifecycleScope.launch(Dispatchers.IO) {

                                    val downloadsDir = File(
                                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                                        "CompressoPDF"
                                    )

                                    if (!downloadsDir.exists()) downloadsDir.mkdirs()

                                    val outputFile = File(
                                        downloadsDir,
                                        selectedPdfName!!.substringBeforeLast(".") + "_vector_edit.pdf"
                                    )

//                                    FileOutputStream(outputFile).use { out ->
//
//                                        val count = PdfVectorEditor.replaceTextVector(
//                                            selectedPdfFile!!.absolutePath,
//                                            out,                     // ⭐ REAL stream
//                                            originalText,
//                                            editedText,
//                                            cacheDir
//                                        )
//
//                                        out.flush()
//                                    }

                                    val count = FileOutputStream(outputFile).use { out ->
                                        PdfVectorEditor.replaceTextVector(
                                            selectedPdfFile!!.absolutePath,
                                            out,
                                            originalText,
                                            editedText,
                                            cacheDir
                                        )
                                    }

                                    if (count == 0) {
                                        outputFile.delete()

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "No text changes detected, Possibly Vector edit not supported for this PDF.\\nUse AI edit...",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        return@launch
                                    }

//                                    if (count == 0) {
//
//                                        outputFile.delete()
//
//                                        withContext(Dispatchers.Main) {
//                                            Toast.makeText(
//                                                this@MainActivity,
//                                                "Vector edit not supported for this PDF.\nSwitching to AI mode...",
//                                                Toast.LENGTH_LONG
//                                            ).show()
//                                        }
//
//                                        // ⭐ fallback automatically
//                                        val aiFile = File(
//                                            downloadsDir,
//                                            selectedPdfName!!.substringBeforeLast(".") + "_ai_edit.pdf"
//                                        )
//
//                                        PdfAiEditor.replaceTextImageMode(
//                                            selectedPdfFile!!.absolutePath,
//                                            aiFile.absolutePath,
//                                            mapOf(originalText to editedText)
//                                        )
//
//                                        MediaScannerConnection.scanFile(
//                                            this@MainActivity,
//                                            arrayOf(aiFile.absolutePath),
//                                            arrayOf("application/pdf"),
//                                            null
//                                        )
//
//                                        return@launch
//                                    }



                                    MediaScannerConnection.scanFile(
                                        this@MainActivity,
                                        arrayOf(outputFile.absolutePath),
                                        arrayOf("application/pdf"),
                                        null
                                    )

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Saved to Downloads/CompressoPDF",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        actionMode = PdfActionMode.NONE
                                    }
                                }



                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Smart Edit")
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { actionMode = PdfActionMode.NONE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back")
                        }
                    }
                }









            }

            if (compressionDone && compressedPdfFile != null && compressedSizeKb != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Compressed size: ${compressedSizeKb} KB",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Saved to Downloads/CompressoPDF",
                    fontSize = 12.sp
                )
            }
        }
    }
}
