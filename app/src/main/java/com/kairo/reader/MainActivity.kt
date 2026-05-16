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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.ReaderTheme
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.data.books.BookImportResult
import com.kairo.reader.data.books.WebArticleUrl
import com.kairo.reader.sample.SampleBooks
import com.kairo.reader.ui.LocalDispatcherProvider
import com.kairo.reader.ui.focus.FocusModeSideEffects
import com.kairo.reader.ui.focus.SystemBarsStyleSideEffect
import com.kairo.reader.ui.focus.shouldApplyFocusMode
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.navigation.KairoRoutes
import com.kairo.reader.ui.navigation.LibraryRoute
import com.kairo.reader.ui.navigation.ReaderRoute
import com.kairo.reader.ui.navigation.RsvpRoute
import com.kairo.reader.ui.navigation.settingsRoutes
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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

private data class StartingTutorialLaunchContext(
    val bookId: String,
    val chapterIndex: Int,
    val tokenIndex: Int,
)

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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dispatcherProvider = container.dispatcherProvider
    var importState by remember { mutableStateOf(ImportUiState()) }
    var importProgressJob by remember { mutableStateOf<Job?>(null) }
    var tutorialSteps by remember {
        mutableStateOf(startingTutorialSteps(includeReaderAndRsvp = false))
    }
    var tutorialLaunchContext by remember { mutableStateOf<StartingTutorialLaunchContext?>(null) }
    var tutorialStepIndex by rememberSaveable { mutableIntStateOf(0) }
    var tutorialActive by rememberSaveable { mutableStateOf(false) }
    var tutorialAutoStarted by rememberSaveable { mutableStateOf(false) }
    val availableTutorialLaunchContext by produceState<StartingTutorialLaunchContext?>(
        initialValue = null,
        prefs.hasSeenStartingTutorial,
        tutorialAutoStarted,
    ) {
        if (prefs.hasSeenStartingTutorial || tutorialAutoStarted) {
            value = null
            return@produceState
        }
        combine(
            container.libraryRepository.observeLibrary(),
            container.readingPositionRepository.observePositions(),
        ) { books, positions ->
            resolveStartingTutorialLaunchContext(books, positions)
        }
            .distinctUntilChanged()
            .collect { context ->
                value = context
            }
    }

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
    FocusModeSideEffects(
        enabled = shouldApplyFocusMode(currentRoute, prefs),
        hideStatusBar = prefs.focusHideStatusBar,
        pauseNotifications = prefs.focusPauseNotifications,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = KairoRoutes.LIBRARY) {
            composable(KairoRoutes.LIBRARY) {
                LibraryRoute(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    selectedWpm = selectedWpm,
                    importState = importState,
                    onImportFile = ::handleImportFile,
                    onImportUrl = ::handleImportUrl,
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
                LibraryRoute(
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    selectedWpm = selectedWpm,
                    importState = importState,
                    initialTabRouteValue = tab,
                    onImportFile = ::handleImportFile,
                    onImportUrl = ::handleImportUrl,
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
                RsvpRoute(
                    backStackEntry = backStackEntry,
                    container = container,
                    navController = navController,
                    prefs = prefs,
                    tutorialState = rsvpTutorialState,
                    onShowUserMessage = { message -> showUserMessage(message) },
                    onTutorialNext = { moveStartingTutorial(1) },
                    onTutorialPrevious = { moveStartingTutorial(-1) },
                    onTutorialSkip = { dismissStartingTutorial() },
                )
            }

            settingsRoutes(
                container = container,
                navController = navController,
                prefs = prefs,
                coroutineScope = coroutineScope,
                tutorialState = settingsTutorialState,
                onOpenStartingTutorial = ::startStartingTutorial,
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
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
