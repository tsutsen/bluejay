# Bluejay vs FUTO Grayjay — what the fork changed

Bluejay is a fork of [FUTO Grayjay](https://github.com/futo-project/grayjay). The plugin/core engine and the platform sources are inherited almost intact; the UI and application architecture are a rewrite; several Grayjay subsystems were removed. Evidence lives in git history (the "NUKE" commit `70d245d93`, "Remove unused inherited subsystems" `2b9fe9235`, the rename commit `1915e7cdd`).

## Kept (inherited nearly intact)

- **Plugin engine** — Javet/V8 JS runtime (`engine/V8Plugin.kt`, `api/media/platforms/js/`), plugin signing (`SignatureProvider`), encrypted configs, captcha flow, dev clients.
- **Platform sources** — all bundled sources remain git submodules from FUTO's repos: YouTube, BiliBili, SoundCloud, Twitch, Odysee, Rumble, Kick, Nebula, Patreon, PeerTube, BitChute, Dailymotion, Crunchyroll, Mixcloud, CuriosityStream, TED, Red Bull TV, FOSDEM, NASA+, Internet Archive, Apple Podcasts, RadioBrowser.
- **Legacy state layer** — `StatePlatform/Subscriptions/Downloads/History/Library/Playlists/Player/Plugins/Backup/App/...` singletons (partially slimmed by refactors, still the app's engine backbone).
- **Downloads engine** — DASH/HLS/UMP/progressive segment downloaders, `VideoDownload`, `VideoLocal`, exports.
- **FragmentedStorage**, local HTTP server + live chat manager, SABR (YouTube) session, subscription groups, noise protocol, encryption, plugin-script cache directories.
- **Comments as a plugin capability** (`CommentRepository`) — only the built-in backend was removed (see Removed).
- **License**: FUTO Source First License 1.1 kept verbatim; `CONTRIBUTION.md` (plugin docs) nearly as-is.

## Changed (modernized)

- **UI: complete Compose rewrite.** The "NUKE" commit deleted 808 files / ~64,500 lines of XML layouts, Fragments, Views, and dialogs. All screens are Compose (`compose/` + `feature/*`).
- **Modularization**: Grayjay is a single module; Bluejay is `app` (legacy engine) + 7 `core/*` modules (model, data, database, datastore, designsystem, navigation, ui) + 7 `feature/*` modules, with a repository layer over Room (local playlists, history) instead of legacy stores.
- **Toolchain**: modern Compose BOM / Material3 1.5 expressive, version catalog (`libs.versions.toml`), minSdk 28 / target 36, R8 shrinking (with keep rules for Javet/protobuf-lite), vendored ffmpeg-kit.
- **Architecture discipline**: `docs/`, `GLOSSARY.md`, `CONVENTIONS.md`, `AGENTS.md`, conventional commits, profiling (gfxinfo script + macrobenchmarks), large god-class splits, coroutine hygiene.

## Added (not in Grayjay)

- **Theming engine**: surface skins/textures, font choice/scale, thumbnail tint with strength/blend modes, free-form color picker, thumbnail contrast, theme editor, theme export/import, live draft preview behind the editor.
- **Dual-screen companion + Swap screens** (AYN Thor / RG DS class devices): companion UI (controls/stats/comments/queue/library) on the second display, per-display brightness, screen-swap setting with freeze-frame transition.
- **Homebrew recommendations feed**: weighted interleave of platform home + unwatched subscriptions + top creators, spawn-probability sliders, recency window, hidden-content probability tiers.
- **Source-error recovery**: fresh-resolve on IO/parse errors, expiry-aware resume refresh, recovery telemetry; queue eviction on close/watched; watch-progress bleed fix.
- **Caching**: homebrew feed cache, subs feed file cache with stale-while-revalidate, persistent load-more pages, batched global feed fetch.
- **Updates from GitHub releases** (check/download/install), demo mode, watch stats screen, watch-later auto-cleanup, backup hardening (WAL checkpoint, pre-migration snapshots, write-verify-swap), storyboard scrub previews, 12-locale i18n.

## Removed (from the fork)

- **Polycentric** social/identity layer (comments backend, moderation, submodules `dep/polycentricandroid`, `dep/futopay`).
- **Chromecast casting** (sender SDK AAR, `StateCasting`, casting UI) — companion-screen replaced the use case.
- **Device sync** (`sync/` package, `StateSync`, sync tests).
- **FUTO Pay / payments** (`StatePayment`).
- **The entire legacy XML UI layer** (~64,500 lines: Fragments, Views, dialogs, layouts).
- Legacy `ffmpeg-kit.aar` (replaced by vendored 9.0.2), old plugin submodule URLs (now FUTO GitHub-first with GitLab fallback).

## Identity

- Package `com.futo.platformplayer` → `com.tsutsen.platformplayer`; app name Bluejay in all flavors (stable / unstable / playstore — the playstore flavor is new).
- Update, log, telemetry, announcement, and subs-exchange endpoints moved to `*.bluejay.app` / `github.com/tsutsen/bluejay`; bundled plugin submodules still point at FUTO repos.
- Keystore handling moved to repo-root `keystore.properties` (never committed).
- Known quirks: `settings.gradle` rootProject.name is still `"FutoVideo"`; the `app` module is still the monolithic legacy engine alongside the new modules; `docs/integrations/polycentric.md` is a stale leftover doc.
