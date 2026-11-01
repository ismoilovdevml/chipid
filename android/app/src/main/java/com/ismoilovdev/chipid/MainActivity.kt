package com.ismoilovdev.chipid

import android.content.Intent
import android.hardware.camera2.CameraManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ismoilovdev.chipid.core.Mrz
import com.ismoilovdev.chipid.reader.AccessKey
import com.ismoilovdev.chipid.reader.ChipReader
import com.ismoilovdev.chipid.reader.ReadProgress
import com.ismoilovdev.chipid.reader.ReadReport
import com.ismoilovdev.chipid.reader.Stage
import com.ismoilovdev.chipid.reader.TrustStore
import com.ismoilovdev.chipid.ui.AboutScreen
import com.ismoilovdev.chipid.ui.ChipIdTheme
import com.ismoilovdev.chipid.ui.DetailsScreen
import com.ismoilovdev.chipid.ui.DocType
import com.ismoilovdev.chipid.ui.HelpContent
import com.ismoilovdev.chipid.ui.HomeScreen
import com.ismoilovdev.chipid.ui.ManualScreen
import com.ismoilovdev.chipid.ui.NfcScreen
import com.ismoilovdev.chipid.ui.ReportModel
import com.ismoilovdev.chipid.ui.ResultScreen
import com.ismoilovdev.chipid.ui.ScanScreen
import java.util.concurrent.Executors

private enum class Route { HOME, SCAN, MANUAL, NFC, HELP, RESULT, DETAILS, ABOUT }

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val io = Executors.newSingleThreadExecutor()
    private var nfc: NfcAdapter? = null

    private var route by mutableStateOf(Route.HOME)
    private var docType by mutableStateOf(DocType.PASSPORT)
    private var trust by mutableStateOf<TrustStore?>(null)
    private var key: AccessKey? = null
    private var prefill by mutableStateOf<AccessKey.Mrz?>(null)
    @Volatile private var reading = false
    private var progress by mutableStateOf<ReadProgress?>(null)
    private var error by mutableStateOf<String?>(null)
    private var report by mutableStateOf<ReadReport?>(null)
    private var nfcEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Personal data on screen: keep it out of screenshots and the recents thumbnail.
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        nfc = NfcAdapter.getDefaultAdapter(this)
        // Older builds could export chip files to shared app storage; remove any leftovers.
        io.execute { java.io.File(getExternalFilesDir(null), "export").deleteRecursively() }
        io.execute { val t = TrustStore.load(applicationContext); runOnUiThread { trust = t } }
        setContent { ChipIdTheme { App() } }
    }

    /**
     * Samsung's camera service stops NFC polling while any camera is open and, when the camera
     * closes, restarts the system default discovery, which silently drops this app's reader mode.
     * So reader mode is re-armed whenever a camera becomes available again.
     */
    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            // Fires once per camera id (the S24 has several); coalesce into one re-arm.
            main.removeCallbacks(rearm)
            main.postDelayed(rearm, 300)
        }
    }
    private val rearm = Runnable { if (resumed && !reading) armReader() }
    private val main = Handler(Looper.getMainLooper())
    private var resumed = false

    private fun armReader() {
        val adapter = nfc ?: return
        adapter.disableReaderMode(this)
        // Reader mode keeps wallet and other NFC apps from grabbing the document while ChipID is open.
        adapter.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000) },
        )
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        nfcEnabled = nfc?.isEnabled == true
        armReader()
        getSystemService(CameraManager::class.java).registerAvailabilityCallback(cameraCallback, main)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(cameraCallback)
        nfc?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        val k = key ?: return
        if (route != Route.NFC || reading) return
        reading = true
        runOnUiThread { error = null; progress = ReadProgress(Stage.FOUND, 0.02f, "Chip found") }
        io.execute {
            try {
                val r = ChipReader.read(tag, k, trust?.anchors.orEmpty()) { p -> runOnUiThread { progress = p } }
                runOnUiThread {
                    report = r
                    key = null // the key has done its job; do not keep it around
                    prefill = null
                    route = Route.RESULT
                }
            } catch (e: Exception) {
                runOnUiThread { error = e.message ?: e.javaClass.simpleName }
            } finally {
                reading = false
            }
        }
    }

    private fun startRead(k: AccessKey) {
        key = k
        progress = null
        error = null
        route = Route.NFC
        // Belt and braces for the camera hand-off: the camera is released as the scan screen leaves.
        main.postDelayed({ if (resumed && !reading && route == Route.NFC) armReader() }, 800)
    }

    private fun resetAll() {
        key = null; prefill = null; report = null; progress = null; error = null
        route = Route.HOME
    }

    @Composable
    private fun App() {
        BackHandler(enabled = route != Route.HOME) {
            route = when (route) {
                Route.SCAN, Route.ABOUT -> Route.HOME
                Route.MANUAL -> if (prefill != null) Route.SCAN else Route.HOME
                Route.NFC -> { key = null; Route.HOME }
                Route.HELP -> Route.NFC
                Route.DETAILS -> Route.RESULT
                Route.RESULT -> { resetAll(); Route.HOME }
                Route.HOME -> Route.HOME
            }
        }
        when (route) {
            Route.HOME -> HomeScreen(
                docType, onSelect = { docType = it },
                onStart = { route = Route.SCAN },
                onSettings = { route = Route.ABOUT },
            )
            Route.SCAN -> ScanScreen(
                docType,
                onBack = { route = Route.HOME },
                onManual = { route = Route.MANUAL },
                onContinue = { m: Mrz.Result ->
                    val k = AccessKey.Mrz(m.documentNumber, m.dateOfBirth, m.dateOfExpiry)
                    prefill = k
                    startRead(k)
                },
            )
            Route.MANUAL -> ManualScreen(
                docType, prefill,
                onBack = { route = if (prefill != null) Route.SCAN else Route.HOME },
                onCamera = { route = Route.SCAN },
                onSubmit = { startRead(it) },
            )
            Route.NFC -> NfcScreen(
                docType, progress, error, nfcEnabled,
                onCancel = { key = null; route = Route.HOME },
                onRetry = { error = null; progress = null },
                onHelp = { route = Route.HELP },
                onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
            )
            Route.HELP -> HelpContent(docType) { route = Route.NFC }
            Route.RESULT -> report?.let { r ->
                ResultScreen(
                    r,
                    userAnchor = trust?.isUserAdded(r.passive.csca) == true,
                    onClose = { resetAll() },
                    onNew = { resetAll(); route = Route.SCAN },
                    onDetails = { route = Route.DETAILS },
                )
            }
            Route.DETAILS -> report?.let { r ->
                DetailsScreen(r, onBack = { route = Route.RESULT })
            }
            Route.ABOUT -> AboutScreen(trust, onBack = { route = Route.HOME })
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        io.shutdownNow()
    }
}
