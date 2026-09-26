package com.pingucodu.us

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.pingucodu.us.notifications.NOTIFICATION_ROUTE_EXTRA
import com.pingucodu.us.ui.nav.PinguCoduApp
import com.pingucodu.us.ui.theme.UsTheme
import dagger.hilt.android.AndroidEntryPoint

private val SHARED_URL_REGEX = Regex("""https?://\S+""")

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private var pendingRoute by mutableStateOf<String?>(null)
    private var pendingShareUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        pendingRoute = intent?.getStringExtra(NOTIFICATION_ROUTE_EXTRA)
        // Only on a fresh start - a config change re-runs onCreate with the same intent, and the
        // shared link was already handed to Stash the first time.
        if (savedInstanceState == null) pendingShareUrl = intent?.let(::sharedUrlFrom)

        // App is light-themed only (no dark mode yet), so force dark system bar icons
        // regardless of the device's theme - otherwise they can wash out on our light backgrounds.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            UsTheme {
                PinguCoduApp(
                    pendingRoute = pendingRoute,
                    onPendingRouteConsumed = { pendingRoute = null },
                    pendingShareUrl = pendingShareUrl,
                    onPendingShareUrlConsumed = { pendingShareUrl = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(NOTIFICATION_ROUTE_EXTRA)
        sharedUrlFrom(intent)?.let { pendingShareUrl = it }
    }

    /** The first http(s) link in text shared to us (e.g. "Inception https://imdb.com/..." from the
     * IMDb app, or "Place name\nhttps://maps.app.goo.gl/..." from Maps), or null if there's none. */
    private fun sharedUrlFrom(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return SHARED_URL_REGEX.find(text)?.value
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
