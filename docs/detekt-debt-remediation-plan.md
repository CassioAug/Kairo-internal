# Detekt debt remediation plan

Date: 2026-07-18

Remediation branch: `refactor/detekt-debt-remediation`

Remediation base: `6f29f7b`

Detekt version: `1.23.8`

Command: `./gradlew detektFull`

## Completion status

Completed on 2026-07-18.

- Detekt reduced from the 1,084-finding starting baseline to **zero findings**.
- `detektFull` passes with `ignoreFailures = false` and no Detekt baseline configured.
- The temporary baseline was deleted after the final long-tail cleanup.
- Pull requests and `main` now run `detektFull` in `.github/workflows/detekt.yml` and retain the HTML, XML, SARIF, and text reports.
- Parser constants and scan stages, RSVP timing and tokenisation, Reader/RSVP Compose contracts, navigation coordinators, and preference mapping were remediated in domain-focused slices.
- `ktlintCheck`, `compileDebugKotlin`, all 437 `testDebugUnitTest` tests, and unbaselined `detektFull` pass together.
- The app and instrumentation APKs compile; connected execution still requires an attached emulator or device.

The remaining sections preserve the starting inventory and the remediation rationale for future maintenance.

## Starting executive summary

The first successful repository-wide Detekt run reported 1,116 weighted findings. After removing 31 stale EPUB constants and one unused helper exposed by the large-file refactor, the remediation starting baseline was **1,084 findings**.

This is not a list of 1,084 equally serious bugs:

- 516 findings (47.6%) are `MagicNumber`.
- 150 findings (13.8%) are `ReturnCount`.
- 67 findings are PascalCase Compose functions reported by the standard Kotlin `FunctionNaming` rule.
- Only nine findings are in unit-test sources; 1,075 are in production sources.
- The six most common rules account for 912 findings (84.1%).
- `data/books` alone accounts for 361 findings (33.3%), mostly binary-format constants and parser control flow.

The right approach is to establish a CI ratchet, classify intentional patterns, and then remove the baseline in focused behavior-preserving slices. A repository-wide mechanical cleanup would be high risk and would obscure parser, timing, and UI regressions.

## Starting baseline

### Findings by rule

| Rule | Count | Share | Recommended treatment |
| --- | ---: | ---: | --- |
| `MagicNumber` | 516 | 47.6% | Name behavioral/protocol constants; narrowly suppress documented lookup/range tables |
| `ReturnCount` | 150 | 13.8% | Keep clear guard clauses; refactor genuinely branching transformations |
| `LongMethod` | 74 | 6.8% | Extract cohesive rendering, parsing, or state transitions |
| `FunctionNaming` | 67 | 6.2% | Treat PascalCase `@Composable` names as intentional Compose convention |
| `CyclomaticComplexMethod` | 53 | 4.9% | Split decision stages and encode states/results explicitly |
| `LongParameterList` | 52 | 4.8% | Introduce cohesive immutable state/action/input objects at real boundaries |
| `TooManyFunctions` | 40 | 3.7% | Split by responsibility only when functions change for different reasons |
| `MaxLineLength` | 37 | 3.4% | Reformat code; preserve long user-facing sample prose where readability wins |
| `LoopWithTooManyJumpStatements` | 32 | 3.0% | Extract predicates/stages or use typed scan results |
| `ComplexCondition` | 21 | 1.9% | Name predicates and domain decisions |
| `MatchingDeclarationName` | 18 | 1.7% | Rename single-declaration files or intentionally group related models |
| `NestedBlockDepth` | 15 | 1.4% | Guard clauses, extracted stages, and typed intermediate results |
| Other rules | 9 | 0.8% | Resolve individually; these are small enough not to baseline indefinitely |

### Findings by area

