# Kairo – AGENTS

Kairo is a Kotlin / Android RSVP-first ebook reader that:

- Imports `.epub` and `.mobi` files
- Presents a scrollable “reader view” with a single enlarged focus word
- Switches into a minimal RSVP view when the focus word is tapped
- Provides a customisable RSVP engine and user preferences

## Mandatory engineering contract

This section applies to every human contributor and every automated coding agent. It takes precedence over the module descriptions below.

Before changing the repository:

1. Read this file and `CONTRIBUTING.md` completely.
2. Inspect the current branch and working tree. Preserve unrelated and pre-existing changes.
3. Read the nearest implementation and tests before editing; reuse existing domain seams rather than creating parallel systems.

While making changes:

- Keep behavior changes, formatting changes, and policy changes logically separable.
- Use named domain/protocol constants instead of unexplained literals.
- Prefer typed state, action, request, and result objects over long parameter lists.
- Split parsing, timing, navigation, and rendering into cohesive stages before they exceed the configured Detekt boundaries.
- Keep Compose functions declarative and move business logic into testable Kotlin functions or state holders.
- Add or update focused tests for every behavior change and regression fix.
- Never add a Detekt or ktlint baseline, set `ignoreFailures`, disable a quality task, or widen a quality threshold merely to make CI pass.
- Suppress a rule only on the narrowest declaration where the representation is inherently exceptional. Every new suppression must include a nearby reason.
- Changes to `detekt.yml`, `.editorconfig`, quality Gradle tasks, hooks, or CI require explicit justification and must not reduce enforcement without user approval.

Before declaring work complete:

1. Run `./gradlew qualityCheck` for all Kotlin, resource, Gradle, manifest, or test changes.
2. Run `./gradlew qualityGate` when changing UI, Android integration, resources, manifests, dependencies, build configuration, or release behavior.
3. Run `git diff --check` and inspect the final diff for unrelated churn, generated files, debug logging, and accidental API changes.
4. Report any check that could not run. Do not describe an unexecuted check as passing.

The required CI check is `Quality gate`. Local hooks are installed with `./scripts/setup-dev.sh`, but CI remains authoritative because hooks can be bypassed.

This document defines the core “agents” (modules / responsibilities) that together implement Kairo.

---

## Agent 0 – System Architect

**Goal**
Define the high-level architecture, module boundaries, and data flow so that all other agents can work independently and integrate cleanly.

**Responsibilities**

- Decide high-level stack:
  - Android, Kotlin, Jetpack Compose UI
  - Room + DataStore for persistence
  - Readium (or similar) for EPUB; dedicated MOBI parser / converter for DRM-free MOBI
- Define core modules:
  - `core-model`
  - `core-rsvp`
  - `data-books`
  - `data-preferences`
  - `ui-library`
  - `ui-reader`
  - `ui-rsvp`
  - `ui-settings`
- Define navigation flow:
  - Library → Reader → RSVP → Reader
- Define error handling and logging conventions.

**Inputs**

- Product requirements
- Android platform constraints
- Library capabilities (Readium, MOBI parser)

**Outputs**

- High-level architecture diagram
- Module list and Gradle config skeleton
- Shared conventions (naming, package layout, error handling)

---

## Agent 1 – Domain Model & Book Abstractions

**Goal**
Represent books, chapters, tokens, reading positions, and RSVP configuration in a clean, format-agnostic way.

**Responsibilities**

- Define data classes:

  ```kotlin
  data class BookId(val value: String)

  data class Book(
      val id: BookId,
      val title: String,
      val authors: List<String>,
      val chapters: List<Chapter>,
      val coverImage: ByteArray? = null
  )

  data class Chapter(
      val index: Int,
      val title: String?,
      val htmlContent: String,
      val plainText: String
  )

  data class ReadingPosition(
      val bookId: BookId,
      val chapterIndex: Int,
      val tokenIndex: Int
  )

  enum class TokenType { WORD, PUNCTUATION, PARAGRAPH_BREAK }

  data class Token(
      val text: String,
      val type: TokenType,
      val orpIndex: Int? = null,
      val pauseAfterMs: Long = 0L
  )

  data class RsvpConfig(
      val baseWpm: Int,
      val wordsPerFrame: Int,
      val maxChunkLength: Int,
      val punctuationPauseFactor: Double,
      val paragraphPauseMs: Long,
      val longWordMultiplier: Double,
      val orpEnabled: Boolean,
      val startDelayMs: Long,
      val endDelayMs: Long
  )
  ```

* Ensure domain model is UI-agnostic and parser-agnostic.
* Provide small helper functions (e.g. ORP index calculation).

**Inputs**

* Requirements from RSVP Engine Agent
* Requirements from Reader / RSVP UI agents

