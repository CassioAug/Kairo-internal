package com.kairo.reader.ui.importing

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.navigation.NavHostController
import com.kairo.reader.KairoApplication
import com.kairo.reader.R
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.WebArticleUrl
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.navigation.KairoRoutes
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException

private const val IMPORT_COMPLETE_HOLD_MS = 200L
private const val URL_IMPORT_COMPLETE_HOLD_MS = 40L

internal data class ImportCoordinator(
    val state: ImportUiState,
    val importFile: (Uri) -> Unit,
    val importUrl: (String) -> Unit,
)

@Composable
internal fun rememberImportCoordinator(
    container: KairoApplication,
    navController: NavHostController,
    externalImportUri: Uri?,
    externalArticleUrl: String?,
    onExternalImportUriConsumed: (Uri) -> Unit,
    onExternalArticleUrlConsumed: (String) -> Unit,
    onShowUserMessage: (String, SnackbarDuration) -> Unit,
): ImportCoordinator {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val dispatcherProvider = container.dispatcherProvider
    var importState by remember { mutableStateOf(ImportUiState()) }
    var importProgressJob by remember { mutableStateOf<Job?>(null) }

    fun showUserMessage(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        onShowUserMessage(message, duration)
    }

    fun handleImport(
        displayName: String?,
        completionHoldMs: Long = IMPORT_COMPLETE_HOLD_MS,
        onImported: (BookImportResult) -> Unit = {},
        importBook: suspend () -> BookImportResult,
    ) {
        if (importState.isImporting) return
        importState =
            ImportUiState(
                isImporting = true,
                progress = 0f,
                fileName = displayName,
            )
        importProgressJob?.cancel()
        importProgressJob =
            coroutineScope.launch {
                driveImportProgress { progress ->
                    importState = importState.copy(progress = progress)
                }
            }
        coroutineScope.launch(dispatcherProvider.io) {
            val result = runCatching { importBook() }
            withContext(Dispatchers.Main) {
                importProgressJob?.cancel()
                if (result.isSuccess) {
                    importState = importState.copy(progress = 1f)
                    if (completionHoldMs > 0L) {
                        delay(completionHoldMs)
                    }
                }
                importState = ImportUiState()
                result.onSuccess { importResult ->
                    val book = importResult.book
                    if (importResult.alreadyImported) {
                        showUserMessage(
                            resources.getString(
                                R.string.toast_import_duplicate_detail,
                                book.title,
                            ),
                            duration = SnackbarDuration.Long,
                        )
                        onImported(importResult)
                        return@onSuccess
                    }
                    val chapterCount = book.chapters.size
                    val message =
                        resources.getQuantityString(
                            R.plurals.toast_imported_with_chapter_count,
                            chapterCount,
                            book.title,
                            chapterCount,
                        )
                    showUserMessage(message)
                    onImported(importResult)
                }
                result.onFailure { error ->
                    val message = resolveImportFailureMessage(resources, error)
                    showUserMessage(message, duration = SnackbarDuration.Long)
                }
            }
        }
    }

    fun handleImportFile(uri: Uri) {
        handleImport(resolveImportFileName(context, uri)) {
            container.libraryRepository.import(uri)
        }
    }

    fun handleImportUrl(rawUrl: String) {
        handleImport(
            displayName = resolveImportUrlName(rawUrl),
            completionHoldMs = URL_IMPORT_COMPLETE_HOLD_MS,
            onImported = { importResult ->
                navController.navigate(KairoRoutes.reader(importResult.book.id.value)) {
                    launchSingleTop = true
                }
            },
        ) {
            container.libraryRepository.importUrl(rawUrl)
        }
    }

    LaunchedEffect(externalImportUri, importState.isImporting) {
        val uri = externalImportUri ?: return@LaunchedEffect
        if (importState.isImporting) return@LaunchedEffect
        onExternalImportUriConsumed(uri)
        navController.navigate(KairoRoutes.LIBRARY) {
            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
            launchSingleTop = true
        }
        handleImportFile(uri)
    }

    LaunchedEffect(externalArticleUrl, importState.isImporting) {
        val url = externalArticleUrl ?: return@LaunchedEffect
        if (importState.isImporting) return@LaunchedEffect
        onExternalArticleUrlConsumed(url)
        navController.navigate(KairoRoutes.LIBRARY) {
            popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
            launchSingleTop = true
        }
        handleImportUrl(url)
    }

    return ImportCoordinator(
        state = importState,
        importFile = ::handleImportFile,
        importUrl = ::handleImportUrl,
    )
}

private fun resolveImportFileName(
    context: Context,
    uri: Uri,
): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    }.getOrNull()

private fun resolveImportUrlName(rawUrl: String): String? =
    runCatching { WebArticleUrl.displayHost(WebArticleUrl.normalize(rawUrl)) }.getOrNull()

internal fun resolveImportFailureMessage(
    resources: Resources,
    error: Throwable,
): String {
    val message =
        when (val root = error.rootCause()) {
            is HttpStatusException ->
                when (root.statusCode) {
                    401, 403 -> resources.getString(R.string.toast_import_failed_blocked)
                    404 -> resources.getString(R.string.toast_import_failed_not_found)
                    429 -> resources.getString(R.string.toast_import_failed_rate_limited)
                    in 500..599 -> resources.getString(R.string.toast_import_failed_server)
                    else -> resources.getString(R.string.toast_import_failed_detail, root.message)
                }
            is UnknownHostException -> resources.getString(R.string.toast_import_failed_network)
            is SocketTimeoutException -> resources.getString(R.string.toast_import_failed_timeout)
            is SSLException -> resources.getString(R.string.toast_import_failed_secure)
            else ->
                error.message?.let {
                    resources.getString(R.string.toast_import_failed_detail, it)
                } ?: resources.getString(R.string.toast_import_failed_unknown)
        }
    return message
}

private fun Throwable.rootCause(): Throwable {
    var current = this
    while (current.cause != null && current.cause !== current) {
        current = current.cause ?: break
    }
    return current
}

private suspend fun driveImportProgress(onUpdate: (Float) -> Unit) {
    var progress = 0f
    onUpdate(progress)
    while (currentCoroutineContext().isActive && progress < 0.92f) {
        delay(120)
        progress = (progress + (1f - progress) * 0.08f).coerceAtMost(0.92f)
        onUpdate(progress)
    }
}