| Area | Count | Primary debt |
| --- | ---: | --- |
| `data/books` | 361 | MOBI/EPUB constants, parsing branches, scan loops |
| `core/rsvp` | 178 | Timing constants, return-heavy policies, pacing complexity |
| `core/tokenization` | 105 | Unicode range data, segmentation branches, language-specific rules |
| `ui/reader` | 97 | Large composables, derived state, parameter surfaces |
| `ui/settings` | 86 | UI ranges, long settings sections, Compose naming |
| `ui/rsvp` | 84 | Playback/context rendering and parameter groups |
| `ui/library` | 44 | Card/overlay composition and coordinator complexity |
| `ui/navigation` | 34 | Route coordinators and callback construction |
| `data/preferences` | 17 | Explicit schema mapping and update methods |
| Remaining areas | 78 | Smaller isolated findings |

### Highest-count files

| File | Findings | Dominant rules |
| --- | ---: | --- |
| `data/books/mobi/MobiBinary.kt` | 61 | 55 magic numbers; binary offsets and flags |
| `data/books/mobi/MobiImageProcessor.kt` | 60 | 45 magic numbers plus parser flow |
| `data/books/mobi/MobiHeaderParser.kt` | 47 | 36 magic numbers plus parser flow |
| `core/tokenization/cjk/CjkCharClassifier.kt` | 42 | 40 Unicode/range literals |
| `core/rsvp/analysis/RsvpWordPacing.kt` | 42 | timing constants, return count, complexity |
| `data/books/mobi/MobiContentProcessor.kt` | 41 | parser flow, return count, large-class signal |
| `core/rsvp/timing/RsvpPunctuationTimingPolicy.kt` | 35 | 27 timing constants plus branching |
| `ui/settings/RsvpAdvancedSettingsContent.kt` | 34 | 33 user-facing constraint literals |
| `core/rsvp/timing/RsvpTiming.kt` | 29 | timing constants and branch structure |
| `data/books/EpubContentRewriter.kt` | 21 | path/markup branches and line length |

## Principles for resolving the debt

1. **Ratchet before cleanup.** Existing findings must not allow new findings to enter unnoticed.
2. **Fix behavior, not the counter.** Do not create meaningless constants such as `NUMBER_FOUR` or flatten readable guard clauses merely to satisfy a rule.
3. **Keep changes domain-scoped.** MOBI parsing, RSVP timing, tokenization, and Compose decomposition need separate reviews and test suites.
4. **Prefer typed intermediate results.** Parser and timing complexity is easier to reduce with explicit scan/result/state models than with nested Boolean expressions.
5. **Use suppressions only for inherent representations.** Unicode tables, binary masks, and Compose naming can justify narrow, documented suppression. Business logic cannot.
6. **Delete baseline entries as part of every cleanup PR.** The baseline should move in one direction only.

## Phase 0: establish a reliable Detekt gate

### 0.1 Classify configuration noise

Review the rules against Kairo conventions before generating a baseline:

- `FunctionNaming`: PascalCase `@Composable` functions are idiomatic. Prefer declaration-level `@Suppress("FunctionNaming")` or a tightly scoped UI-path policy. Do not relax naming for all Kotlin functions.
- `MagicNumber`: keep the rule active. Use narrow suppression only for static Unicode ranges, lookup tables, masks, and offsets where replacing every literal with a name would reduce clarity.
- `ReturnCount`: retain clear early guard clauses. Refactor only functions where multiple returns reflect tangled decision logic.
- `MatchingDeclarationName`: rename files containing one primary class; allow intentional related model/extension groupings only with a documented suppression.
- `LongParameterList`: Compose functions should not receive a blanket exemption. Use state/action objects where parameters form a cohesive screen contract.

### 0.2 Create and wire a baseline

Add a tracked baseline after configuration triage:

```kotlin
detekt {
    baseline = file("$rootDir/config/detekt/baseline.xml")
}
```

Then generate it:

```bash
./gradlew :app:detektBaseline
./gradlew detektFull
```

The second command must pass with the baseline applied. Keep `ignoreFailures = false`; otherwise the ratchet is advisory rather than protective.

### 0.3 Add CI enforcement

Run `detektFull` on every pull request and upload `app/build/reports/detekt/detekt.sarif` or the HTML report as an artifact. CI should fail when a new finding is not represented by the baseline.

