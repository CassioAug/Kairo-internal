## What changed

<!-- Describe the user-visible behavior and the implementation boundary. -->

## Risk

<!-- Call out parser, token-index, reading-position, RSVP timing, persistence, or navigation risks. -->

## Validation

- [ ] `./gradlew qualityCheck`
- [ ] `./gradlew qualityGate` when Android/UI/build behavior changed
- [ ] Android Lint attempted for Android-facing changes; any analyzer stall is documented separately
- [ ] Focused regression tests added or updated
- [ ] `git diff --check`
- [ ] Connected instrumentation run when device behavior changed, or the missing device/emulator is documented

## Quality policy

- [ ] No baseline, `ignoreFailures`, broad exclusion, or unrelated threshold relaxation was added
- [ ] Any new suppression is declaration-scoped and explained next to the annotation
- [ ] No generated files, debug logging, commented-out code, or unrelated formatting churn is included
