# Ponytail audit — Bluejay (2026-09-05)

Repo-wide over-engineering sweep. Scope: complexity only (correctness/security/perf out).
`dep/*` submodules and plugin-asset submodules skipped by request.
Ranked biggest cut first. Tags: `delete` / `native` / `stdlib` / `yagni` / `shrink`.

## Implemented this pass

- `delete` **feature/dualscreen module** — entire module is a stub: zero callers repo-wide, every method returns a constant or an empty body (`persistState` is a no-op, `isFoldingDevice()` → false, `getSecondaryDisplaySize()` → null; comments say "will be implemented in Phase 7"). Deleted module, `di/DualScreenModule.kt`, `settings.gradle` include, app dependency, and its 6 dep declarations. → −216 tracked lines, −1 module. [feature/dualscreen, app/src/main/java/.../di/DualScreenModule.kt]
- `delete` **androidx.window:window:1.3.0** — 0 imports anywhere in app/feature/core; the build.gradle comment admits it is "future-proofing for multi-display". [app/build.gradle]
- `delete` **media3-exoplayer-rtsp + media3-exoplayer-smoothstreaming** — 0 usages (declared in both app and core/data). RTSP/SmoothStreaming are not formats this app plays. [app/build.gradle, core/data/build.gradle]
- `delete` **androidx.concurrent:concurrent-futures-ktx:1.3.0** — 0 imports. [app/build.gradle]
- `delete` **kotlin-parcelize plugin on :app** — 0 `@Parcelize` in the app module (the 4 usages live in core, which keeps its own plugin). [app/build.gradle]
- `delete` **dead asset srcDirs** `'src/tests/assets', 'src/test/assets'` in app main sourceSets — both directories do not exist. [app/build.gradle]
- `delete` **junk dirs** `runtime/runtime-android,` and `ui/ui-android}/1.12.0` — empty local dirs with typo'd names (stray `,` and `}`), untracked by git.

## Not implemented (needs a refactor, not a delete)

- `native` **Two image loaders: Glide (5 files) + Coil (21 files)**. Coil is the mainstream; Glide survives in notification-bitmap/background paths (Utility, BackgroundWorker, ImageVariable, MediaPlaybackService, StateNotifications). Coil 2 does synchronous bitmap loads fine → consolidate on Coil, drop `glide` + its `annotationProcessor`. ~−2 deps. [app/build.gradle]
- `native` **Two JSON libraries: Gson (5 files) + kotlinx-serialization (122 files)**. build.gradle comment: Gson "used for complex/anonymous cases like during development conversions (eg. V8RemoteObject)". The 5 Gson files (DeveloperEndpoints, V8RemoteObject, StateBackup, StateSubscriptions, ExchangeContract) can migrate; drop `gson`. −1 dep. [app/build.gradle]
- `yagni` **15 repository interfaces in core/data, each with exactly one implementation** (3 in core/data/impl, 11 as `Engine*RepositoryImpl` in app/di), and the test suite (23 files) mocks none of them — the "seam" buys nothing today. Standard Hilt pattern, but it's the cheapest-to-keep yagni in the repo; revisit if a second implementation ever appears. [core/data/.../repository]
- `yagni` **ReorderableList.kt** (~95-line wrapper around `sh.calvin.reorderable`) with a single caller (SettingsScreen). Borderline: the wrapper is small and used; inline it into SettingsScreen if the second use never comes. [core/designsystem/.../ReorderableList.kt]
- `shrink` **Two parallel persistence systems in app/stores/**: `stores/db/ManagedDB*` (~700 lines, Room) and `stores/v2/ManagedStore` (~560 lines, file/fragmented) — both live (StateHistory uses both). Pick one substrate; "v2" that runs next to v1 is how v3 starts. Big refactor, needs its own pass. [app/src/main/java/.../stores]
- `shrink` **CompanionPresentation.kt is 2,751 lines** (largest file in repo); StateCasting.kt 1,856; VideoDownload.kt 2,081. Split candidates, not deletions. [app/src/main/java/.../activities]

## Checked, kept (deliberate, not bloat)

- `javet` (V8 JS engine, 89 files) + `kotlin-reflect` (10 files) — the plugin/JS-adapter engine actually runs on it.
- `ffmpeg-kit.aar` (36 MB) / `sender-sdk` aar — download/export and SABR casting use them.
- `protobuf` (sabr casting + polycentric), `jsoup` (package DOM parsing), `webkit` (package WebView), `appcompat` (AppCompatDelegate night mode), `androidx.media:media` (MediaStyle notification), `material:1.13.0` (host XML theme for `material3-android`), `documentfile`, `work-runtime`, `kotlinx-serialization`, `material-icons-extended` (used across ~570 files), `reorderable` (used, see yagni note).
- `noise/` (hand-rolled Noise protocol) — out of scope (security), but it *is* hand-rolled crypto; worth an eventual review against a vetted library, just not from this audit.
- BuildConfig flavor flags (IS_UNSTABLE_BUILD/IS_PLAYSTORE_BUILD) — all read.
- Hand-rolled event bus (`constructs/Event*`, 35 consumers) and `helpers/`, `builders/` (DASH manifest generation) — actively used, not stdlib-replaceable.

## Net

- Implemented: **−216 tracked lines, −1 module, −5 dependency declarations** (window, rtsp, smoothstreaming, concurrent-futures, parcelize-plugin), 0 code references touched.
- Possible if refactors land: **−2 more deps** (glide, gson), −1 module-level abstraction pair (stores v1/v2).

Verification: `./gradlew :app:compileUnstableDebugKotlin` (see compile log / commit).
