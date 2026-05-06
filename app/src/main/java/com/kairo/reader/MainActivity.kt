package com.kairo.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.Bookmark
import com.kairo.reader.core.model.ReadingPosition
import com.kairo.reader.core.model.Token
import com.kairo.reader.core.model.UserPreferences
import com.kairo.reader.core.model.buildWordCountByToken
import com.kairo.reader.core.model.nearestWordIndex
import com.kairo.reader.core.model.wordIndexForToken
import com.kairo.reader.core.rsvp.RsvpConfigResolver
import com.kairo.reader.core.rsvp.RsvpEstimatedReadingPace
import com.kairo.reader.core.rsvp.RsvpEffectivePace
import com.kairo.reader.sample.SampleBooks
import com.kairo.reader.ui.LocalDispatcherProvider
import com.kairo.reader.ui.focus.FocusModeSideEffects
import com.kairo.reader.ui.focus.SystemBarsStyleSideEffect
import com.kairo.reader.ui.library.ImportUiState
import com.kairo.reader.ui.library.LibraryScreen
import com.kairo.reader.ui.library.LibraryTab
import com.kairo.reader.ui.library.buildLibraryProgress
import com.kairo.reader.ui.reader.ReaderScreen
import com.kairo.reader.ui.reader.ReaderViewModel
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
import com.kairo.reader.ui.settings.LanguageSettingsScreen
import com.kairo.reader.ui.settings.ReaderSettingsScreen
import com.kairo.reader.ui.settings.RsvpSettingsScreen
import com.kairo.reader.ui.settings.SettingsHomeScreen
import com.kairo.reader.ui.theme.KairoSnackbarHost
import com.kairo.reader.ui.theme.KairoTheme
import com.kairo.reader.ui.tutorial.StartingTutorialOverlayState
import com.kairo.reader.ui.tutorial.StartingTutorialRoute
import com.kairo.reader.ui.tutorial.startingTutorialSteps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val pendingExternalImportUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        pendingExternalImportUriState.value = intent.bookImportUri()

        val container = application as KairoApplication

        setContent {
            val prefs by container.preferencesRepository.preferences.collectAsState(
                initial = null,
            )
            val effectivePrefs = prefs ?: UserPreferences()

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
                                onExternalImportUriConsumed = { consumedUri ->
                                    clearConsumedExternalImportIntent(consumedUri)
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
        setIntent(intent)
        pendingExternalImportUriState.value = intent.bookImportUri()
    }

    private fun clearConsumedExternalImportIntent(consumedUri: Uri) {
        if (pendingExternalImportUriState.value == consumedUri) {
            pendingExternalImportUriState.value = null
        }
        if (intent.bookImportUri() == consumedUri) {
            setIntent(Intent(this, MainActivity::class.java))
        }
    }
}

private fun Intent.bookImportUri(): Uri? =
    if (action == Intent.ACTION_VIEW) {
        data
    } else {
        null
    }

private const val RSVP_RESULT_CHAPTER_INDEX_KEY = "rsvp_result_chapter_index"
private const val RSVP_RESULT_TOKEN_INDEX_KEY = "rsvp_result_token_index"
private const val RSVP_RESULT_RESUME_CURSOR_KEY = "rsvp_result_resume_cursor"
private const val RSVP_PLAYBACK_IS_PLAYING_KEY = "rsvp_playback_is_playing"
private const val RSVP_CURRENT_TOKEN_INDEX_KEY = "rsvp_current_token_index"
private const val RSVP_CURRENT_RESUME_CURSOR_KEY = "rsvp_current_resume_cursor"
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

private object RsvpLaunchSnapshotStore {
    private data class Snapshot(
        val bookId: String,
        val chapterIndex: Int,
        val tokens: List<Token>,
    )

    private var snapshot: Snapshot? = null

    fun put(
        bookId: String,
        chapterIndex: Int,
        tokens: List<Token>,
    ) {
        if (tokens.isEmpty()) return
        snapshot = Snapshot(bookId = bookId, chapterIndex = chapterIndex, tokens = tokens)
    }

    fun tokensFor(
        bookId: String,
        chapterIndex: Int,
    ): List<Token> =
        snapshot
            ?.takeIf { it.bookId == bookId && it.chapterIndex == chapterIndex }
            ?.tokens
            .orEmpty()
}

private fun buildRsvpRoute(
    bookId: String,
    chapterIndex: Int,
    tokenIndex: Int,
    tempoMsPerWord: Long? = null,
): String {
    val encodedTempoMs = tempoMsPerWord?.takeIf { it > 0L } ?: -1L
    return "rsvp/$bookId/$chapterIndex/$tokenIndex?tempoMs=$encodedTempoMs"
}

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

private fun resolveWordIndex(
    wordCountByToken: IntArray?,
    tokenIndex: Int,
): Int {
    if (wordCountByToken == null || wordCountByToken.isEmpty()) return -1
    return wordIndexForToken(wordCountByToken, tokenIndex)
}

