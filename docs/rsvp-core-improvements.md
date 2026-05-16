# RSVP Core Improvements

This tracks the current RSVP core performance and refactor pass.

## Active Pass

- [x] Reduce full-chapter work when generating frames from a later `startIndex`.
- [x] Narrow RSVP frame-cache keys so visual-only settings do not invalidate timing frames.
- [x] Consolidate repeated expanded-token analysis passes into one analysis result.

## Backlog

- [ ] Move session/resume timing policy out of mixed engine/UI ownership.
- [ ] Align RSVP helper package declarations with the new folder boundaries.
- [ ] Memoize estimated WPM by timing config and language.
- [ ] Add frame index maps for faster resume/token alignment.
- [ ] Remove broad engine suppressions once complexity is lower.