**Outputs**

* Kotlin data classes in `core-model`
* Utility methods for token and ORP operations

---

## Agent 2 – Import & Parsing (EPUB / MOBI)

**Goal**
Load `.epub` and `.mobi` files, parse them into the shared domain model, and persist them.

**Responsibilities**

* Define the parser interface:

  ```kotlin
  interface BookParser {
      suspend fun parse(uri: Uri): Book
      fun supports(extension: String): Boolean
  }
  ```

* Implement:

    * `EpubBookParser` using Readium or equivalent
    * `MobiBookParser` using a MOBI library or pre-conversion step for DRM-free MOBI

* Extract:

    * Metadata (title, authors, cover)
    * Chapter boundaries and titles
    * Original HTML content per chapter

* Normalise chapter content:

    * Strip or simplify HTML into well-formed text
    * Ensure paragraphs and punctuation are preserved for later tokenisation

* Implement `BookRepository`:

  ```kotlin
  interface BookRepository {
      suspend fun importBook(uri: Uri): Book
      suspend fun getBook(bookId: BookId): Book
      suspend fun getChapter(bookId: BookId, chapterIndex: Int): Chapter
  }
  ```

**Inputs**

* Raw URIs from Android storage picker
* Underlying parsing libraries

**Outputs**

* `Book` and `Chapter` instances persisted via Room
* Errors and status for UI (e.g. unsupported DRM)

---

## Agent 3 – Tokenisation & Text Normalisation

**Goal**
Convert chapter text into precise tokens suitable for RSVP, and maintain stable token indices.

**Responsibilities**

* Implement a tokenizer that:

    * Splits chapter `plainText` into `Token`s
    * Handles:

        * Words
        * Sentence-ending punctuation (., !, ?)
        * Mid-sentence punctuation (, ; :)
        * Paragraph boundaries
    * Cleans up extra whitespace.

* Annotate tokens:

    * `TokenType`
    * ORP index per word
    * Extra pause weights (e.g. after full stops or paragraph breaks)

* Provide a `TokenRepository` abstraction:

  ```kotlin
  interface TokenRepository {
      suspend fun getTokens(bookId: BookId, chapterIndex: Int): List<Token>
  }
  ```

    * Optionally cache tokens per chapter in DB or in-memory.

**Inputs**

* `Chapter` (plain text / basic HTML info)
* ORP rules from RSVP Engine Agent

**Outputs**

* Ordered list of `Token`s per chapter
* Stable mapping between `ReadingPosition.tokenIndex` and on-screen content

---

## Agent 4 – RSVP Engine

**Goal**
Generate a timed sequence of RSVP frames based on tokens and configuration, independent of Android UI.

**Responsibilities**

* Define frame model:

  ```kotlin
  data class RsvpFrame(
      val tokens: List<Token>,
      val durationMs: Long
  )
  ```

* Provide `RsvpEngine`:

  ```kotlin
  interface RsvpEngine {
      fun generateFrames(
          tokens: List<Token>,
          startIndex: Int,
          config: RsvpConfig
      ): List<RsvpFrame>
  }
  ```

* Implement default engine:

    * Compute base `msPerWord` from WPM
    * Adjust frame duration by:

        * Word length (long words → more time)
        * Punctuation (pause factors)
        * Paragraph breaks (extra pause)
    * Support:

        * `wordsPerFrame`
        * `maxChunkLength`
        * Start / end delays (handled either here or in controller)

* Keep implementation pure Kotlin (no Android dependencies).

**Inputs**

* Tokens from Tokenisation Agent
* `RsvpConfig` from Settings Agent

**Outputs**

* List or sequence of `RsvpFrame`s starting from a given token index

---

## Agent 5 – Reader UI (Scrollable View with Focus Word)

**Goal**
Display the book like a normal reader while visually emphasising a single “focus word” that maps to the RSVP starting point.

**Responsibilities**

* Implement `ReaderScreen` in Compose:

    * Displays chapter content as a scrollable view
    * Renders tokens in layout that respects line wrapping
    * Highlights one token as the “focus word”:

        * Larger font size
        * Colour highlight
    * Makes focus word tappable:

        * Triggers transition into RSVP mode

* Track reading position:

    * Maintain `ReadingPosition` in ViewModel
    * Update `focusIndex` as user scrolls or moves chapters
    * Save position back via `ReadingPositionRepository`

* Provide chapter navigation UI:

    * Bottom / top controls for next / previous chapter
    * Optional chapter picker (TOC)

**Inputs**

* `Book` and `Tokens` from repositories
* `ReadingPosition` from persistence
* User font / theme preferences

**Outputs**

