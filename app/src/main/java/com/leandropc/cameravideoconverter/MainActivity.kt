package com.leandropc.cameravideoconverter

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private enum class CropMode { FULL, TOP, BOTTOM }

    private val executor = Executors.newSingleThreadExecutor()
    private var selectedVideo: Uri? = null
    private var cropMode = CropMode.BOTTOM
    private var outputUri: Uri? = null
    private lateinit var fileLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var convertButton: Button

    private val pickVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedVideo = uri
            fileLabel.text = "Video seleccionado:\n${queryDisplayName(uri)}"
            statusLabel.text = "Elegí el lente que querés guardar."
            convertButton.isEnabled = true
        }
    }

    private val createOutput = registerForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        if (uri != null) {
            outputUri = uri
            startConversion()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashFile = File(filesDir, "ultimo_error.txt")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { crashFile.writeText(android.util.Log.getStackTraceString(e).take(1500)) } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
        buildInterface()
        if (crashFile.exists()) {
            statusLabel.text = "La app se cerró por este error:\n\n" + crashFile.readText()
            crashFile.delete()
        } else if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                val ultimo = am.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull()
                val prefs = getSharedPreferences("diag", MODE_PRIVATE)
                if (ultimo != null && (ultimo.reason == 4 || ultimo.reason == 5) &&
                    ultimo.timestamp > prefs.getLong("visto", 0L)) {
                    prefs.edit().putLong("visto", ultimo.timestamp).apply()
                    val pasoFile = File(filesDir, "paso.txt")
                    val ultimoPaso = if (pasoFile.exists()) pasoFile.readText() else "sin dato"
                    statusLabel.text = "La app se cerró.\nMotivo: ${ultimo.reason} (4=error Java, 5=error nativo)\nDetalle: ${ultimo.description}\nÚltimo paso: $ultimoPaso"
                }
            } catch (_: Throwable) {}
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(0xFFF5F6FA.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(content)
        root.addView(scroll, ViewGroup.LayoutParams(-1, -1))

        content.addView(TextView(this).apply {
            text = "CAMERA VIDEO CONVERTER"
            textSize = 23f
            setTextColor(0xFF172554.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Convertí y separá los videos de tu cámara doble lente"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFF475569.toInt())
            setPadding(0, 0, 0, dp(22))
        })

        val chooseButton = Button(this).apply {
            text = "1. Seleccionar video"
            setOnClickListener { pickVideo.launch(arrayOf("video/*")) }
        }
        content.addView(chooseButton, matchWidth())
        fileLabel = TextView(this).apply {
            text = "Todavía no seleccionaste ningún video."
            textSize = 14f
            setTextColor(0xFF334155.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(18))
        }
        content.addView(fileLabel)

        content.addView(TextView(this).apply {
            text = "2. ¿Qué parte querés guardar?"
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xFF172554.toInt())
            setPadding(0, dp(8), 0, dp(6))
        })
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val full = RadioButton(this).apply { text = "Video completo (sin separar)" }
        val top = RadioButton(this).apply { text = "Solo lente superior" }
        val bottom = RadioButton(this).apply { text = "Solo lente inferior"; isChecked = true }
        group.addView(full); group.addView(top); group.addView(bottom)
        group.setOnCheckedChangeListener { _, checked ->
            cropMode = when (checked) {
                full.id -> CropMode.FULL
                top.id -> CropMode.TOP
                else -> CropMode.BOTTOM
            }
        }
        // Assign stable IDs after adding so the listener can identify each choice.
        full.id = android.view.View.generateViewId()
        top.id = android.view.View.generateViewId()
        bottom.id = android.view.View.generateViewId()
        group.check(bottom.id)
        content.addView(group)

        convertButton = Button(this).apply {
            text = "3. Convertir y guardar"
            isEnabled = false
            setOnClickListener {
                val name = "video_${when(cropMode) {
                    CropMode.FULL -> "completo"
                    CropMode.TOP -> "lente_superior"
                    CropMode.BOTTOM -> "lente_inferior"
                }}.mp4"
                createOutput.launch(name)
            }
        }
        content.addView(convertButton, matchWidth())

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = ProgressBar.GONE
            isIndeterminate = true
        }
        content.addView(progress, matchWidth())
        statusLabel = TextView(this).apply {
            text = "El video se procesa en el teléfono. No se sube a Internet."
            textSize = 14f
            setTextColor(0xFF475569.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(8))
        }
        content.addView(statusLabel)
        setContentView(root)
    }

    private fun startConversion() {
        val sourceUri = selectedVideo ?: return
        val destinationUri = outputUri ?: return
        convertButton.isEnabled = false
        progress.visibility = ProgressBar.VISIBLE
        statusLabel.text = "Preparando video…"
        executor.execute {
            var inputFile: File? = null
            val outputFile = File(cacheDir, "converted_${System.currentTimeMillis()}.mp4")
            try {
                paso("copiando el video de entrada")
                inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.video")
                contentResolver.openInputStream(sourceUri).use { input ->
                    requireNotNull(input) { "No se pudo leer el video seleccionado." }
                    FileOutputStream(inputFile).use { output -> input.copyTo(output) }
                }

                val filter = when (cropMode) {
                    CropMode.FULL -> "scale='min(1080,iw)':-2"
                    CropMode.TOP -> "crop=iw:ih/2:0:0,scale='min(1080,iw)':-2"
                    CropMode.BOTTOM -> "crop=iw:ih/2:0:ih/2,scale='min(1080,iw)':-2"
                }
                val inputArg = "'" + inputFile.absolutePath + "'"
                val outputArg = "'" + outputFile.absolutePath + "'"
                val command = "-y -i $inputArg -vf \"$filter\" -c:v libopenh264 -b:v 2500k -pix_fmt yuv420p -c:a aac -b:a 128k -ar 44100 -ac 2 -movflags +faststart $outputArg"
                paso("ejecutando FFmpeg con libopenh264")
                var session = FFmpegKit.execute(command)
                paso("FFmpeg termino el primer intento, codigo=${session.returnCode}")

                // Si el codificador H.264 por software no esta disponible, se usa MPEG-4.
                if (!ReturnCode.isSuccess(session.returnCode)) {
                    outputFile.delete()
                    val fallback = "-y -i $inputArg -vf \"$filter\" -c:v mpeg4 -q:v 4 -pix_fmt yuv420p -c:a aac -b:a 128k -ar 44100 -ac 2 -movflags +faststart $outputArg"
                    paso("ejecutando FFmpeg con mpeg4")
                    session = FFmpegKit.execute(fallback)
                    paso("FFmpeg termino el segundo intento, codigo=${session.returnCode}")
                }
                if (!ReturnCode.isSuccess(session.returnCode) || !outputFile.exists() || outputFile.length() == 0L) {
                    throw IllegalStateException("No se pudo convertir el video. Probá con otro archivo MP4.")
                }

                paso("copiando el resultado al destino")
                contentResolver.openOutputStream(destinationUri, "w").use { stream ->
                    requireNotNull(stream) { "No se pudo guardar el archivo elegido." }
                    outputFile.inputStream().use { it.copyTo(stream) }
                }
                paso("terminado sin problemas")
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    statusLabel.text = "¡Listo! El video se guardó correctamente."
                    convertButton.isEnabled = true
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    progress.visibility = ProgressBar.GONE
                    statusLabel.text = "Error: ${e.javaClass.simpleName}: ${e.message ?: "No se pudo procesar el video."}"
                    convertButton.isEnabled = true
                }
            } finally {
                inputFile?.delete()
                outputFile.delete()
            }
        }
    }

    private fun paso(msg: String) {
        try { File(filesDir, "paso.txt").writeText(msg) } catch (_: Throwable) {}
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "video seleccionado"
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        return name
    }

    private fun matchWidth() = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(10)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
