# Ponytail audit — Bluejay (2026-09-05)

Repo-wide over-engineering sweep. Scope: complexity only (correctness/security/perf out).
`dep/*` submodules and plugin-asset submodules skipped by request.
Ranked biggest cut first. Tags: `delete` / `native` / `stdlib` / `yagni` / `shrink`.

## Implemented — batch 1 (zero-reference cuts)

- `delete` **feature/dualscreen module** — entire module is a stub: zero callers repo-wide, every method returns a constant or an empty body (`persistState` is a no-op, `isFoldingDevice()` → false, `getSecondaryDisplaySize()` → null; comments say "will be implemented in Phase 7"). Deleted module, `di/DualScreenModule.kt`, `settings.gradle` include, app dependency, and its 6 dep declarations. → −216 tracked lines, −1 module. [feature/dualscreen, app/src/main/java/.../di/DualScreenModule.kt]
- `delete` **androidx.window:window:1.3.0** — 0 imports anywhere in app/feature/core; the build.gradle comment admits it is "future-proofing for multi-display". [app/build.gradle]
- `delete` **media3-exoplayer-rtsp + media3-exoplayer-smoothstreaming** — 0 usages (declared in both app and core/data). RTSP/SmoothStreaming are not formats this app plays. [app/build.gradle, core/data/build.gradle]
- `delete` **androidx.concurrent:concurrent-futures-ktx:1.3.0** — 0 imports. [app/build.gradle]
- `delete` **kotlin-parcelize plugin on :app** — 0 `@Parcelize` in the app module (the 4 usages live in core, which keeps its own plugin). [app/build.gradle]
- `delete` **dead asset srcDirs** `'src/tests/assets', 'src/test/assets'` in app main sourceSets — both directories do not exist. [app/build.gradle]
- `delete` **junk dirs** `runtime/runtime-android,` and `ui/ui-android}/1.12.0` — empty local dirs with typo'd names (stray `,` and `}`), untracked by git.

## Implemented — batch 2 (refactors)

