package com.kairo.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.appcompat.app.AppCompatActivity
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.data.books.WebArticleUrl
import com.kairo.reader.ui.LocalDispatcherProvider
import com.kairo.reader.ui.focus.SystemBarsStyleSideEffect
import com.kairo.reader.ui.navigation.KairoNavHost
import com.kairo.reader.ui.theme.KairoTheme

@Composable
private fun rememberSystemDefaultPreferences(): UserPreferences {
    val isDark = isSystemInDarkTheme()
    return remember(isDark) {
        UserPreferences(
            readerTheme =
                if (isDark) {
                    ReaderTheme.DARK
                } else {
                    ReaderTheme.LIGHT
                },
        )
    }
}

class MainActivity : AppCompatActivity() {
    private val pendingExternalImportUriState = mutableStateOf<Uri?>(null)
    private val pendingSharedArticleUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingExternalImportUriState.value = intent.bookImportUri()
        pendingSharedArticleUrlState.value =
            if (pendingExternalImportUriState.value == null) intent.sharedArticleUrl() else null

        val container = application as KairoApplication

        setContent {
            val fallbackPrefs = rememberSystemDefaultPreferences()
            val prefs by container.preferencesRepository.preferences.collectAsState(
                initial = null,
            )
            val effectivePrefs = prefs ?: fallbackPrefs

            CompositionLocalProvider(
                LocalDispatcherProvider provides container.dispatcherProvider
            ) {
                KairoTheme(readerTheme = effectivePrefs.readerTheme) {
                    SystemBarsStyleSideEffect(readerTheme = effectivePrefs.readerTheme)
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        if (prefs == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            KairoNavHost(
                                container = container,
                                prefs = effectivePrefs,
                                externalImportUri = pendingExternalImportUriState.value,
                                externalArticleUrl = pendingSharedArticleUrlState.value,
                                onExternalImportUriConsumed = { consumedUri ->
                                    clearConsumedExternalImportIntent(consumedUri)
                                },
                                onExternalArticleUrlConsumed = { consumedUrl ->
                                    clearConsumedSharedArticleIntent(consumedUrl)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        val importUri = intent.bookImportUri()
        pendingExternalImportUriState.value = importUri
        pendingSharedArticleUrlState.value =
            if (importUri == null) intent.sharedArticleUrl() else null
    }

    private fun clearConsumedExternalImportIntent(consumedUri: Uri) {
        if (pendingExternalImportUriState.value == consumedUri) {
            pendingExternalImportUriState.value = null
        }
        if (intent.bookImportUri() == consumedUri) {
            intent = Intent(this, MainActivity::class.java)
        }
    }

    private fun clearConsumedSharedArticleIntent(consumedUrl: String) {
        if (pendingSharedArticleUrlState.value == consumedUrl) {
            pendingSharedArticleUrlState.value = null
        }
        if (intent.sharedArticleUrl() == consumedUrl) {
            intent = Intent(this, MainActivity::class.java)
        }
    }
}

private fun Intent.bookImportUri(): Uri? =
    if (action == Intent.ACTION_VIEW) {
        data
    } else {
        null
    }

private fun Intent.sharedArticleUrl(): String? =
    if (action == Intent.ACTION_SEND && type?.startsWith("text/", ignoreCase = true) == true) {
        listOfNotNull(
            getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            getCharSequenceExtra(Intent.EXTRA_HTML_TEXT)?.toString(),
            getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
        )
            .joinToString(separator = "\n")
            .let(WebArticleUrl::extractBestWebUrl)
    } else {
        null
    }