* Visual reader experience
* Events: `OnFocusWordClick(tokenIndex)`, `OnChapterChanged`

---

## Agent 6 – RSVP UI & Playback Controller

**Goal**
Present a clean RSVP reading view and drive frame playback over time.

**Responsibilities**

* Implement `RsvpScreen` in Compose:

    * Large centred text, minimal chrome
    * Optional:

        * Progress bar / percentage
        * Remaining time estimate
    * Tap areas or gesture:

        * Tap to pause / play
        * Swipe left/right to step frames or adjust speed

* Implement RSVP controller (inside ViewModel or dedicated class):

    * Consume `RsvpFrame`s from the engine
    * Manage state: playing, paused, stopped
    * Step through frames via `delay(frame.durationMs)`
    * Handle exit:

        * Compute final `ReadingPosition` (chapter + token index)
        * Notify caller so Reader UI can update

* Integrate user preferences:

    * WPM, theme, ORP highlighting
    * Update engine input when settings change

**Inputs**

* `RsvpFrame`s from engine
* Current `ReadingPosition`
* `RsvpConfig` from Settings Agent

**Outputs**

* Real-time RSVP UI
* Updated `ReadingPosition` when RSVP ends or user exits

---

## Agent 7 – Settings & Preferences

**Goal**
Expose and persist user preferences for RSVP behaviour and reading appearance.

**Responsibilities**

* Define `UserPreferences`:

  ```kotlin
  data class UserPreferences(
      val rsvpConfig: RsvpConfig,
      val readerFontSizeSp: Float,
      val readerTheme: ReaderTheme
  )

  enum class ReaderTheme { LIGHT, DARK, SEPIA }
  ```

* Implement `PreferencesRepository` using DataStore:

    * Get / observe preferences as a Flow
    * Update WPM, theme, fonts etc.

* Implement `SettingsScreen` in Compose:

    * Sliders for WPM, font size
    * Toggles for ORP, words per frame
    * Theme selection (background, text colour)
    * Option to reset to defaults

**Inputs**

* Product requirements
* Feedback from RSVP / Reader UI agents

**Outputs**

* Persistent `UserPreferences`
* Reactive streams feeding Reader and RSVP screens

---

## Agent 8 – Persistence & Library Management

**Goal**
Store books, reading positions, and maintain the user’s library.

**Responsibilities**

* Implement Room entities & DAOs:

    * Books metadata
    * Chapters (or references to files)
    * Reading positions

* Implement repositories:

  ```kotlin
  interface ReadingPositionRepository {
      suspend fun getPosition(bookId: BookId): ReadingPosition?
      suspend fun savePosition(position: ReadingPosition)
  }
  ```

* Implement `LibraryRepository`:

    * List books with cover, title, authors
    * Delete books
    * Provide last-read position for each book

* Provide `LibraryScreen` UI:

    * Grid/list of books
    * “Import book” action (Storage Access Framework)
    * Sorting and filtering (optional)

**Inputs**

* Parsed `Book`s from Import Agent
* `ReadingPosition` updates from Reader / RSVP UI

**Outputs**

* Stable, resumable library
* Data for Library UI

---

## Agent 9 – QA, UX & Performance

**Goal**
Raise the overall quality of Kairo through tests, UX polish, and performance tuning.

**Responsibilities**

* Testing:

    * Unit tests for:

        * Tokenisation
        * RSVP engine timing
        * Reading position updates
    * Instrumented UI tests for:

        * Reader scroll + focus word behaviour
        * RSVP playback and resume

* UX and usability:

    * Smooth transitions:

        * Reader → RSVP and back
    * Accessibility:

        * Font scaling with system settings
        * High contrast themes
    * Animation quality (if any)

* Performance:

    * Efficient token caching
    * Avoid holding entire large books in memory
    * Precompute RSVP frames in chunks if needed

**Inputs**

* All modules
* Sample books of various sizes and formats

**Outputs**

* Test suites and coverage
* UX recommendations and refinements
* Performance benchmarks and fixes

---

## Implementation Order (Recommended)

1. **Agent 0 / 1** – Architecture + domain model
2. **Agent 2** – EPUB parsing + minimal library import
3. **Agent 3** – Tokenisation & ORP
4. **Agent 4** – RSVP engine (pure Kotlin)
5. **Agent 8** – Persistence & library management
6. **Agent 5** – Reader UI with focus word
7. **Agent 6** – RSVP UI & controller
8. **Agent 7** – Settings & preferences
9. **Agent 9** – QA, UX and performance pass

Once these are in place, you can iterate on the “revolutionary” RSVP behaviour (extra heuristics, adaptive pacing, experimental layouts) without disturbing the rest of Kairo.

```
::contentReference[oaicite:0]{index=0}
```
