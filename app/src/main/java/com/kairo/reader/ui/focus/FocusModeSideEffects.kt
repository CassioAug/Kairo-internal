@file:Suppress("FunctionNaming")

package com.kairo.reader.ui.focus

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.ui.theme.readerThemePalette

@Composable
fun FocusModeSideEffects(
    enabled: Boolean,
    hideStatusBar: Boolean,
    pauseNotifications: Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() } ?: return
    val window = activity.window
    val insetsController = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    DisposableEffect(enabled, hideStatusBar) {
        val previousBehavior = insetsController.systemBarsBehavior

        if (enabled && hideStatusBar) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }

        onDispose {
            insetsController.systemBarsBehavior = previousBehavior
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    FocusDndSideEffect(enabled = enabled && pauseNotifications)
}

@Composable
@Suppress("DEPRECATION")
fun SystemBarsStyleSideEffect(readerTheme: ReaderTheme) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() } ?: return
    val window = activity.window
    val controller = remember(window, view) { WindowInsetsControllerCompat(window, view) }

    DisposableEffect(readerTheme) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= MIN_CONTRAST_API) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val useDarkIcons = !readerTheme.readerThemePalette().isDark
        controller.isAppearanceLightStatusBars = useDarkIcons
        controller.isAppearanceLightNavigationBars = useDarkIcons
        onDispose { }
    }
}

@Composable
private fun FocusDndSideEffect(enabled: Boolean) {
    val context = LocalContext.current
    val owner = remember { Any() }
    val notificationManager =
        remember(context) {
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }

    DisposableEffect(enabled, notificationManager, owner) {
        if (enabled) {
            FocusDndSessionController.acquire(owner, notificationManager)
        } else {
            FocusDndSessionController.release(owner, notificationManager)
        }

        onDispose {
            FocusDndSessionController.release(owner, notificationManager)
        }
    }
}

private object FocusDndSessionController {
    private val handler = Handler(Looper.getMainLooper())
    private val activeOwners = linkedSetOf<Any>()
    private var previousFilter: Int? = null
    private var didChange = false
    private var pendingRestore: Runnable? = null

    fun acquire(
        owner: Any,
        notificationManager: NotificationManager,
    ) {
        cancelPendingRestore()
        val added = activeOwners.add(owner)
        if (!added || !notificationManager.isNotificationPolicyAccessGranted) return
        if (didChange) return

        val currentFilter = notificationManager.currentInterruptionFilter
        previousFilter = currentFilter
        if (currentFilter != NotificationManager.INTERRUPTION_FILTER_NONE) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            didChange = true
        }
    }

    fun release(
        owner: Any,
        notificationManager: NotificationManager,
    ) {
        val removed = activeOwners.remove(owner)
        if (!removed || activeOwners.isNotEmpty()) return

        scheduleRestore(notificationManager)
    }

    private fun scheduleRestore(notificationManager: NotificationManager) {
        cancelPendingRestore()
        val restoreRunnable =
            Runnable {
                pendingRestore = null
                if (activeOwners.isNotEmpty()) return@Runnable

                if (didChange &&
                    previousFilter != null &&
                    notificationManager.isNotificationPolicyAccessGranted
                ) {
                    notificationManager.setInterruptionFilter(previousFilter!!)
                }
                previousFilter = null
                didChange = false
            }
        pendingRestore = restoreRunnable
        handler.postDelayed(restoreRunnable, DND_RESTORE_GRACE_MS)
    }

    private fun cancelPendingRestore() {
        pendingRestore?.let(handler::removeCallbacks)
        pendingRestore = null
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private const val MIN_CONTRAST_API = 29
private const val DND_RESTORE_GRACE_MS = 1_500L
