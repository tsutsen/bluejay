# AGENTS.md

## LSP diagnostics: Gradle is the source of truth

pi-lens runs a kotlin-language-server (1.3.13, Kotlin 2.1.0) that keeps a long-lived
in-memory project model. When you **add new files or new Gradle dependencies**, the
running LSP instance does NOT re-import the project, so it reports phantom
`kotlin:UNRESOLVED_REFERENCE` / `MISSING_DEPENDENCY_CLASS` / `NAMED_PARAMETER_NOT_FOUND`
"blocking" errors for exactly the new symbols — and pi-lens **caches those results**
per file with `hasFinalDiagnosticsSnapshot: true`, replaying them on every subsequent
`lens_diagnostics` call (mode=delta/all are cache-only).

Telltale signs the LSP is stale (not the code):

- every unresolved reference is a symbol/file/dependency you added this session
- suggested fixes import classes that don't exist (e.g.
  `androidx.compose.material3.TonalPalette`)
- `./gradlew :module:compileDebugKotlin` passes with zero errors

If Gradle compiles green, the LSP 🔴s are false positives — **do not "fix" code to
satisfy them** (e.g. never take the bogus quick-fix imports).

### Fix procedure (when LSP 🔴s contradict a green Gradle compile)

1. `./gradlew :<module>:bundleLibCompileToJarDebug` for each module whose public API
   changed — consuming modules (and their LSPs) resolve sibling modules against this
   artifact, not the classes dir.
2. `pkill -f 'org.javacs.kt.MainKt'` — kills the stale LSP instance(s); pi-lens
   spawns a fresh one on demand. The import of this project takes a few minutes.
3. Wait for the import to settle (LSP process CPU drops), then run
   `lens_diagnostics mode=full` with `paths:` for the affected files — this is the
   only mode that does an **active** LSP scan. Fresh results replace the cache for
   files that have findings.

### Known gap: zero-finding files keep their stale cache

The active sweep only overwrites the cache for files it reports findings for. A file
the fresh LSP considers **clean** keeps its old "final" snapshot, so the stale 🔴s
persist in mode=all. Workaround: edit the session state file and clear the entry:

```
~/.pi-lens/projects/home-leon-Projects-bluejay/sessions/<session-uuid>.json
```

In `widget.files[]`, find the file entry (`filePath` ends with the file path):
delete entries in `diagnostics`/`allDiagnostics` whose `tool == "lsp"` and whose
`rule` is one of `kotlin:UNRESOLVED_REFERENCE`, `kotlin:MISSING_DEPENDENCY_CLASS`,
`kotlin:NAMED_PARAMETER_NOT_FOUND`, `kotlin:NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER`,
`kotlin:NO_ELSE_IN_WHEN`, `kotlin:UNUSED_PARAMETER`, set
`hasFinalDiagnosticsSnapshot: false`, and recompute `diagnosticCounts` from what
remains. Re-run `lens_diagnostics mode=all` to confirm the entry is gone.

Caveat: the pi-lens process keeps this state in memory and re-saves it on a
debounce, so the edit can be clobbered (observed: flag flipped back to `true`
with the original `observedAt`). Reliable escapes are (a) a new session (fresh
LSP import + empty cache) or (b) keeping the LSP off (see
`~/.pi-lens` config) — Gradle compiles are the gate either way.

### Environmental noise to ignore

- `kotlinx.serialization ... compiled with an incompatible version of Kotlin
  (2.3.0 vs 2.1.0)` — the LSP runs Kotlin 2.1.0, the project uses 2.2.21; Gradle
  resolves the right one. Never an actionable code issue.
- ast-grep style warnings (`kotlin-no-var` in `@Serializable` settings classes,
  `kotlin-no-lateinit` on Android app fields) — pre-existing by design in this codebase.

## Performance profiling (jank / frametimes / resources)

On-device, no build needed — drive a scenario and read `gfxinfo`:

    adb connect <phone>            # e.g. 192.168.1.159:<wireless-adb-port>
    ./scripts/profile_jank.sh morph   # or: feed | player

Reports frames rendered, janky % (>16.7 ms), p50/p90/p95/p99 frame ms.
Baseline on a Nothing phone (API 34, arm64) after the morph-dock fix:
**morph = 477 frames, 0.63% janky, p95 15 ms**. Re-run after UI changes and compare.

Macrobenchmarks (CI / API<=32 devices): `BluejayBenchmarks` in
`app/src/androidTest/.../benchmark/` — startup cold, feed scroll, morph gesture,
`FrameTimingMetric`. Run: `./gradlew :app:connectedUnstableDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=com.tsutsen.platformplayer.benchmark.BluejayBenchmarks`
(known issue: GrantPermissionRule fails on API 33+ — use the gfxinfo script there).

Resource watch: `adb -s <dev> shell dumpsys meminfo com.tsutsen.platformplayer.d` (PSS).

## Build machine / signing

- Build on the remote machine `ssh 100.107.89.14` (62 GB RAM; local laptop OOMs on
  big Gradle builds). Repo at `~/projects/bluejay` on branch `dev`; the remote shell
  is **fish** — write bash to a file, `scp` it, run `bash file.sh`. Warm builds ~1.5 min.
- Release signing key: `keystore.properties` + `release-test.jks` in repo root
  (both gitignored; `/opt/key.properties` takes precedence if present).
  Backup copies: `~/keystore-backup/` locally and `~/keystore-backup-bluejay-release.jks`
  on the remote. Never commit them.
- Plugin source submodules: `futo-org/grayjay-plugin-*` on GitHub where the pinned
  (signed) commit exists, else `gitlab.futo.org/videostreaming/plugins/*`.
