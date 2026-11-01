package com.ismoilovdev.chipid.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.ismoilovdev.chipid.core.Mrz
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs on-device OCR on camera frames and reports an MRZ once two consecutive frames agree on the
 * chip key (number, birth date, expiry). Frames are processed in memory and never stored.
 */
class MrzAnalyzer(
    private val onCandidate: (Boolean) -> Unit,
    private val onMrz: (Mrz.Result) -> Unit,
) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)
    private val done = AtomicBoolean(false)
    private var last: Triple<String, String, String>? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || done.get() || !busy.compareAndSet(false, true)) {
            proxy.close(); return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { b -> b.lines.map { it.text } + b.text.split('\n') }
                // Looks like MRZ filler is visible: tell the UI we are close.
                onCandidate(lines.any { it.count { c -> c == '<' } >= 5 })
                val found = Mrz.find(lines)
                if (found != null) {
                    val key = Triple(found.documentNumber, found.dateOfBirth, found.dateOfExpiry)
                    if (key == last && done.compareAndSet(false, true)) onMrz(found)
                    last = key
                }
            }
            .addOnCompleteListener {
                busy.set(false)
                proxy.close()
            }
    }

    fun reset() {
        last = null
        done.set(false)
    }

    fun close() = recognizer.close()
}