## Phase 1: correctness and cheap cleanup

Resolve small high-signal rules before large structural changes:

- `UnusedPrivateMember` (1)
- `UnusedParameter` (2)
- `TooGenericExceptionCaught` (2)
- `UseCheckOrError` (1)
- `FunctionOnlyReturningConstant` (1)
- straightforward `MaxLineLength` and `MatchingDeclarationName` findings

For each change:

```bash
./gradlew compileDebugKotlin testDebugUnitTest
./gradlew detektFull
```

Do not mix formatting-only changes with behavior changes.

## Phase 2: book parser workstream

This is the largest area and should be split into protocol constants first, control flow second.

### 2.1 MOBI binary constants

Target:

- `MobiBinary.kt`
- `MobiHeaderParser.kt`
- `MobiImageProcessor.kt`

Actions:

- Name byte offsets, record header sizes, flag masks, compression identifiers, encoding identifiers, maximum counts, and sentinel values.
- Group constants by MOBI/PalmDB structure rather than placing them in one generic constants object.
- Add short comments citing the represented header/record field.
- Keep raw literals inside documented static tables only when the table itself is the clearest representation.
- Add boundary tests for truncated records, invalid offsets, overflow, record-count limits, and unsupported encodings.

### 2.2 MOBI parser flow

Target:

- `MobiContentProcessor.kt`
- `MobiImageProcessor.kt`
- `MobiFallbackParser.kt`
- `MobiBookParser.kt`

Actions:

- Replace nested parse decisions with typed stages such as header validation, record selection, decoding, and asset resolution.
- Return typed success/fallback/rejection results instead of using several nullable values and early exits across one function.
- Extract scan predicates to reduce multi-jump loops without allocating unnecessary intermediate collections.
- Preserve file-size budgets and DRM/unsupported-format behavior.

### 2.3 EPUB tail

Target:

- `EpubContentRewriter.kt`
- `epub/EpubTextDecoder.kt`
- `epub/EpubOpfParser.kt`
- `epub/EpubPathResolver.kt`
- `EpubNavigationClassifier.kt`

Actions:

- Name encoding markers, scan limits, and title/navigation thresholds.
- Keep guard-clause returns where they make malformed-input handling obvious.
- Extract repeated path/source classification predicates.
- Add tests before changing fallback order, lenient XML behavior, or entity decoding.

Validation for all parser slices:

```bash
./gradlew testDebugUnitTest --tests 'com.kairo.reader.data.books.*'
./gradlew compileDebugKotlin
./gradlew detektFull
```

## Phase 3: RSVP timing and tokenization

### 3.1 RSVP timing constants and policy stages

Target:

- `RsvpWordPacing.kt`
- `RsvpPunctuationTimingPolicy.kt`
- `RsvpTiming.kt`
- `RsvpTextRules.kt`

Actions:

- Move user-tunable ranges to typed domain constraints shared by settings, persistence, profile JSON, and engine validation.
- Name fixed algorithm constants by effect: smoothing bounds, pause caps, word-length thresholds, and multiplier limits.
- Split timing into base duration, lexical adjustment, structural pause, contextual adjustment, and final clamp stages.
- Keep defensive engine clamps even when persisted/profile inputs are normalized.
- Preserve exact timing tests; do not loosen millisecond assertions merely to satisfy complexity rules.

### 3.2 Tokenization and Unicode data

Target:

- `core/tokenization/cjk/CjkCharClassifier.kt`
- CJK/RTL segmenters and link appliers
- `Tokenizer.kt`

Actions:

- Represent Unicode blocks as named ranges or documented tables.
- Apply a narrow suppression to data-only range tables if naming every endpoint would be less readable.
- Extract script classification, boundary detection, and segment joining into separate pure functions.
- Add multilingual fixtures covering boundary code points before altering ranges.

Validation:

```bash
./gradlew testDebugUnitTest --tests 'com.kairo.reader.core.rsvp.*'
./gradlew testDebugUnitTest --tests 'com.kairo.reader.core.tokenization.*'
./gradlew detektFull
```

