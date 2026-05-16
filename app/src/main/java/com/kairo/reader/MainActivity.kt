package com.kairo.reader

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.WebArticleUrl
import com.kairo.reader.sample.SampleBooks
import com.kairo.reader.ui.LocalDispatcherProvider
import com.kairo.reader.ui.focus.FocusModeSideEffects
import com.kairo.reader.ui.focus.SystemBarsStyleSideEffect
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.library.LibraryScreen
import com.kairo.reader.ui.library.LibraryTab
import com.kairo.reader.ui.library.buildLibraryProgress
import com.kairo.reader.ui.navigation.KairoRoutes
import com.kairo.reader.ui.navigation.KairoSavedStateKeys
import com.kairo.reader.ui.navigation.ReaderRoute
import com.kairo.reader.ui.navigation.RsvpLaunchSnapshotStore
import com.kairo.reader.ui.navigation.resolveWordIndex
import com.kairo.reader.ui.rsvp.RsvpBookContext
import com.kairo.reader.ui.rsvp.RsvpBookmarkCallbacks
import com.kairo.reader.ui.rsvp.RsvpLayoutBias
import com.kairo.reader.ui.rsvp.RsvpPlaybackCallbacks
import com.kairo.reader.ui.rsvp.RsvpPreferenceCallbacks
import com.kairo.reader.ui.rsvp.RsvpProfileContext
import com.kairo.reader.ui.rsvp.RsvpResumePoint
import com.kairo.reader.ui.rsvp.RsvpScreen
import com.kairo.reader.ui.rsvp.RsvpScreenCallbacks
import com.kairo.reader.ui.rsvp.RsvpScreenDependencies
import com.kairo.reader.ui.rsvp.RsvpScreenState
import com.kairo.reader.ui.rsvp.RsvpTextStyle
import com.kairo.reader.ui.rsvp.RsvpThemeCallbacks
import com.kairo.reader.ui.rsvp.RsvpUiCallbacks
import com.kairo.reader.ui.rsvp.RsvpUiPreferences
import com.kairo.reader.ui.settings.FocusSettingsScreen
import com.kairo.reader.ui.settings.InfoSettingsScreen
import com.kairo.reader.ui.settings.LanguageSettingsScreen
import com.kairo.reader.ui.settings.ReaderSettingsScreen
import com.kairo.reader.ui.settings.RsvpSettingsScreen
import com.kairo.reader.ui.settings.SettingsHomeScreen
import com.kairo.reader.ui.theme.KairoSnackbarHost
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialRoute
import com.kairo.reader.ui.tutorial.startingTutorialSteps
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

private const val IMPORT_COMPLETE_HOLD_MS = 200L
private const val URL_IMPORT_COMPLETE_HOLD_MS = 40L
private data class RsvpReturnTarget(
    val chapterIndex: Int,
    val tokenIndex: Int,
    val resumeCursor: Int,
)

private data class StartingTutorialLaunchContext(
    val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int,
)

private fun resolveRsvpReturnTarget(
    resumePoint: RsvpResumePoint,
    currentChapterIndex: Int,
    chapterCount: Int,
    currentChapterTokens: List<Token>,
): RsvpReturnTarget {
    val lastTokenIndex = currentChapterTokens.lastIndex
    val nextChapterIndex =
        if (resumePoint.tokenIndex > lastTokenIndex && currentChapterIndex < chapterCount - 1) {
            currentChapterIndex + 1
        } else {
            currentChapterIndex
        }
    if (nextChapterIndex != currentChapterIndex) {
        return RsvpReturnTarget(
            chapterIndex = nextChapterIndex,
            tokenIndex = 0,
            resumeCursor = -1,
        )
    }

    val tokenIndex =
        when {
            currentChapterTokens.isEmpty() -> resumePoint.tokenIndex.coerceAtLeast(0)
            resumePoint.tokenIndex > lastTokenIndex ->
                currentChapterTokens.nearestWordIndex(lastTokenIndex)
            else -> currentChapterTokens.nearestWordIndex(resumePoint.tokenIndex.coerceAtLeast(0))
        }
    val resumeCursor =
        if (resumePoint.tokenIndex > lastTokenIndex) {
            -1
        } else {
            resumePoint.resumeCursor
        }
    return RsvpReturnTarget(
        chapterIndex = resumePoint.chapterIndex ?: currentChapterIndex,
        tokenIndex = tokenIndex,
        resumeCursor = resumeCursor,
    )
}

