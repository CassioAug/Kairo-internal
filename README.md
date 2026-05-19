# <p align="center">Kairo</p>

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/kairo_logo.png" alt="Kairo logo" width="220" />
</p>

<p align="center"><strong>An Android ebook reader built for momentum, focus, and high-speed reading.</strong></p>

Kairo is an RSVP-first ebook reader for Android. It lets you import DRM-free EPUB and MOBI books, read them in a comfortable scrollable reader, and jump into a tuned RSVP playback experience the moment you want to accelerate.

This project is for readers who want less friction between opening a book and actually moving through it. It is also for people who enjoy experimenting with reading speed, pacing, typography, and focus-friendly interfaces.

## About Kairo

Most reading apps treat rapid serial visual presentation as a side feature. Kairo starts from the opposite direction.

Kairo is designed around a simple idea: reading should feel intentional. Sometimes that means a clean, traditional reader with chapter navigation, bookmarks, and progress tracking. Sometimes it means a minimal RSVP mode that surfaces one word or short phrase at a time with pacing that respects punctuation, sentence flow, long words, and readability.

The result is an Android reader that sits somewhere between an ebook app, a speed-reading tool, and a focused reading environment.

## What It Does

- Imports DRM-free `.epub` and `.mobi` files from device storage
- Builds a local library with covers, progress, and resume state
- Opens books in a scrollable reader with chapter and page-aware navigation
- Launches RSVP reading from your current reading position
- Persists reading position, bookmarks, and reader preferences
- Gives fine-grained control over RSVP timing, display, rhythm, and readability
- Supports focus mode options for a quieter reading experience
- Includes language-aware tokenization foundations for Latin, CJK, and RTL text flows

## Features

### Library

- Import books directly from Android's document picker
- Browse your collection with extracted cover art and metadata
- Track completion progress and estimated time remaining
- Manage bookmarks from a dedicated library tab

### Reader

- Scrollable reading view optimized for long-form text
- Chapter navigation and table of contents access
- Page-aware progress indicators and ETA
- Inline image handling for illustrated books
- Quick handoff into RSVP mode from the current reading position

### RSVP

- Clean full-screen RSVP playback with minimal chrome
- ORP highlighting support
- Adaptive pacing based on word length, syllables, punctuation, clause boundaries, and difficulty
- Phrase chunking, blink modes, readability floors, and punctuation tuning
- Built-in reading profiles such as Balanced, Flow, Sprint, Narrative, and Study
- Live quick-tuning for tempo, typography, and layout bias

### Personalization

- Reader font size and text brightness controls
- Multiple reader themes including Light, Sepia, Dark, Nord, Cyberpunk, and Forest
- RSVP font family, font weight, brightness, and positioning controls
- Focus mode settings, including optional status bar hiding and Do Not Disturb integration
- Persistent preferences powered by DataStore

## Who It Is For

- Readers who want to move through books faster without sacrificing control
- Students and knowledge workers reading dense material on mobile
- People who like tuning interfaces to match how they think and read
- Readers who want a calmer, more stripped-back Android reading experience
- Developers interested in ebook parsing, tokenization, and RSVP engine design

## Why Kairo Feels Different

Kairo is not trying to be a generic bookstore app or a shelf full of features for their own sake. It is a reading tool built around flow.

The reader, tokenizer, persistence layer, and RSVP engine all work toward the same goal: make it easy to import a book, find your place, adjust the experience, and keep going.

## Built With

- Kotlin 2.1
- Jetpack Compose
- AndroidX Navigation
- Room
- DataStore
- Coil
- KSP
- Detekt and Ktlint
- Gradle Wrapper 8.13

## Requirements

To build and run Kairo locally you will need:

- Android Studio with Android SDK 36 installed
- JDK 17
- An Android emulator or device running Android 7.0 (API 24) or newer
- Internet access the first time Gradle dependencies are resolved

The project uses the checked-in Gradle wrapper, so you do not need to install Gradle separately.

## Build Instructions

### Android Studio

1. Clone the repository.
2. Open the project root in Android Studio.
3. Let Android Studio sync the Gradle project and install any missing SDK components.
4. Select an emulator or connected Android device running API 24+.
5. Run the `app` configuration.

### Command Line

Build a debug APK:

```bash
./gradlew assembleDebug
```

Install the debug build on a connected device or running emulator:

```bash
./gradlew installDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

Run static analysis:

```bash
./gradlew ktlintCheck detektFull
```

If you want the Android lint pass as well:

```bash
./gradlew lintDebug
```

## Project Structure

The app currently ships as a single Android module, with the code organized by responsibility:

- `app/src/main/java/com/kairo/reader/core` for domain models, tokenization, linguistics, and RSVP logic
- `app/src/main/java/com/kairo/reader/data` for parsing, persistence, repositories, and import flows
- `app/src/main/java/com/kairo/reader/ui` for library, reader, RSVP, settings, and theming
- `app/src/main/res` for Android resources, strings, and image assets

## Supported Formats

- EPUB
- MOBI

Kairo is intended for personal reading of DRM-free files. Support for locked or vendor-protected ebooks is outside the scope of the current parser pipeline.

## Current State

Kairo already includes the core reading loop:

- import a book
- browse it in your library
- read in the standard reader
- switch into RSVP playback
- save progress, bookmarks, and preferences locally

The project is still evolving, especially around polish, performance, and the more experimental RSVP tuning surfaces.

## Contributing

Contributions are welcome. If you want to improve Kairo, strong areas to contribute include:

- ebook parsing robustness across real-world EPUB and MOBI files
- reader and RSVP UX refinements
- tokenization and multilingual text handling
- performance work for large books
- tests for parsing, pacing, and reading-state persistence

Before opening a change, it is a good idea to run:

```bash
./gradlew testDebugUnitTest ktlintCheck detektFull
```

## Vision

Kairo is aimed at readers who want speed without chaos, focus without clutter, and a reading app that feels like it was built for deliberate forward motion.

If that sounds like your kind of reader, you are in the right place.
