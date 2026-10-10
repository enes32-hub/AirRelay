package io.github.jqssun.airplay

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jqssun.airplay.receiver.UserExperiencePrefs
import io.github.jqssun.airplay.receiver.ReceiverPresentation
import io.github.jqssun.airplay.ui.ProductEnvironment
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jqssun.airplay.service.AirPlayService
import io.github.jqssun.airplay.ui.MainScreen
import io.github.jqssun.airplay.ui.theme.AirPlayTheme
import io.github.jqssun.airplay.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var service: AirPlayService? = null
    private var quitting = false
    // unbindService() throws IllegalArgumentException when no binding was established,
    // so the result of the one bind call must be remembered.
    private var serviceBound = false
    val isInPip = mutableStateOf(false)
    val permissionRevision = mutableStateOf(0)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? AirPlayService.LocalBinder)?.service ?: return
            service = svc
            if (quitting) { svc.stopServer(); return }
            viewModel.bindService(svc)
            if (viewModel.mayAutoStart() && svc.serverState.value == AirPlayService.ServerState.STOPPED) {
                viewModel.startServer()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            viewModel.unbindService()
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionRevision.value++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        serviceBound = bindService(Intent(this, AirPlayService::class.java), connection, BIND_AUTO_CREATE)
        if (!serviceBound) {
            // Without a binding the receiver cannot be controlled at all; say so instead of
            // leaving a UI that looks functional but can never start a server.
            android.util.Log.w("MainActivity", "AirPlayService binding rejected; receiver control unavailable")
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.keepScreenOn, viewModel.connectionCount) { keep, conns ->
                    keep && conns > 0
                }.collect { on ->
                    if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        // auto-enter pre-declared in pip params
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    combine(
                        viewModel.receiverState, viewModel.videoPlaybackActive,
                        viewModel.videoPlaybackAspect, viewModel.videoAspect
                    ) { _, _, _, _ -> }.collect { setPictureInPictureParams(_pipParams()) }
                }
            }
        }

        setContent {
            val language by viewModel.language.collectAsStateWithLifecycle()
            val largeText by viewModel.largeText.collectAsStateWithLifecycle()
            val reducedMotion by viewModel.reduceMotion.collectAsStateWithLifecycle()
            ProductEnvironment(language, largeText, reducedMotion) {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            AirPlayTheme(theme) {
                MainScreen(
                    viewModel = viewModel,
                    isInPip = isInPip.value,
                    onSurfaceAvailable = { viewModel.onSurfaceAvailable(it) },
                    onSurfaceDestroyed = { viewModel.onSurfaceDestroyed(it) },
                    onPip = { enterPip() }
                )
            }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ReceiverPresentation.activityStarted()
        if (!quitting && viewModel.mayAutoStart() && service?.serverState?.value == AirPlayService.ServerState.STOPPED) {
            viewModel.startServer()
        }
    }

    /** Quit is not Home: withdraw discovery, stop playback/listeners and remember user intent. */
    fun quitApplication() {
        if (quitting) return
        quitting = true
        viewModel.stopServer(userInitiated = true)
        // Also covers an already-running service whose async bind has not delivered yet.
        stopService(Intent(this, AirPlayService::class.java))
        finishAndRemoveTask()
    }

    fun automaticDisplayAllowed(): Boolean = ReceiverPresentation.automaticDisplayAllowed(this)

    fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

    override fun onResume() {
        super.onResume()
        // Runtime results and returns from special-access settings both refresh the UI.
        permissionRevision.value++
        viewModel.refreshDiscoveryOnResume()
    }

    fun requestNotificationPermission() {
        val experience = getSharedPreferences(UserExperiencePrefs.NAME, MODE_PRIVATE)
        val prompted = experience.getBoolean(UserExperiencePrefs.NOTIFICATION_PROMPTED, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!prompted || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            experience.edit().putBoolean(UserExperiencePrefs.NOTIFICATION_PROMPTED, true).apply()
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    fun openNotificationSettings() = openPermissionSettings(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))

    fun openAutomaticDisplaySettings() = openPermissionSettings(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))

    private fun openPermissionSettings(intent: Intent) {
        runCatching { startActivity(intent) }.recoverCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.onFailure {
            android.widget.Toast.makeText(this, io.github.jqssun.airplay.receiver.AppLanguages.context(this)
                .getString(R.string.permission_settings_unavailable), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** Only called by an explicit user action; no overlay or background-launch workaround. */
    fun requestAutomaticDisplayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || automaticDisplayAllowed()) return
        openAutomaticDisplaySettings()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        enterPictureInPictureMode(_pipParams())
    }

    @androidx.annotation.RequiresApi(26)
    private fun _pipParams(): PictureInPictureParams {
        val aspect = if (viewModel.videoPlaybackActive.value) viewModel.videoPlaybackAspect.value
            else viewModel.videoAspect.value
        // Android PiP accepts only ratios in roughly [1/2.39, 2.39].
        // This clamps the small PiP window, never the decoder or main video surface.
        val safeAspect = aspect.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        val rational = Rational((safeAspect * 1000).toInt().coerceIn(419, 2390), 1000)
        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(_shouldAutoPip())
        }
        return builder.build()
    }

    private fun _shouldAutoPip(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            viewModel.serverState.value == AirPlayService.ServerState.RUNNING &&
            viewModel.receiverState.value.pin == null &&
            (viewModel.receiverState.value.firstFrameSubmitted || viewModel.videoPlaybackActive.value)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && _shouldAutoPip()) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        isInPip.value = inPip
    }

    override fun onStop() {
        super.onStop()
        ReceiverPresentation.activityStopped()
        // The keep-screen-on collector is cancelled when the lifecycle leaves STARTED, so its
        // clear branch never runs. Clear the flag here or it stays on while backgrounded.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!isChangingConfigurations && !isInPictureInPictureMode &&
            io.github.jqssun.airplay.recording.RecordingService.status.value.active) {
            // The recording service may already be gone while its status has not flipped yet;
            // a background start request would otherwise throw.
            runCatching {
                startService(Intent(this, io.github.jqssun.airplay.recording.RecordingService::class.java)
                    .setAction(io.github.jqssun.airplay.recording.RecordingService.STOP))
            }
        }
        // never stop mid-session
        if (!viewModel.runInBackground.value && !isChangingConfigurations &&
            (viewModel.receiverState.value.running || viewModel.receiverState.value.starting) &&
            !viewModel.receiverState.value.sessionBusy) {
            viewModel.stopServer(userInitiated = false)
        }
    }

    override fun onDestroy() {
        viewModel.unbindService()
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        super.onDestroy()
    }
}
