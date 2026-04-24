# Branching and Releases

Kairo uses a simple trunk-based flow:

- `main` is stable and should always be close to releasable.
- Day-to-day work happens on short-lived branches from `main`.
- Release branches are temporary and only used when a version needs a short freeze/QA period.
- Public release points are marked with SemVer-style git tags, for example `v0.2.0`.

## Branch Names

Use branch names that describe the area and outcome:

- `feat/core-rsvp-improvements`
- `feat/library-search`
- `fix/mobi-import-crash`
- `fix/core-tokenization`
- `refactor/reader-screen`
- `chore/gradle-cleanup`

Prefer scoped but flexible names. For example, `feat/core-rsvp-improvements` is better than a very narrow name when the change may touch the RSVP engine, tokenization, model helpers, and related tests.

Avoid broad names that can live forever, such as `core-fixes`, `misc`, or `cleanup`.

## Release Branches

Most releases can be tagged directly from `main`.

Use a release branch only when `main` is stable enough to ship but needs final release work before tagging:

```bash
git checkout main
git pull
git checkout -b release/v0.2.0
```

Allowed release-branch work:

- bump Android `versionName` and `versionCode`
- update release notes
- run final QA
- fix small release-blocking bugs

Do not add new features on a release branch.

When the release is ready:

```bash
git checkout main
git merge --no-ff release/v0.2.0
git tag v0.2.0
git push origin main v0.2.0
git branch -d release/v0.2.0
```

## Versioning

Use SemVer-style versions:

- `MAJOR.MINOR.PATCH`
- `MINOR` for meaningful user-facing features or capability changes
- `PATCH` for bug fixes, parser hardening, and small polish
- `MAJOR` for incompatible changes or a clear production milestone

While Kairo is still evolving quickly, `0.x.y` versions are a good fit. Move to `1.0.0` when the core import, library, reader, RSVP playback, settings, and persistence flows are stable enough to treat as production-ready.

Android releases also need a monotonically increasing `versionCode`.

## Cleanup

After a branch is merged and no longer needed, delete it locally and remotely:

```bash
git branch -d feat/example-branch
git push origin --delete feat/example-branch
```