private fun resolveStartingTutorialLaunchContext(
    books: List<Book>,
    positions: List<ReadingPosition>,
): StartingTutorialLaunchContext? {
    val book =
        books.firstOrNull { it.id.value == SampleBooks.STARTER_BOOK_ID }
            ?: books.firstOrNull()
            ?: return null
    if (book.chapters.isEmpty()) {
        return StartingTutorialLaunchContext(
            bookId = book.id.value,
            chapterIndex = 0,
            tokenIndex = 0,
        )
    }
    val savedPosition = positions.firstOrNull { it.bookId == book.id }
    val fallbackChapterIndex = book.chapters.indexOfFirst { it.plainText.isNotBlank() }.let { index ->
        if (index >= 0) index else 0
    }
    val chapterIndex =
        savedPosition?.chapterIndex?.coerceIn(0, book.chapters.lastIndex) ?: fallbackChapterIndex
    val tokenIndex = savedPosition?.tokenIndex?.coerceAtLeast(0) ?: 0
    return StartingTutorialLaunchContext(
        bookId = book.id.value,
        chapterIndex = chapterIndex,
        tokenIndex = tokenIndex,
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

private fun resolveImportFailureMessage(
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

@Suppress("CyclomaticComplexMethod", "FunctionNaming", "LongMethod")
@Composable
private fun KairoNavHost(
    container: KairoApplication,
    prefs: UserPreferences,
    externalImportUri: Uri?,
    externalArticleUrl: String?,
    onExternalImportUriConsumed: (Uri) -> Unit,
    onExternalArticleUrlConsumed: (String) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val resources = LocalResources.current
    val libraryFlow = container.libraryRepository.observeLibrary()
    val books by libraryFlow.collectAsState(initial = emptyList())
    val bookmarksFlow = container.bookmarkRepository.observeBookmarks()
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())
    val positionsFlow = container.readingPositionRepository.observePositions()
    val positions by positionsFlow.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dispatcherProvider = container.dispatcherProvider
    var importState by remember { mutableStateOf(ImportUiState()) }
    var importProgressJob by remember { mutableStateOf<Job?>(null) }
    val availableTutorialLaunchContext =
        remember(books, positions) { resolveStartingTutorialLaunchContext(books, positions) }
    var tutorialSteps by remember {
        mutableStateOf(startingTutorialSteps(includeReaderAndRsvp = false))
    }
    var tutorialLaunchContext by remember { mutableStateOf<StartingTutorialLaunchContext?>(null) }
    var tutorialStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var tutorialActive by rememberSaveable { mutableStateOf(false) }
    var tutorialAutoStarted by rememberSaveable { mutableStateOf(false) }

    val selectedWpm by produceState(initialValue = 0, prefs.rsvpConfig) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEffectivePace.estimateWpm(prefs.rsvpConfig)
            }
    }
    val estimatedWpm by produceState(initialValue = selectedWpm, prefs.rsvpConfig, selectedWpm) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEstimatedReadingPace.estimateWpm(
                    config = prefs.rsvpConfig,
                    fallbackEstimatedWpm = selectedWpm,
                )
            }
    }
    val libraryEstimatedWpmByBook by produceState(
        initialValue = emptyMap(),
        books,
        prefs.rsvpConfig,
        estimatedWpm,
        selectedWpm,
    ) {
        value =
            withContext(dispatcherProvider.default) {
                books.associate { book ->
                    val resolvedRsvpConfig =
                        RsvpConfigResolver.resolve(prefs.rsvpConfig, book.languageTag)
                    book.id.value to
                        RsvpEstimatedReadingPace.estimateWpm(
                            config = resolvedRsvpConfig,
                            fallbackEstimatedWpm = selectedWpm,
                            languageTag = book.languageTag,
                        )
                }
            }
    }
    val libraryProgress by produceState(
        initialValue = emptyMap(),
        books,
        positions,
        libraryEstimatedWpmByBook,
    ) {
        value =
            withContext(dispatcherProvider.io) {
                buildLibraryProgress(
                    books = books,
                    positions = positions,
                    estimatedWpmByBookId = libraryEstimatedWpmByBook,
                )
            }
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    fun resolveCurrentTutorialRoute(route: String?): StartingTutorialRoute? =
        when (route) {
            KairoRoutes.LIBRARY,
            KairoRoutes.LIBRARY_WITH_TAB,
            -> StartingTutorialRoute.LIBRARY
            KairoRoutes.SETTINGS -> StartingTutorialRoute.SETTINGS
            KairoRoutes.READER,
            KairoRoutes.READER_WITH_POSITION,
            ->
                StartingTutorialRoute.READER
            KairoRoutes.RSVP -> StartingTutorialRoute.RSVP
            else -> null
        }

    fun navigateToTutorialRoute(route: StartingTutorialRoute) {
        if (resolveCurrentTutorialRoute(currentRoute) == route) return
        when (route) {
            StartingTutorialRoute.LIBRARY -> {
                navController.navigate(KairoRoutes.LIBRARY) {
                    popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.SETTINGS -> {
                navController.navigate(KairoRoutes.SETTINGS) {
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.READER -> {
                val context = tutorialLaunchContext ?: return
                navController.navigate(
                    KairoRoutes.reader(
                        context.bookId,
                        context.chapterIndex,
                        context.tokenIndex,
                    )
                ) {
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.RSVP -> {
                val context = tutorialLaunchContext ?: return
                navController.navigate(
                    KairoRoutes.rsvp(
                        context.bookId,
                        context.chapterIndex,
                        context.tokenIndex,
                    )
                ) {
                    launchSingleTop = true
                }
            }
        }
    }

    fun markStartingTutorialSeenIfNeeded() {
        if (prefs.hasSeenStartingTutorial) return
        coroutineScope.launch {
            container.preferencesRepository.updateHasSeenStartingTutorial(true)
        }
    }

    fun startStartingTutorial() {
        val sessionSteps =
            startingTutorialSteps(includeReaderAndRsvp = availableTutorialLaunchContext != null)
        tutorialLaunchContext = availableTutorialLaunchContext
        tutorialSteps = sessionSteps
        tutorialStepIndex = 0
        tutorialActive = true
        tutorialAutoStarted = true
        navigateToTutorialRoute(sessionSteps.first().route)
    }

    fun dismissStartingTutorial(markAsSeen: Boolean = true) {
        tutorialActive = false
        if (markAsSeen) {
            markStartingTutorialSeenIfNeeded()
        }
    }

    fun finishStartingTutorial() {
        dismissStartingTutorial(markAsSeen = true)
        navigateToTutorialRoute(StartingTutorialRoute.LIBRARY)
    }

    fun moveStartingTutorial(stepDelta: Int) {
        val nextIndex = tutorialStepIndex + stepDelta
        if (nextIndex !in tutorialSteps.indices) {
            finishStartingTutorial()
            return
        }
        val previousRoute = tutorialSteps.getOrNull(tutorialStepIndex)?.route
        tutorialStepIndex = nextIndex
        val nextRoute = tutorialSteps[nextIndex].route
        if (nextRoute != previousRoute) {
            navigateToTutorialRoute(nextRoute)
        }
    }

    val tutorialOverlayState =
        if (tutorialActive) {
            StartingTutorialOverlayState(
                step = tutorialSteps[tutorialStepIndex],
                index = tutorialStepIndex,
                totalSteps = tutorialSteps.size,
            )
        } else {
            null
        }

    val libraryTutorialState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.LIBRARY }
    val settingsTutorialState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.SETTINGS }
    val readerTutorialState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.READER }
    val rsvpTutorialState =
        tutorialOverlayState?.takeIf { it.step.route == StartingTutorialRoute.RSVP }

    BackHandler(enabled = tutorialActive) {
        if (tutorialStepIndex == 0) {
            dismissStartingTutorial()
        } else {
            moveStartingTutorial(-1)
        }
    }

    fun showUserMessage(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
    ) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = duration,
            )
        }
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

    LaunchedEffect(
        prefs.hasSeenStartingTutorial,
        externalImportUri,
        externalArticleUrl,
        importState.isImporting,
    ) {
        if (!prefs.hasSeenStartingTutorial &&
            !tutorialAutoStarted &&
            externalImportUri == null &&
            externalArticleUrl == null &&
            !importState.isImporting
        ) {
            startStartingTutorial()
        }
    }
    val focusEnabledForRoute =
        prefs.focusModeEnabled &&
            when (currentRoute) {
                KairoRoutes.SETTINGS,
                KairoRoutes.SETTINGS_LANGUAGE,
                KairoRoutes.SETTINGS_INFO,
                -> true
                KairoRoutes.READER,
                KairoRoutes.READER_WITH_POSITION,
                -> prefs.focusApplyInReader
                KairoRoutes.RSVP -> prefs.focusApplyInRsvp
                else -> false
            }
    FocusModeSideEffects(
        enabled = focusEnabledForRoute,
        hideStatusBar = prefs.focusHideStatusBar,
        pauseNotifications = prefs.focusPauseNotifications,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = KairoRoutes.LIBRARY) {
            composable(KairoRoutes.LIBRARY) {
                LibraryScreen(
                    books = books,
                    bookmarks = bookmarks,
                    bookProgress = libraryProgress,
                    initialTab = LibraryTab.Library,
                    importState = importState,
                    onOpen = { book ->
                        // Navigate to reader - saved position will be restored there
                        navController.navigate(KairoRoutes.reader(book.id.value))
                    },
                    onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                        coroutineScope.launch(dispatcherProvider.io) {
                            container.readingPositionRepository.savePosition(
                                ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                            )
                        }
                        navController.navigate(KairoRoutes.reader(bookId, chapterIndex, tokenIndex))
                    },
                    onDeleteBookmark = { bookmarkId ->
                        coroutineScope.launch { container.bookmarkRepository.delete(bookmarkId) }
                    },
                    onDeleteBookmarksForBook = { bookId ->
                        coroutineScope.launch {
                            container.bookmarkRepository.deleteForBook(BookId(bookId))
                        }
                    },
                    onImportFile = ::handleImportFile,
                    onImportUrl = ::handleImportUrl,
                    onSettings = { navController.navigate(KairoRoutes.SETTINGS) },
                    onSetCompleted = { book, isCompleted ->
                        coroutineScope.launch {
                            container.libraryRepository.setCompleted(book.id.value, isCompleted)
                        }
                    },
                    onDelete = { book ->
                        coroutineScope.launch { container.libraryRepository.delete(book.id.value) }
                    },
                    tutorialState = libraryTutorialState,
                    onTutorialNext = { moveStartingTutorial(1) },
                    onTutorialPrevious = { moveStartingTutorial(-1) },
                    onTutorialSkip = { dismissStartingTutorial() },
                )
            }

            composable(
                route = KairoRoutes.LIBRARY_WITH_TAB,
                arguments =
                    listOf(
                        navArgument(KairoRoutes.ARG_LIBRARY_TAB) {
                            type = NavType.StringType
                            defaultValue = KairoRoutes.TAB_LIBRARY
                        },
                    ),
            ) { backStackEntry ->
                val tab =
                    backStackEntry.arguments?.getString(KairoRoutes.ARG_LIBRARY_TAB)
                        ?: KairoRoutes.TAB_LIBRARY
                val initialTab =
                    when (tab.lowercase()) {
                        KairoRoutes.TAB_COMPLETED -> LibraryTab.Completed
                        KairoRoutes.TAB_BOOKMARKS -> LibraryTab.Bookmarks
                        else -> LibraryTab.Library
                    }
                LibraryScreen(
                    books = books,
                    bookmarks = bookmarks,
                    bookProgress = libraryProgress,
                    initialTab = initialTab,
                    importState = importState,
                    onOpen = { book ->
                        navController.navigate(KairoRoutes.reader(book.id.value))
                    },
                    onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                        coroutineScope.launch(dispatcherProvider.io) {
                            container.readingPositionRepository.savePosition(
                                ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                            )
                        }
                        navController.navigate(KairoRoutes.reader(bookId, chapterIndex, tokenIndex))
                    },
                    onDeleteBookmark = { bookmarkId ->
                        coroutineScope.launch { container.bookmarkRepository.delete(bookmarkId) }
                    },
                    onDeleteBookmarksForBook = { bookId ->
                        coroutineScope.launch {
                            container.bookmarkRepository.deleteForBook(BookId(bookId))
                        }
                    },
                    onImportFile = ::handleImportFile,
                    onImportUrl = ::handleImportUrl,
                    onSettings = { navController.navigate(KairoRoutes.SETTINGS) },
                    onSetCompleted = { book, isCompleted ->
                        coroutineScope.launch {
                            container.libraryRepository.setCompleted(book.id.value, isCompleted)
                        }
                    },
                    onDelete = { book ->
                        coroutineScope.launch { container.libraryRepository.delete(book.id.value) }
                    },
                    tutorialState = libraryTutorialState,
                    onTutorialNext = { moveStartingTutorial(1) },
                    onTutorialPrevious = { moveStartingTutorial(-1) },
                    onTutorialSkip = { dismissStartingTutorial() },
                )
            }

        composable(
            route = KairoRoutes.READER,
            arguments =
                listOf(
                    navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            ReaderRoute(
                backStackEntry = backStackEntry,
                container = container,
                navController = navController,
                prefs = prefs,
                estimatedWpm = estimatedWpm,
                tutorialActive = tutorialActive,
                tutorialState = readerTutorialState,
                onShowUserMessage = { message -> showUserMessage(message) },
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
        }

        composable(
            route = KairoRoutes.READER_WITH_POSITION,
            arguments =
                listOf(
                    navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
                    navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
                    navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
                ),
        ) { backStackEntry ->
            ReaderRoute(
                backStackEntry = backStackEntry,
                container = container,
                navController = navController,
                prefs = prefs,
                estimatedWpm = estimatedWpm,
                tutorialActive = tutorialActive,
                tutorialState = readerTutorialState,
                initialChapterIndex =
                    backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0,
                initialTokenIndex =
                    backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0,
                onShowUserMessage = { message -> showUserMessage(message) },
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
        }

        composable(
            route = KairoRoutes.RSVP,
            arguments =
            listOf(
                navArgument(KairoRoutes.ARG_BOOK_ID) { type = NavType.StringType },
                navArgument(KairoRoutes.ARG_CHAPTER_INDEX) { type = NavType.IntType },
                navArgument(KairoRoutes.ARG_TOKEN_INDEX) { type = NavType.IntType },
                navArgument(KairoRoutes.ARG_TEMPO_MS) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val bookId =
                backStackEntry.arguments?.getString(KairoRoutes.ARG_BOOK_ID)
                    ?: return@composable
            val chapterIndex =
                backStackEntry.arguments?.getInt(KairoRoutes.ARG_CHAPTER_INDEX) ?: 0
            val startIndex = backStackEntry.arguments?.getInt(KairoRoutes.ARG_TOKEN_INDEX) ?: 0
            val launchSnapshotTokens =
                remember(bookId, chapterIndex) {
                    RsvpLaunchSnapshotStore.tokensFor(bookId, chapterIndex)
                }
            val tokensState =
                produceState(
                    initialValue = launchSnapshotTokens,
                    bookId,
                    chapterIndex,
                ) {
                    val loadedTokens =
                        runCatching {
                            container.tokenRepository.getTokens(BookId(bookId), chapterIndex)
                        }.getOrElse { emptyList() }
                    if (loadedTokens.isNotEmpty() || value.isEmpty()) {
                        value = loadedTokens
                    }
                }
            val tokens = tokensState.value
            val wordCountByToken = remember(tokens) { buildWordCountByToken(tokens) }

            val focusEnabledInRsvp = prefs.focusModeEnabled && prefs.focusApplyInRsvp
            val rsvpLifecycleOwner = LocalLifecycleOwner.current
            val bookIdValue = BookId(bookId)
            val savedCurrentTokenIndex =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_CURRENT_TOKEN_INDEX]
                        ?: -1
                }
            val savedCurrentResumeCursor =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle[KairoSavedStateKeys.RSVP_CURRENT_RESUME_CURSOR]
                        ?: -1
                }
            val safeStartIndex =
                savedCurrentTokenIndex
                    .takeIf { it >= 0 }
                    ?: startIndex.coerceAtLeast(0)
            val chapterCountState =
                produceState(
                    initialValue = chapterIndex + 1,
                    bookId,
                ) {
                    value =
                        runCatching {
                            container.bookRepository.getBook(bookIdValue).chapters.size
                        }.getOrDefault(chapterIndex + 1)
                }
            val savedResumePositionState =
                produceState<ReadingPosition?>(
                    initialValue = null,
                    bookId,
                    chapterIndex,
                    safeStartIndex,
                ) {
                    value = container.readingPositionRepository.getPosition(bookIdValue)
                }
            val startResumeCursor =
                savedCurrentResumeCursor
                    .takeIf { savedCurrentTokenIndex >= 0 && it >= 0 }
                    ?: savedResumePositionState.value
                        ?.takeIf {
                            it.chapterIndex == chapterIndex &&
                                it.tokenIndex == safeStartIndex &&
                                it.rsvpResumeCursor >= 0
                        }?.rsvpResumeCursor
                    ?: -1
            val playbackIsPlayingFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(
                        KairoSavedStateKeys.RSVP_PLAYBACK_IS_PLAYING,
                        true,
                    )
                }
            val playbackIsPlaying by playbackIsPlayingFlow.collectAsState(initial = true)
            val languageTagState =
                produceState<String?>(
                    initialValue = null,
                    bookId,
                ) {
                    value =
                        runCatching { container.bookRepository.getBookLanguageTag(bookIdValue) }
                            .getOrNull()
                }
            val resolvedRsvpConfig =
                RsvpConfigResolver.resolve(prefs.rsvpConfig, languageTagState.value)
            fun saveRsvpPosition(
                targetChapterIndex: Int,
                targetTokenIndex: Int,
                targetWordIndex: Int,
                targetResumeCursor: Int,
            ) {
                rsvpLifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(
                            bookIdValue,
                            targetChapterIndex,
                            targetTokenIndex,
                            targetWordIndex,
                            rsvpResumeCursor = targetResumeCursor,
                        ),
                    )
                }
            }

            val rsvpState =
                RsvpScreenState(
                    book =
                    RsvpBookContext(
                        bookId = bookIdValue,
                        chapterIndex = chapterIndex,
                        tokens = tokens,
                        startIndex = safeStartIndex,
                        startResumeCursor = startResumeCursor,
                        sessionStartIndex = startIndex.coerceAtLeast(0),
                    ),
                    profile =
                    RsvpProfileContext(
                        config = resolvedRsvpConfig,
                        selectedProfileId = prefs.rsvpSelectedProfileId,
                        customProfiles = prefs.rsvpCustomProfiles,
                    ),
                    initialIsPlaying = playbackIsPlaying,
                    uiPrefs =
                    RsvpUiPreferences(
                        extremeSpeedUnlocked = prefs.unlockExtremeSpeed,
                        readerTheme = prefs.readerTheme,
                        focusModeEnabled = focusEnabledInRsvp,
                    ),
                    textStyle =
                    RsvpTextStyle(
                        fontSizeSp = prefs.rsvpFontSizeSp,
                        fontFamily = prefs.rsvpFontFamily,
                        fontWeight = prefs.rsvpFontWeight,
                        textBrightness = prefs.rsvpTextBrightness,
                    ),
                    layoutBias =
                    RsvpLayoutBias(
                        verticalBias = prefs.rsvpVerticalBias,
                        horizontalBias = prefs.rsvpHorizontalBias,
                    ),
                )
            val rsvpCallbacks =
                RsvpScreenCallbacks(
                    bookmarks =
                    RsvpBookmarkCallbacks(
                        onAddBookmark = { tokenIndex, previewText ->
                            coroutineScope.launch {
                                val id = "$bookId:$chapterIndex:$tokenIndex"
                                container.bookmarkRepository.add(
                                    Bookmark(
                                        id = id,
                                        bookId = bookIdValue,
                                        chapterIndex = chapterIndex,
                                        tokenIndex = tokenIndex,
                                        previewText = previewText,
                                        createdAt = System.currentTimeMillis(),
                                    ),
                                )
                                showUserMessage(resources.getString(R.string.toast_bookmark_added))
                            }
                        },
                        onOpenBookmarks = {
                            navController.navigate(KairoRoutes.libraryBookmarks()) {
                                popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                            }
                        },
                    ),
                    playback =
                    RsvpPlaybackCallbacks(
                        onFinished = { resumePoint ->
                            val returnTarget =
                                resolveRsvpReturnTarget(
                                    resumePoint = resumePoint,
                                    currentChapterIndex = chapterIndex,
                                    chapterCount = chapterCountState.value,
                                    currentChapterTokens = tokens,
                                )
                            val wordIndex =
                                if (returnTarget.chapterIndex == chapterIndex) {
                                    resolveWordIndex(wordCountByToken, returnTarget.tokenIndex)
                                } else {
                                    0
                                }
                            saveRsvpPosition(
                                targetChapterIndex = returnTarget.chapterIndex,
                                targetTokenIndex = returnTarget.tokenIndex,
                                targetWordIndex = wordIndex,
                                targetResumeCursor = returnTarget.resumeCursor,
                            )
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(
                                    KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX,
                                    returnTarget.chapterIndex,
                                )
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(
                                    KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX,
                                    returnTarget.tokenIndex,
                                )
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(
                                    KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                                    returnTarget.resumeCursor,
                                )
                            navController.popBackStack()
                        },
                        onPositionChanged = { resumePoint ->
                            val safeIndex =
                                if (tokens.isNotEmpty()) {
                                    resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                                } else {
                                    0
                                }
                            backStackEntry.savedStateHandle[
                                KairoSavedStateKeys.RSVP_CURRENT_TOKEN_INDEX
                            ] =
                                safeIndex
                            backStackEntry.savedStateHandle[
                                KairoSavedStateKeys.RSVP_CURRENT_RESUME_CURSOR
                            ] =
                                resumePoint.resumeCursor
                            val wordIndex = resolveWordIndex(wordCountByToken, safeIndex)
                            saveRsvpPosition(
                                targetChapterIndex = chapterIndex,
                                targetTokenIndex = safeIndex,
                                targetWordIndex = wordIndex,
                                targetResumeCursor = resumePoint.resumeCursor,
                            )
                        },
                        onTempoChange = { tempoMsPerWord ->
                            val baseTempoMs =
                                RsvpConfigResolver.toBaseTempoMs(
                                    tempoMsPerWord,
                                    languageTagState.value,
                                )
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpTempoMsPerWord(baseTempoMs)
                            }
                        },
                        onExit = { resumePoint ->
                            val resumeIndex =
                                if (tokens.isNotEmpty()) {
                                    resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                                } else {
                                    resumePoint.tokenIndex.coerceAtLeast(0)
                                }
                            val wordIndex = resolveWordIndex(wordCountByToken, resumeIndex)
                            saveRsvpPosition(
                                targetChapterIndex = chapterIndex,
                                targetTokenIndex = resumeIndex,
                                targetWordIndex = wordIndex,
                                targetResumeCursor = resumePoint.resumeCursor,
                            )
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(KairoSavedStateKeys.RSVP_RESULT_CHAPTER_INDEX, chapterIndex)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(KairoSavedStateKeys.RSVP_RESULT_TOKEN_INDEX, resumeIndex)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(
                                    KairoSavedStateKeys.RSVP_RESULT_RESUME_CURSOR,
                                    resumePoint.resumeCursor,
                                )
                            navController.popBackStack()
                        },
                        onOpenLibrary = { resumePoint ->
                            val resumeIndex =
                                if (tokens.isNotEmpty()) {
                                    resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                                } else {
                                    resumePoint.tokenIndex.coerceAtLeast(0)
                                }
                            val wordIndex = resolveWordIndex(wordCountByToken, resumeIndex)
                            saveRsvpPosition(
                                targetChapterIndex = chapterIndex,
                                targetTokenIndex = resumeIndex,
                                targetWordIndex = wordIndex,
                                targetResumeCursor = resumePoint.resumeCursor,
                            )
                            navController.navigate(KairoRoutes.LIBRARY) {
                                popUpTo(KairoRoutes.LIBRARY) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        onPlaybackStateChanged = { isPlaying ->
                            backStackEntry.savedStateHandle[
                                KairoSavedStateKeys.RSVP_PLAYBACK_IS_PLAYING
                            ] =
                                isPlaying
                        },
                    ),
                    preferences =
                    RsvpPreferenceCallbacks(
                        onExtremeSpeedUnlockedChange = { enabled ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateUnlockExtremeSpeed(
                                    enabled
                                )
                            }
                        },
                        onSelectProfile = { profileId ->
                            coroutineScope.launch {
                                container.preferencesRepository.selectRsvpProfile(profileId)
                            }
                        },
                        onSaveCustomProfile = { name, config ->
                            coroutineScope.launch {
                                container.preferencesRepository.saveRsvpCustomProfile(name, config)
                            }
                        },
                        onDeleteCustomProfile = { profileId ->
                            coroutineScope.launch {
                                container.preferencesRepository.deleteRsvpCustomProfile(
                                    profileId
                                )
                            }
                        },
                        onRsvpConfigChange = { updated ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpConfig { updated }
                            }
                        },
                    ),
                    ui =
                    RsvpUiCallbacks(
                        onFocusModeEnabledChange = { enabled ->
                            coroutineScope.launch {
                                if (enabled) {
                                    if (!prefs.focusModeEnabled) {
                                        container.preferencesRepository.updateFocusModeEnabled(
                                            true
                                        )
                                    }
                                    container.preferencesRepository.updateFocusApplyInRsvp(true)
                                } else {
                                    container.preferencesRepository.updateFocusApplyInRsvp(
                                        false
                                    )
                                }
                            }
                        },
                        onRsvpFontSizeChange = { size ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpFontSize(size)
                            }
                        },
                        onRsvpTextBrightnessChange = { brightness ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpTextBrightness(
                                    brightness
                                )
                            }
                        },
                        onRsvpFontWeightChange = { weight ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpFontWeight(weight)
                            }
                        },
                        onRsvpFontFamilyChange = { family ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpFontFamily(family)
                            }
                        },
                    ),
                    theme =
                    RsvpThemeCallbacks(
                        onThemeChange = { theme ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateTheme(theme.name)
                            }
                        },
                        onVerticalBiasChange = { bias ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpVerticalBias(bias)
                            }
                        },
                        onHorizontalBiasChange = { bias ->
                            coroutineScope.launch {
                                container.preferencesRepository.updateRsvpHorizontalBias(bias)
                            }
                        },
                    ),
                )
            val rsvpDependencies =
                RsvpScreenDependencies(
                    frameRepository = container.rsvpFrameRepository,
                )

            RsvpScreen(
                state = rsvpState,
                callbacks = rsvpCallbacks,
                dependencies = rsvpDependencies,
                tutorialState = rsvpTutorialState,
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
        }

        composable(KairoRoutes.SETTINGS) {
            SettingsHomeScreen(
                onOpenLanguage = {
                    navController.navigate(KairoRoutes.SETTINGS_LANGUAGE)
                },
                onOpenRsvp = { navController.navigate(KairoRoutes.SETTINGS_RSVP) },
                onOpenReader = { navController.navigate(KairoRoutes.SETTINGS_READER) },
                onOpenFocus = { navController.navigate(KairoRoutes.SETTINGS_FOCUS) },
                onOpenInfo = { navController.navigate(KairoRoutes.SETTINGS_INFO) },
                onOpenStartingTutorial = ::startStartingTutorial,
                onReset = {
                    coroutineScope.launch {
                        container.preferencesRepository.reset()
                    }
                },
                onClose = { navController.popBackStack() },
                tutorialState = settingsTutorialState,
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
        }

        composable(KairoRoutes.SETTINGS_LANGUAGE) {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(KairoRoutes.SETTINGS_INFO) {
            InfoSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(KairoRoutes.SETTINGS_RSVP) {
            RsvpSettingsScreen(
                preferences = prefs,
                onSelectRsvpProfile = { profileId ->
                    coroutineScope.launch {
                        container.preferencesRepository.selectRsvpProfile(profileId)
                    }
                },
                onSaveRsvpProfile = { name, config ->
                    coroutineScope.launch {
                        container.preferencesRepository.saveRsvpCustomProfile(name, config)
                    }
                },
                onDeleteRsvpProfile = { profileId ->
                    coroutineScope.launch {
                        container.preferencesRepository.deleteRsvpCustomProfile(profileId)
                    }
                },
                onRsvpTempoMsPerWordChange = { tempoMsPerWord ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpTempoMsPerWord(tempoMsPerWord)
                    }
                },
                onRsvpConfigChange = { config ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpConfig { config }
                    }
                },
                onUnlockExtremeSpeedChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateUnlockExtremeSpeed(enabled)
                    }
                },
                onRsvpFontSizeChange = { size ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpFontSize(size)
                    }
                },
                onRsvpTextBrightnessChange = { brightness ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpTextBrightness(brightness)
                    }
                },
                onRsvpFontWeightChange = { weight ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpFontWeight(weight)
                    }
                },
                onRsvpFontFamilyChange = { family ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpFontFamily(family)
                    }
                },
                onRsvpVerticalBiasChange = { bias ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpVerticalBias(bias)
                    }
                },
                onRsvpHorizontalBiasChange = { bias ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateRsvpHorizontalBias(bias)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(KairoRoutes.SETTINGS_READER) {
            ReaderSettingsScreen(
                preferences = prefs,
                onFontSizeChange = { size ->
                    coroutineScope.launch { container.preferencesRepository.updateFontSize(size) }
                },
                onThemeChange = { theme ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateTheme(theme.name)
                    }
                },
                onTextBrightnessChange = { brightness ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateReaderTextBrightness(brightness)
                    }
                },
                onInvertedScrollChange = { enabled ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateInvertedScroll(enabled)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

            composable(KairoRoutes.SETTINGS_FOCUS) {
                FocusSettingsScreen(
                    preferences = prefs,
                    onFocusModeEnabledChange = { enabled ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateFocusModeEnabled(enabled)
                        }
                    },
                    onFocusHideStatusBarChange = { enabled ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateFocusHideStatusBar(enabled)
                        }
                    },
                    onFocusPauseNotificationsChange = { enabled ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateFocusPauseNotifications(enabled)
                        }
                    },
                    onFocusApplyInReaderChange = { enabled ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateFocusApplyInReader(enabled)
                        }
                    },
                    onFocusApplyInRsvpChange = { enabled ->
                        coroutineScope.launch {
                            container.preferencesRepository.updateFocusApplyInRsvp(enabled)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        KairoSnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    ),
        )
    }
}