## Phase 4: Compose and navigation workstream

### 4.1 Reader and RSVP screens

For `LongParameterList`, introduce small contracts only where values travel together:

- immutable rendering state
- user-event callbacks/actions
- layout metrics
- navigation/progress models

Do not replace explicit callbacks with an untyped catch-all event object.

For `LongMethod` and complexity findings:

- extract existing visual sections into named composables;
- move derived calculations into pure state builders;
- keep effects and lifecycle coordination at the screen boundary;
- keep UI ordering in one small orchestrator.

Priority targets are `ReaderScreenContent`, Reader derived/progress state, `RsvpScreen`, context resolution, and navigation route coordinators.

### 4.2 Settings constraints

`RsvpAdvancedSettingsContent` has 33 magic-number findings because slider bounds and normalization limits live in rendering code. Create typed constraints beside the RSVP domain model, then make:

- UI sliders consume those ranges;
- DataStore reads normalize through them;
- profile JSON decode normalize through them;
- the engine retain defensive clamps;
- round-trip tests prove every path produces equivalent normalized values.

### 4.3 Library and overlays

Split long visual bodies only when there is a stable component boundary. `ImportProgressOverlay` and large cards can be decomposed into state display, progress details, and actions without introducing new navigation state.

Validation:

```bash
./gradlew compileDebugKotlin testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew detektFull
```

Use targeted manual/screenshot checks for Reader, RSVP, settings, Library, compact landscape, and tutorial overlays.

## Recommended pull-request sequence

1. Detekt policy, baseline, and CI ratchet.
2. High-signal one-off findings and filename/line cleanup.
3. MOBI/PalmDB named protocol constants.
4. MOBI parser flow and typed results.
5. EPUB remaining parser findings.
6. RSVP domain constraints and persistence/profile normalization.
7. RSVP timing/pacing stage extraction.
8. Unicode/tokenization tables and segmentation flow.
9. Reader/RSVP Compose state and action contracts.
10. Settings, Library, navigation, and remaining long composables.
11. Long-tail baseline deletion and final configuration review.

Each PR should remove only its own baseline entries and include the relevant focused tests. Avoid a single repository-wide “Detekt cleanup” commit.

## Tracking progress

Record these numbers in each cleanup PR:

- total findings before and after;
- findings removed by rule;
- baseline entry count before and after;
- tests executed;
- any intentional suppression added, including its reason.

Useful local commands:

```bash
# Full analysis and reports
./gradlew detektFull

# Count current findings
rg -c '<error ' app/build/reports/detekt/detekt.xml

# Count findings by rule
rg -o 'source="detekt\.[^"]+' app/build/reports/detekt/detekt.xml \
  | sed 's/source="detekt\.//' \
  | sort \
  | uniq -c \
  | sort -nr

# Compile and run the JVM regression suite
./gradlew compileDebugKotlin testDebugUnitTest
```

## Definition of done

The debt campaign is complete when:

- `./gradlew detektFull` passes without a baseline;
- no broad repository/file suppressions hide unrelated future findings;
- intentional protocol/Unicode/Compose exceptions are narrow and documented;
- RSVP constraints have one typed owner across UI, persistence, JSON, and engine boundaries;
- parser budgets, fallback order, and malformed-input behavior remain covered;
- Reader/RSVP/Library navigation and playback behavior remain unchanged;
- `compileDebugKotlin`, `testDebugUnitTest`, and relevant Android tests pass;
- Detekt remains a required CI check so the debt cannot regrow.

## References

- [Detekt 1.23.8 Gradle plugin tasks and baseline behavior](https://detekt.dev/docs/1.23.8/gettingstarted/gradle/)
- [Detekt 1.23.8 configuration, validation, filters, and severity](https://detekt.dev/docs/1.23.8/introduction/configurations/)
- [Detekt 1.23.8 overview](https://detekt.dev/docs/1.23.8/intro/)
