# Changelog

## [Unreleased] — since 0.7.0 (2026-09-07)

143 commits; highlights grouped by area. `docs/BLUEJAY-VS-GRAYJAY.md` compares the fork against upstream Grayjay.

### Dual-screen
- **Swap screens** setting (Settings → Dual screen): moves the main video interface to the non-default display and the companion UI to the default one — for devices whose primary panel ordering differs (e.g. Anbernic RG DS vs AYN Thor). Toggling reacts immediately; a freeze-frame mask hides the relaunch flash.
- Companion UI gained a real activity host for the swapped layout (a `Presentation` cannot show on the default display by design), respects the status bar, and re-asserts itself on display add/change/remove events.
- Fixed: presentation-display races (`InvalidDisplayException`), companion stranded in front of the returning main UI, launch-loop guards.

### Player
- **Source-error recovery**: fresh-resolve recovery for expired/reset stream URLs (IO-class errors), parse-error recovery, expiry-aware resume refresh (re-resolves before the buffer drains when the source nears expiry after a long suspension), recovery telemetry counters.
- **Scrub preview**: storyboard thumbnails in a compact token-rounded frame (108 dp, secondary-colored, playhead-consistent) that no longer lifts the control bar.
- **Chapters**: the dedicated button on the bottom overlay was replaced by a clickable current-chapter title with a chevron.
- **Queue semantics**: closing the player empties the queue; videos passing the 95%-watched rule are evicted automatically (head included, with or without autoplay).
- **Watch-progress bleed fixed**: switching videos mid-resolve no longer writes the previous video's position into the new video's history (poisoned thumbnails and resume positions).
- Playhead flash on scrub release fixed; loading spinner in play/pause; dimmed surface on playback end; looping-surface fixes.

### Theming
- **Free-form color picker** (7 curated presets, saturation/value square, hue band, hex input, live swatch) for palette slots, surface styles, and thumbnail tint — no Material color picker exists in M3 1.5, so it's built in-house on Canvas + gestures.
- **Live draft preview**: the app behind the theme editor re-themes instantly while editing; draft cleared on dismiss/save.
- **Theme export/import**: share any custom theme as JSON (`bluejay-theme-*.json`), import with envelope validation and id dedup.
- **Thumbnail contrast** setting after Desaturate; thumbnails tinted with strength + blend mode; per-theme surface skins/textures, fonts, and video-card text colors follow the theme's Text slot.
- The theme editor's Custom (per-surface overrides) tab is hidden pending maturity.

### Feeds & content
- **Subs caching**: no spinner flash on revisits (in-memory pager reuse), load-more pages persisted, stale-while-revalidate file cache (no more blank spinner on old caches); 10-channel batched global feed fetch.
- **Bottom overdrag load-more** on Home, Subscriptions, and channel tabs.
- **Homebrew recommendations**: weighted interleave of platform home + unwatched subs + top creators, with spawn-probability sliders, recency window, and hidden-content probability tiers.
- Subscription date corruption repair + R8 keep rules (Room `_Impl` stripping made "All subs" empty).

### Watch states & library
- Watch-later **auto-cleanup** toggle (Settings → Content → Library): removes videos that pass the 95%-watched rule.
- Unified watch-state policy module (`WatchState`), continue-watching pool size, history-based Started/Watched filters on subs.

### Platform & infrastructure
- **Demo mode** (Settings → About): simulated content from bundled artwork, no network fetch; demo playback is a looping slideshow; demo data never touches history or caches.
- **Updates from GitHub releases**: check/download/install with confirm-before-download.
- **Dependency version catalog** (`gradle/libs.versions.toml`), Compose BOM 2026.08 / Material3 1.5.0-beta02, protobuf/ffmpeg vendoring, R8 keep rules for Javet/V8 and protobuf-lite.
- **12-locale i18n** (ar, de, es, fr, it, ja, ko, pt, ru, tr, zh) after extracting 333 strings into `core/ui`.
- **Backup hardening**: WAL checkpoint + integrity check before export/import, automatic pre-migration DB snapshots (last 2 kept), write-verify-swap for auto-backups, per-store row-count summary logged.
- Refactor waves: PlayerRepositoryImpl (1349→757), PlayerViewModel/PlayerView, SettingsScreen (2974→798), StatePlatform (1157→653), SabrSession, CompanionPages; legacy `GlobalScope`/`runBlocking` cleanup; instrumented lock/unlock regression test; jank profiling script + macrobenchmarks.
- Housekeeping: `.gitmodules` repaired, About links and plugin-docs URL corrected, 16 stale branches deleted, history squashed.