@Composable
private fun rememberReaderEstimatedWpm(
    baseConfig: com.kairo.reader.core.model.RsvpConfig,
    fallbackEstimatedWpm: Int,
    dispatcherProvider: com.kairo.reader.core.dispatchers.DispatcherProvider,
): Int {
    val estimatedWpm by produceState(
        initialValue = fallbackEstimatedWpm,
        baseConfig,
        fallbackEstimatedWpm,
    ) {
        value =
            withContext(dispatcherProvider.default) {
                RsvpEstimatedReadingPace.estimateWpm(
                    config = baseConfig,
                    fallbackEstimatedWpm = fallbackEstimatedWpm,
                )
            }
    }
    return estimatedWpm
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
    onExternalImportUriConsumed: (Uri) -> Unit,
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
            "library", "library?tab={tab}" -> StartingTutorialRoute.LIBRARY
            "settings" -> StartingTutorialRoute.SETTINGS
            "reader/{bookId}", "reader/{bookId}/{chapterIndex}/{tokenIndex}" ->
                StartingTutorialRoute.READER
            "rsvp/{bookId}/{chapterIndex}/{tokenIndex}" -> StartingTutorialRoute.RSVP
            "rsvp/{bookId}/{chapterIndex}/{tokenIndex}?tempoMs={tempoMs}" ->
                StartingTutorialRoute.RSVP
            else -> null
        }

    fun navigateToTutorialRoute(route: StartingTutorialRoute) {
        if (resolveCurrentTutorialRoute(currentRoute) == route) return
        when (route) {
            StartingTutorialRoute.LIBRARY -> {
                navController.navigate("library") {
                    popUpTo("library") { inclusive = false }
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.SETTINGS -> {
                navController.navigate("settings") {
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.READER -> {
                val context = tutorialLaunchContext ?: return
                navController.navigate(
                    "reader/${context.bookId}/${context.chapterIndex}/${context.tokenIndex}"
                ) {
                    launchSingleTop = true
                }
            }

            StartingTutorialRoute.RSVP -> {
                val context = tutorialLaunchContext ?: return
                navController.navigate(
                    "rsvp/${context.bookId}/${context.chapterIndex}/${context.tokenIndex}"
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

    fun moveStartingTutorial(stepDelta: Int) {
        val nextIndex = tutorialStepIndex + stepDelta
        if (nextIndex !in tutorialSteps.indices) {
            dismissStartingTutorial(markAsSeen = true)
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

    fun handleImportFile(uri: Uri) {
        if (importState.isImporting) return
        val displayName = resolveImportFileName(context, uri)
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
            val result = runCatching { container.libraryRepository.import(uri) }
            withContext(Dispatchers.Main) {
                importProgressJob?.cancel()
                if (result.isSuccess) {
                    importState = importState.copy(progress = 1f)
                    delay(200)
                }
                importState = ImportUiState()
                result.onSuccess { book ->
                    val chapterCount = book.chapters.size
                    val message =
                        resources.getQuantityString(
                            R.plurals.toast_imported_with_chapter_count,
                            chapterCount,
                            book.title,
                            chapterCount,
                        )
                    showUserMessage(message)
                }
                result.onFailure { error ->
                    val message =
                        error.message?.let {
                            resources.getString(R.string.toast_import_failed_detail, it)
                        } ?: resources.getString(R.string.toast_import_failed_unknown)
                    showUserMessage(message, duration = SnackbarDuration.Long)
                }
            }
        }
    }

    LaunchedEffect(externalImportUri, importState.isImporting) {
        val uri = externalImportUri ?: return@LaunchedEffect
        if (importState.isImporting) return@LaunchedEffect
        onExternalImportUriConsumed(uri)
        navController.navigate("library") {
            popUpTo("library") { inclusive = false }
            launchSingleTop = true
        }
        handleImportFile(uri)
    }

    LaunchedEffect(prefs.hasSeenStartingTutorial, externalImportUri, importState.isImporting) {
        if (!prefs.hasSeenStartingTutorial &&
            !tutorialAutoStarted &&
            externalImportUri == null &&
            !importState.isImporting
        ) {
            startStartingTutorial()
        }
    }
    val focusEnabledForRoute =
        prefs.focusModeEnabled &&
            when (currentRoute) {
                "settings" -> true
                "settings/language" -> true
                "reader/{bookId}" -> prefs.focusApplyInReader
                "reader/{bookId}/{chapterIndex}/{tokenIndex}" -> prefs.focusApplyInReader
                "rsvp/{bookId}/{chapterIndex}/{tokenIndex}" -> prefs.focusApplyInRsvp
                "rsvp/{bookId}/{chapterIndex}/{tokenIndex}?tempoMs={tempoMs}" ->
                    prefs.focusApplyInRsvp
                else -> false
            }
    FocusModeSideEffects(
        enabled = focusEnabledForRoute,
        hideStatusBar = prefs.focusHideStatusBar,
        pauseNotifications = prefs.focusPauseNotifications,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                LibraryScreen(
                    books = books,
                    bookmarks = bookmarks,
                    bookProgress = libraryProgress,
                    initialTab = LibraryTab.Library,
                    importState = importState,
                    onOpen = { book ->
                        // Navigate to reader - saved position will be restored there
                        navController.navigate("reader/${book.id.value}")
                    },
                    onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                        coroutineScope.launch(dispatcherProvider.io) {
                            container.readingPositionRepository.savePosition(
                                ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                            )
                        }
                        navController.navigate("reader/$bookId/$chapterIndex/$tokenIndex")
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
                    onSettings = { navController.navigate("settings") },
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
                route = "library?tab={tab}",
                arguments =
                listOf(
                    navArgument("tab") {
                        type = NavType.StringType
                        defaultValue = "library"
                    },
                ),
            ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab") ?: "library"
            val initialTab =
                when (tab.lowercase()) {
                    "completed" -> LibraryTab.Completed
                    "bookmarks" -> LibraryTab.Bookmarks
                    else -> LibraryTab.Library
                }
            LibraryScreen(
                books = books,
                bookmarks = bookmarks,
                bookProgress = libraryProgress,
                initialTab = initialTab,
                importState = importState,
                onOpen = { book ->
                    navController.navigate("reader/${book.id.value}")
                },
                onOpenBookmark = { bookId, chapterIndex, tokenIndex ->
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(BookId(bookId), chapterIndex, tokenIndex),
                        )
                    }
                    navController.navigate("reader/$bookId/$chapterIndex/$tokenIndex")
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
                onSettings = { navController.navigate("settings") },
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
            route = "reader/{bookId}",
            arguments =
            listOf(
                navArgument("bookId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable

            // Load book once
            val initialBook: Book? = null
            val bookState =
                produceState(
                    initialValue = initialBook,
                    bookId,
                ) {
                    value =
                        runCatching { container.bookRepository.getBook(BookId(bookId)) }.getOrNull()
                }
            val book = bookState.value
            if (book == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@composable
            }

            // Use ViewModel for chapter caching and preloading
            val readerViewModel: ReaderViewModel =
                viewModel(
                    factory =
                    ReaderViewModel.factory(
                        container.bookRepository,
                        container.tokenRepository,
                        dispatcherProvider,
                    ),
                )
            val uiState by readerViewModel.uiState.collectAsState()

            // Resume index returned from RSVP. Use it immediately to avoid focus "jump".
            val rsvpResultFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(RSVP_RESULT_TOKEN_INDEX_KEY, -1)
                }
            val rsvpResultIndex by rsvpResultFlow.collectAsState(initial = -1)
            val rsvpResultChapterFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(RSVP_RESULT_CHAPTER_INDEX_KEY, -1)
                }
            val rsvpResultChapterIndex by rsvpResultChapterFlow.collectAsState(initial = -1)
            val rsvpResumeCursorFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(RSVP_RESULT_RESUME_CURSOR_KEY, -1)
                }
            val rsvpResultResumeCursor by rsvpResumeCursorFlow.collectAsState(initial = -1)
            val safeRsvpResultIndex =
                if (rsvpResultIndex >= 0) {
                    val tokens = uiState.chapterData?.tokens
                    if (rsvpResultChapterIndex == uiState.chapterIndex &&
                        tokens != null &&
                        tokens.isNotEmpty()
                    ) {
                        tokens.nearestWordIndex(rsvpResultIndex)
                    } else {
                        rsvpResultIndex.coerceAtLeast(0)
                    }
                } else {
                    rsvpResultIndex
                }
            val effectiveUiState =
                if (safeRsvpResultIndex >= 0 &&
                    rsvpResultChapterIndex == uiState.chapterIndex
                ) {
                    uiState.copy(focusIndex = safeRsvpResultIndex)
                } else {
                    uiState
                }

            LaunchedEffect(rsvpResultChapterIndex, safeRsvpResultIndex, rsvpResultResumeCursor) {
                if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
                    if (rsvpResultChapterIndex != uiState.chapterIndex) {
                        readerViewModel.loadChapter(rsvpResultChapterIndex, safeRsvpResultIndex)
                    } else if (safeRsvpResultIndex != uiState.focusIndex) {
                        readerViewModel.applyFocusIndex(safeRsvpResultIndex)
                    }
                    val wordIndex =
                        if (rsvpResultChapterIndex == uiState.chapterIndex) {
                            resolveWordIndex(
                                uiState.chapterData?.wordCountByToken,
                                safeRsvpResultIndex,
                            )
                        } else {
                            0
                        }
                    val resumeCursor =
                        if (rsvpResultChapterIndex == uiState.chapterIndex) {
                            rsvpResultResumeCursor
                        } else {
                            -1
                        }
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                rsvpResultChapterIndex,
                                safeRsvpResultIndex,
                                wordIndex,
                                rsvpResumeCursor = resumeCursor,
                            ),
                        )
                    }
                    backStackEntry.savedStateHandle[RSVP_RESULT_CHAPTER_INDEX_KEY] = -1
                    backStackEntry.savedStateHandle[RSVP_RESULT_TOKEN_INDEX_KEY] = -1
                    backStackEntry.savedStateHandle[RSVP_RESULT_RESUME_CURSOR_KEY] = -1
                }
            }

            // Track if we've done initial load
            var hasInitialized by rememberSaveable { mutableStateOf(false) }

            // Load book with saved position on first entry
            LaunchedEffect(book) {
                if (!hasInitialized || uiState.chapterData == null) {
                    val savedPosition = container.readingPositionRepository.getPosition(
                        BookId(bookId)
                    )
                    val initialChapter = savedPosition?.chapterIndex ?: 0
                    val initialFocus = savedPosition?.tokenIndex ?: 0
                    readerViewModel.loadBook(book, initialChapter, initialFocus)
                    hasInitialized = true
                }
            }

            // Sync focus from storage when returning from RSVP (lifecycle resumes)
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

            LaunchedEffect(lifecycleState) {
                if (lifecycleState == Lifecycle.State.RESUMED && hasInitialized) {
                    // If a resume index from RSVP is pending, don't overwrite it.
                    if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
                        return@LaunchedEffect
                    }
                    val savedPosition = container.readingPositionRepository.getPosition(
                        BookId(bookId)
                    )
                    if (savedPosition != null &&
                        savedPosition.chapterIndex == uiState.chapterIndex
                    ) {
                        val tokens = uiState.chapterData?.tokens
                        if (tokens != null && savedPosition.tokenIndex != uiState.focusIndex) {
                            readerViewModel.applyFocusIndex(
                                savedPosition.tokenIndex.coerceIn(0, tokens.lastIndex)
                            )
                        }
                    }
                }
            }

            LaunchedEffect(uiState.chapterIndex, uiState.chapterData) {
                if (!hasInitialized) return@LaunchedEffect
                val tokens = uiState.chapterData?.tokens ?: return@LaunchedEffect
                if (tokens.isEmpty()) return@LaunchedEffect
                val safeIndex =
                    tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
                val wordIndex =
                    resolveWordIndex(uiState.chapterData?.wordCountByToken, safeIndex)
                withContext(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(
                            BookId(bookId),
                            uiState.chapterIndex,
                            safeIndex,
                            wordIndex,
                        ),
                    )
                }
            }

            val resolvedRsvpConfig =
                RsvpConfigResolver.resolve(prefs.rsvpConfig, book.languageTag)
            val readerEstimatedWpm =
                rememberReaderEstimatedWpm(
                    baseConfig = resolvedRsvpConfig,
                    fallbackEstimatedWpm = estimatedWpm,
                    dispatcherProvider = dispatcherProvider,
                )
            LaunchedEffect(
                uiState.chapterIndex,
                uiState.chapterData,
                uiState.focusIndex,
                resolvedRsvpConfig,
            ) {
                if (!hasInitialized) return@LaunchedEffect
                val chapterData = uiState.chapterData ?: return@LaunchedEffect
                if (chapterData.tokens.isEmpty()) return@LaunchedEffect
                val safeStartIndex =
                    chapterData.tokens.nearestWordIndex(uiState.focusIndex)
                        .coerceIn(0, chapterData.tokens.lastIndex)
                container.rsvpFrameRepository.prefetchFrames(
                    BookId(bookId),
                    uiState.chapterIndex,
                    resolvedRsvpConfig,
                    startIndex = safeStartIndex,
                )
            }

            val focusEnabledInReader = prefs.focusModeEnabled && prefs.focusApplyInReader
            var lastExplicitFocusIndex by remember(bookId) { mutableIntStateOf(-1) }
            fun buildCurrentReaderPosition(tokenIndex: Int = effectiveUiState.focusIndex): ReadingPosition? {
                val chapterData = effectiveUiState.chapterData ?: return null
                val tokens = chapterData.tokens
                if (tokens.isEmpty()) return null
                val safeIndex = tokens.nearestWordIndex(tokenIndex).coerceIn(0, tokens.lastIndex)
                val wordIndex = resolveWordIndex(chapterData.wordCountByToken, safeIndex)
                return ReadingPosition(
                    BookId(bookId),
                    effectiveUiState.chapterIndex,
                    safeIndex,
                    wordIndex,
                )
            }

            fun saveReaderPosition(position: ReadingPosition) {
                lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(position)
                }
            }

            fun saveCurrentReaderPosition() {
                buildCurrentReaderPosition()?.let(::saveReaderPosition)
            }

            BackHandler(enabled = !tutorialActive) {
                val position =
                    lastExplicitFocusIndex
                        .takeIf { it >= 0 }
                        ?.let(::buildCurrentReaderPosition)
                        ?: buildCurrentReaderPosition()
                lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                    if (position != null) {
                        container.readingPositionRepository.savePosition(position)
                    }
                    withContext(Dispatchers.Main) {
                        navController.navigate("library") {
                            popUpTo("library") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            }

            ReaderScreen(
                book = book,
                uiState = effectiveUiState,
                fontSizeSp = prefs.readerFontSizeSp,
                invertedScroll = prefs.invertedScroll,
                readerTheme = prefs.readerTheme,
                textBrightness = prefs.readerTextBrightness,
                estimatedWpm = readerEstimatedWpm,
                onFontSizeChange = { size ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFontSize(size)
                    }
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
                focusModeEnabled = focusEnabledInReader,
                onFocusModeEnabledChange = { enabled ->
                    coroutineScope.launch {
                        if (enabled) {
                            if (!prefs.focusModeEnabled) {
                                container.preferencesRepository.updateFocusModeEnabled(true)
                            }
                            container.preferencesRepository.updateFocusApplyInReader(true)
                        } else {
                            container.preferencesRepository.updateFocusApplyInReader(false)
                        }
                    }
                },
                onAddBookmark = { chapterIndex, tokenIndex, previewText ->
                    coroutineScope.launch {
                        val id = "$bookId:$chapterIndex:$tokenIndex"
                        container.bookmarkRepository.add(
                            Bookmark(
                                id = id,
                                bookId = BookId(bookId),
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
                    saveCurrentReaderPosition()
                    navController.navigate("library?tab=bookmarks")
                },
                onFocusChange = { newFocusIndex ->
                    lastExplicitFocusIndex = newFocusIndex
                    readerViewModel.setFocusIndex(newFocusIndex)
                    // Save position when focus changes
                    val wordIndex =
                        resolveWordIndex(uiState.chapterData?.wordCountByToken, newFocusIndex)
                    lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                uiState.chapterIndex,
                                newFocusIndex,
                                wordIndex,
                            ),
                        )
                    }
                },
                onStartRsvp = { start ->
                    RsvpLaunchSnapshotStore.put(
                        bookId = bookId,
                        chapterIndex = uiState.chapterIndex,
                        tokens = uiState.chapterData?.tokens.orEmpty(),
                    )
                    val wordIndex =
                        resolveWordIndex(uiState.chapterData?.wordCountByToken, start)
                    lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                        val existingPosition = container.readingPositionRepository.getPosition(BookId(bookId))
                        val resumeCursor =
                            existingPosition
                                ?.takeIf {
                                    it.chapterIndex == uiState.chapterIndex &&
                                        it.tokenIndex == start
                                }?.rsvpResumeCursor ?: -1
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                uiState.chapterIndex,
                                start,
                                wordIndex,
                                rsvpResumeCursor = resumeCursor,
                            ),
                        )
                        withContext(Dispatchers.Main) {
                            navController.navigate(
                                buildRsvpRoute(
                                    bookId = bookId,
                                    chapterIndex = uiState.chapterIndex,
                                    tokenIndex = start,
                                )
                            )
                        }
                    }
                },
                onChapterChange = { newIndex, focusIndex ->
                    readerViewModel.loadChapter(newIndex, focusIndex)
                },
                onViewportMetricsChanged = { resolvedFontSizeSp, viewportHeightDp ->
                    readerViewModel.updatePaginationMetrics(resolvedFontSizeSp, viewportHeightDp)
                },
                tutorialState = readerTutorialState,
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
        }

        composable(
            route = "reader/{bookId}/{chapterIndex}/{tokenIndex}",
            arguments =
            listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterIndex") { type = NavType.IntType },
                navArgument("tokenIndex") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val initialChapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0
            val initialTokenIndex = backStackEntry.arguments?.getInt("tokenIndex") ?: 0

            val initialBook: Book? = null
            val bookState =
                produceState(
                    initialValue = initialBook,
                    bookId,
                ) {
                    value =
                        runCatching {
                            container.bookRepository.getBook(BookId(bookId))
                        }.getOrNull()
                }
            val book = bookState.value
            if (book == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@composable
            }

            val readerViewModel: ReaderViewModel =
                viewModel(
                    factory =
                    ReaderViewModel.factory(
                        container.bookRepository,
                        container.tokenRepository,
                        dispatcherProvider,
                    ),
                )
            val uiState by readerViewModel.uiState.collectAsState()

            // Resume index returned from RSVP. Use it immediately to avoid focus "jump".
            val rsvpResultFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(RSVP_RESULT_TOKEN_INDEX_KEY, -1)
                }
            val rsvpResultIndex by rsvpResultFlow.collectAsState(initial = -1)
            val rsvpResultChapterFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(RSVP_RESULT_CHAPTER_INDEX_KEY, -1)
                }
            val rsvpResultChapterIndex by rsvpResultChapterFlow.collectAsState(initial = -1)
            val rsvpResumeCursorFlow =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle.getStateFlow(RSVP_RESULT_RESUME_CURSOR_KEY, -1)
                }
            val rsvpResultResumeCursor by rsvpResumeCursorFlow.collectAsState(initial = -1)
            val safeRsvpResultIndex =
                if (rsvpResultIndex >= 0) {
                    val tokens = uiState.chapterData?.tokens
                    if (rsvpResultChapterIndex == uiState.chapterIndex &&
                        tokens != null &&
                        tokens.isNotEmpty()
                    ) {
                        tokens.nearestWordIndex(rsvpResultIndex)
                    } else {
                        rsvpResultIndex.coerceAtLeast(0)
                    }
                } else {
                    rsvpResultIndex
                }
            val effectiveUiState =
                if (safeRsvpResultIndex >= 0 &&
                    rsvpResultChapterIndex == uiState.chapterIndex
                ) {
                    uiState.copy(focusIndex = safeRsvpResultIndex)
                } else {
                    uiState
                }

            LaunchedEffect(rsvpResultChapterIndex, safeRsvpResultIndex, rsvpResultResumeCursor) {
                if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
                    if (rsvpResultChapterIndex != uiState.chapterIndex) {
                        readerViewModel.loadChapter(rsvpResultChapterIndex, safeRsvpResultIndex)
                    } else if (safeRsvpResultIndex != uiState.focusIndex) {
                        readerViewModel.applyFocusIndex(safeRsvpResultIndex)
                    }
                    val wordIndex =
                        if (rsvpResultChapterIndex == uiState.chapterIndex) {
                            resolveWordIndex(
                                uiState.chapterData?.wordCountByToken,
                                safeRsvpResultIndex,
                            )
                        } else {
                            0
                        }
                    val resumeCursor =
                        if (rsvpResultChapterIndex == uiState.chapterIndex) {
                            rsvpResultResumeCursor
                        } else {
                            -1
                        }
                    coroutineScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                rsvpResultChapterIndex,
                                safeRsvpResultIndex,
                                wordIndex,
                                rsvpResumeCursor = resumeCursor,
                            ),
                        )
                    }
                    backStackEntry.savedStateHandle[RSVP_RESULT_CHAPTER_INDEX_KEY] = -1
                    backStackEntry.savedStateHandle[RSVP_RESULT_TOKEN_INDEX_KEY] = -1
                    backStackEntry.savedStateHandle[RSVP_RESULT_RESUME_CURSOR_KEY] = -1
                }
            }

            // Track if we've done initial load
            var hasInitialized by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(book) {
                if (!hasInitialized || uiState.chapterData == null) {
                    val savedPosition =
                        if (hasInitialized) {
                            container.readingPositionRepository.getPosition(
                                BookId(bookId)
                            )
                        } else {
                            null
                        }
                    val initialChapter = savedPosition?.chapterIndex ?: initialChapterIndex
                    val initialFocus = savedPosition?.tokenIndex ?: initialTokenIndex
                    readerViewModel.loadBook(book, initialChapter, initialFocus)
                    hasInitialized = true
                }
            }

            // Sync focus from storage when returning from RSVP (lifecycle resumes)
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

            LaunchedEffect(lifecycleState) {
                if (lifecycleState == Lifecycle.State.RESUMED && hasInitialized) {
                    if (rsvpResultChapterIndex >= 0 && safeRsvpResultIndex >= 0) {
                        return@LaunchedEffect
                    }
                    val savedPosition = container.readingPositionRepository.getPosition(
                        BookId(bookId)
                    )
                    if (savedPosition != null &&
                        savedPosition.chapterIndex == uiState.chapterIndex
                    ) {
                        val tokens = uiState.chapterData?.tokens
                        if (tokens != null && savedPosition.tokenIndex != uiState.focusIndex) {
                            readerViewModel.applyFocusIndex(
                                savedPosition.tokenIndex.coerceIn(0, tokens.lastIndex)
                            )
                        }
                    }
                }
            }

            LaunchedEffect(uiState.chapterIndex, uiState.chapterData) {
                if (!hasInitialized) return@LaunchedEffect
                val tokens = uiState.chapterData?.tokens ?: return@LaunchedEffect
                if (tokens.isEmpty()) return@LaunchedEffect
                val safeIndex =
                    tokens.nearestWordIndex(uiState.focusIndex).coerceIn(0, tokens.lastIndex)
                val wordIndex =
                    resolveWordIndex(uiState.chapterData?.wordCountByToken, safeIndex)
                withContext(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(
                        ReadingPosition(
                            BookId(bookId),
                            uiState.chapterIndex,
                            safeIndex,
                            wordIndex,
                        ),
                    )
                }
            }

            val resolvedRsvpConfig =
                RsvpConfigResolver.resolve(prefs.rsvpConfig, book.languageTag)
            val readerEstimatedWpm =
                rememberReaderEstimatedWpm(
                    baseConfig = resolvedRsvpConfig,
                    fallbackEstimatedWpm = estimatedWpm,
                    dispatcherProvider = dispatcherProvider,
                )
            LaunchedEffect(
                uiState.chapterIndex,
                uiState.chapterData,
                uiState.focusIndex,
                resolvedRsvpConfig,
            ) {
                if (!hasInitialized) return@LaunchedEffect
                val chapterData = uiState.chapterData ?: return@LaunchedEffect
                if (chapterData.tokens.isEmpty()) return@LaunchedEffect
                val safeStartIndex =
                    chapterData.tokens.nearestWordIndex(uiState.focusIndex)
                        .coerceIn(0, chapterData.tokens.lastIndex)
                container.rsvpFrameRepository.prefetchFrames(
                    BookId(bookId),
                    uiState.chapterIndex,
                    resolvedRsvpConfig,
                    startIndex = safeStartIndex,
                )
            }

            val focusEnabledInReader = prefs.focusModeEnabled && prefs.focusApplyInReader
            var lastExplicitFocusIndex by remember(bookId) { mutableIntStateOf(-1) }
            fun buildCurrentReaderPosition(tokenIndex: Int = effectiveUiState.focusIndex): ReadingPosition? {
                val chapterData = effectiveUiState.chapterData ?: return null
                val tokens = chapterData.tokens
                if (tokens.isEmpty()) return null
                val safeIndex = tokens.nearestWordIndex(tokenIndex).coerceIn(0, tokens.lastIndex)
                val wordIndex = resolveWordIndex(chapterData.wordCountByToken, safeIndex)
                return ReadingPosition(
                    BookId(bookId),
                    effectiveUiState.chapterIndex,
                    safeIndex,
                    wordIndex,
                )
            }

            fun saveReaderPosition(position: ReadingPosition) {
                lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                    container.readingPositionRepository.savePosition(position)
                }
            }

            fun saveCurrentReaderPosition() {
                buildCurrentReaderPosition()?.let(::saveReaderPosition)
            }

            BackHandler(enabled = !tutorialActive) {
                val position =
                    lastExplicitFocusIndex
                        .takeIf { it >= 0 }
                        ?.let(::buildCurrentReaderPosition)
                        ?: buildCurrentReaderPosition()
                lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                    if (position != null) {
                        container.readingPositionRepository.savePosition(position)
                    }
                    withContext(Dispatchers.Main) {
                        navController.navigate("library") {
                            popUpTo("library") { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
            }

            ReaderScreen(
                book = book,
                uiState = effectiveUiState,
                fontSizeSp = prefs.readerFontSizeSp,
                invertedScroll = prefs.invertedScroll,
                readerTheme = prefs.readerTheme,
                textBrightness = prefs.readerTextBrightness,
                estimatedWpm = readerEstimatedWpm,
                onFontSizeChange = { size ->
                    coroutineScope.launch {
                        container.preferencesRepository.updateFontSize(size)
                    }
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
                focusModeEnabled = focusEnabledInReader,
                onFocusModeEnabledChange = { enabled ->
                    coroutineScope.launch {
                        if (enabled) {
                            if (!prefs.focusModeEnabled) {
                                container.preferencesRepository.updateFocusModeEnabled(true)
                            }
                            container.preferencesRepository.updateFocusApplyInReader(true)
                        } else {
                            container.preferencesRepository.updateFocusApplyInReader(false)
                        }
                    }
                },
                onAddBookmark = { chapterIndex, tokenIndex, previewText ->
                    coroutineScope.launch {
                        val id = "$bookId:$chapterIndex:$tokenIndex"
                        container.bookmarkRepository.add(
                            Bookmark(
                                id = id,
                                bookId = BookId(bookId),
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
                    saveCurrentReaderPosition()
                    navController.navigate("library?tab=bookmarks")
                },
                onFocusChange = { newFocusIndex ->
                    lastExplicitFocusIndex = newFocusIndex
                    readerViewModel.setFocusIndex(newFocusIndex)
                    val wordIndex =
                        resolveWordIndex(uiState.chapterData?.wordCountByToken, newFocusIndex)
                    lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                uiState.chapterIndex,
                                newFocusIndex,
                                wordIndex,
                            ),
                        )
                    }
                },
                onStartRsvp = { start ->
                    RsvpLaunchSnapshotStore.put(
                        bookId = bookId,
                        chapterIndex = uiState.chapterIndex,
                        tokens = uiState.chapterData?.tokens.orEmpty(),
                    )
                    val wordIndex =
                        resolveWordIndex(uiState.chapterData?.wordCountByToken, start)
                    lifecycleOwner.lifecycleScope.launch(dispatcherProvider.io) {
                        val existingPosition = container.readingPositionRepository.getPosition(BookId(bookId))
                        val resumeCursor =
                            existingPosition
                                ?.takeIf {
                                    it.chapterIndex == uiState.chapterIndex &&
                                        it.tokenIndex == start
                                }?.rsvpResumeCursor ?: -1
                        container.readingPositionRepository.savePosition(
                            ReadingPosition(
                                BookId(bookId),
                                uiState.chapterIndex,
                                start,
                                wordIndex,
                                rsvpResumeCursor = resumeCursor,
                            ),
                        )
                        withContext(Dispatchers.Main) {
                            navController.navigate(
                                buildRsvpRoute(
                                    bookId = bookId,
                                    chapterIndex = uiState.chapterIndex,
                                    tokenIndex = start,
                                )
                            )
                        }
                    }
                },
                onChapterChange = { newIndex, focusIndex ->
                    readerViewModel.loadChapter(newIndex, focusIndex)
                },
                onViewportMetricsChanged = { resolvedFontSizeSp, viewportHeightDp ->
                    readerViewModel.updatePaginationMetrics(resolvedFontSizeSp, viewportHeightDp)
                },
                tutorialState = readerTutorialState,
                onTutorialNext = { moveStartingTutorial(1) },
                onTutorialPrevious = { moveStartingTutorial(-1) },
                onTutorialSkip = { dismissStartingTutorial() },
            )
        }

        composable(
            route = "rsvp/{bookId}/{chapterIndex}/{tokenIndex}?tempoMs={tempoMs}",
            arguments =
            listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("chapterIndex") { type = NavType.IntType },
                navArgument("tokenIndex") { type = NavType.IntType },
                navArgument("tempoMs") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
            ),
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val chapterIndex = backStackEntry.arguments?.getInt("chapterIndex") ?: 0
            val startIndex = backStackEntry.arguments?.getInt("tokenIndex") ?: 0
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
            val bookIdValue = BookId(bookId)
            val savedCurrentTokenIndex =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle[RSVP_CURRENT_TOKEN_INDEX_KEY] ?: -1
                }
            val savedCurrentResumeCursor =
                remember(backStackEntry) {
                    backStackEntry.savedStateHandle[RSVP_CURRENT_RESUME_CURSOR_KEY] ?: -1
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
                        RSVP_PLAYBACK_IS_PLAYING_KEY,
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
            val rsvpState =
                RsvpScreenState(
                    book =
                    RsvpBookContext(
                        bookId = bookIdValue,
                        chapterIndex = chapterIndex,
                        tokens = tokens,
                        startIndex = safeStartIndex,
                        startResumeCursor = startResumeCursor,
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
                            navController.navigate("library?tab=bookmarks") {
                                popUpTo("library") { inclusive = false }
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
                            coroutineScope.launch {
                                container.readingPositionRepository.savePosition(
                                    ReadingPosition(
                                        bookIdValue,
                                        returnTarget.chapterIndex,
                                        returnTarget.tokenIndex,
                                        wordIndex,
                                        rsvpResumeCursor = returnTarget.resumeCursor,
                                    ),
                                )
                            }
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(RSVP_RESULT_CHAPTER_INDEX_KEY, returnTarget.chapterIndex)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(RSVP_RESULT_TOKEN_INDEX_KEY, returnTarget.tokenIndex)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(RSVP_RESULT_RESUME_CURSOR_KEY, returnTarget.resumeCursor)
                            navController.popBackStack()
                        },
                        onPositionChanged = { resumePoint ->
                            val safeIndex =
                                if (tokens.isNotEmpty()) {
                                    resumePoint.tokenIndex.coerceIn(0, tokens.lastIndex)
                                } else {
                                    0
                                }
                            backStackEntry.savedStateHandle[RSVP_CURRENT_TOKEN_INDEX_KEY] =
                                safeIndex
                            backStackEntry.savedStateHandle[RSVP_CURRENT_RESUME_CURSOR_KEY] =
                                resumePoint.resumeCursor
                            val wordIndex = resolveWordIndex(wordCountByToken, safeIndex)
                            coroutineScope.launch(dispatcherProvider.io) {
                                container.readingPositionRepository.savePosition(
                                    ReadingPosition(
                                        bookIdValue,
                                        chapterIndex,
                                        safeIndex,
                                        wordIndex,
                                        rsvpResumeCursor = resumePoint.resumeCursor,
                                    ),
                                )
                            }
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
                            coroutineScope.launch(dispatcherProvider.io) {
                                container.readingPositionRepository.savePosition(
                                    ReadingPosition(
                                        bookIdValue,
                                        chapterIndex,
                                        resumeIndex,
                                        wordIndex,
                                        rsvpResumeCursor = resumePoint.resumeCursor,
                                    ),
                                )
                            }
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(RSVP_RESULT_CHAPTER_INDEX_KEY, chapterIndex)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(RSVP_RESULT_TOKEN_INDEX_KEY, resumeIndex)
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(RSVP_RESULT_RESUME_CURSOR_KEY, resumePoint.resumeCursor)
                            navController.popBackStack()
                        },
                        onPlaybackStateChanged = { isPlaying ->
                            backStackEntry.savedStateHandle[RSVP_PLAYBACK_IS_PLAYING_KEY] =
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

        composable("settings") {
            SettingsHomeScreen(
                onOpenLanguage = {
                    navController.navigate("settings/language")
                },
                onOpenRsvp = { navController.navigate("settings/rsvp") },
                onOpenReader = { navController.navigate("settings/reader") },
                onOpenFocus = { navController.navigate("settings/focus") },
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

        composable("settings/language") {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }

        composable("settings/rsvp") {
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

        composable("settings/reader") {
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

            composable("settings/focus") {
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