- `native` **Glide → Coil** — migrated the 5 remaining Glide files (MediaPlaybackService + StateNotifications notification bitmaps now use `ImageLoader.execute`; `ImageVariable.setImageView` had 0 callers and was deleted; BackgroundWorker/Utility had dead Glide imports + a dead `withMaxSizePx`). Dropped `glide:4.16.0` + its `annotationProcessor`. −2 dep declarations. Coil 2.7.0 was already the mainstream (21 files). [app/build.gradle, app/src/main/java/.../{services,states,models,background,Utility.kt}]
- `native` **Gson → kotlinx-serialization (partial)** — dead Gson paths removed: `StateBackup.importNewPipeSubs` (both overloads, 0 callers repo-wide; the toast even said "Compose screen not yet wired") and unused imports in StateSubscriptions/ExchangeContract. **Dependency kept**: the remaining 2 users (V8RemoteObject, DeveloperEndpoints) reflectively serialize arbitrary V8/dev-object types, which kotlinx-serialization cannot do by design. −37 lines; the "−1 dep" is not achievable without a reflective JSON shim. [app/src/main/java/.../states/StateBackup.kt, .../states/StateSubscriptions.kt, .../subsexchange/ExchangeContract.kt]
- `shrink` **CompanionPresentation.kt split 2,751 → 4 files** — mechanical region split, same package, zero logic changes: `CompanionPresentation.kt` (class, 235), `CompanionContent.kt` (root composable, 489), `CompanionPages.kt` (pages, 1,351), `CompanionControls.kt` (controls, 805). Moved `private` top-level composables to `internal` (verified no name collisions in package); imports pruned per file. [app/src/main/java/.../activities/]
- `shrink` **History consolidated to one substrate (follow-up pass)** — the "3 history paths" were: (1) legacy file store (`FragmentedStorage` + `ReconstructStore`) — read-only since the one-time file→DB migration shipped in 2023, dead; (2) app-module Room table (`ManagedDBStore` + `DBHistory`) — no longer written by playback (the Compose player's `HistoryTracker` replaced it), only fed by device-sync/backup/remote-plugin sync, and never read by any UI; (3) `core/database` Room `history` table — written by playback (HistoryTracker), read by everything (library, resume position, WatchStats, companion, notifications). Migration: `StateHistory` rewritten as a thin facade over `HistoryDao` (new `getSince` query; `importHistoryVideos` for sync-receive + backup-import with the private-mode guard; `toWireHistory`/`toReconString` keep the `List<HistoryVideo>` sync wire format and the `url|||date|||position|||name` backup format intact for cross-device/old-backup compatibility). Deleted: the file store + its migration, the whole `DBHistory` Room type, and 12 dead `StateHistory` methods (pager/search/position/watched/mark/remove — zero callers). DB construction deduped into `core/database/AppDatabaseProvider` (Hilt module now delegates). The orphaned app-module "history" Room file on devices is left in place (harmless; could be deleted in a future cleanup pass). [app/src/main/java/.../states/StateHistory.kt, core/database/.../AppDatabaseProvider.kt, .../dao/HistoryDao.kt, states/State{App,Backup,Sync}.kt, models/HistoryVideo.kt]

## Checked, kept (reclassified after deeper inspection)

- `yagni` **15 repository interfaces in core/data, each with exactly one implementation** — **kept: it's the dependency-inversion seam, not yagni.** The 11 `Engine*RepositoryImpl` implementations live in :app (bound via `@Binds`), while feature modules (`feature/*/impl`, which cannot depend on :app) inject the interfaces — 30+ injection sites (SettingsRepository ×8, LibraryRepository ×5, ...). Deleting the interfaces forces the implementations (and the app state classes they wrap) down into core/data, i.e. moving half the app module. Single-impl-per-interface is exactly what a clean seam looks like when the impl sits in the higher-dependency module. [core/data/.../repository, app/src/main/java/.../di/]
- `shrink` **Two persistence systems in app/stores/** — **kept: complementary, and mid-migration, not duplicate.** `FragmentedStorage` (file) is the main substrate (25+ consumers); `stores/v2/ManagedStore` sits on top of it; `stores/db/ManagedDB*` (Room) handles queryable history. Real wrinkle found: **history data has 3 paths** — `ManagedDBStore<HistoryVideo>`, `ReconstructStore<HistoryVideo>` (file), and the new `core/database` Room `HistoryDao` consumed by `LibraryRepositoryImpl`. Collapsing that is a product-level migration (the build.gradle "Compose migration phase 0+" comment points the same way), not an audit cut. Needs its own planned pass. [app/src/main/java/.../stores, core/database]
- `yagni` **ReorderableList.kt** (~95-line wrapper around `sh.calvin.reorderable`, single caller) — **skipped per request** (item #10).

## Checked, kept (deliberate, not bloat)

- `javet` (V8 JS engine, 89 files) + `kotlin-reflect` (10 files) — the plugin/JS-adapter engine actually runs on it.
- `ffmpeg-kit.aar` (36 MB) / `sender-sdk` aar — download/export and SABR casting use them.
- `protobuf` (sabr casting + polycentric), `jsoup` (package DOM parsing), `webkit` (package WebView), `appcompat` (AppCompatDelegate night mode), `androidx.media:media` (MediaStyle notification), `material:1.13.0` (host XML theme for `material3-android`), `documentfile`, `work-runtime`, `kotlinx-serialization`, `material-icons-extended` (used across ~570 files), `reorderable` (used, see yagni note).
- `noise/` (hand-rolled Noise protocol) — out of scope (security), but it *is* hand-rolled crypto; worth an eventual review against a vetted library, just not from this audit.
- BuildConfig flavor flags (IS_UNSTABLE_BUILD/IS_PLAYSTORE_BUILD) — all read.
- Hand-rolled event bus (`constructs/Event*`, 35 consumers) and `helpers/`, `builders/` (DASH manifest generation) — actively used, not stdlib-replaceable.

## Net

- Implemented (batches 1+2): **−2 module (dualscreen), −7 dependency declarations** (window, rtsp, smoothstreaming, concurrent-futures, parcelize-plugin, glide ×2), **−253 tracked lines net** (216 dualscreen + 37 Gson dead paths + split refactor), CompanionPresentation 2,751 → max 1,351 lines.
- Remaining audit surface: the eventual reorderable-wrapper inline if a second use never comes. (The 3-path history collapse landed as the follow-up pass above.)

Verification: `./gradlew :app:compileUnstableDebugKotlin` green after every batch (commits on `feat/platform-sample-updates`).
