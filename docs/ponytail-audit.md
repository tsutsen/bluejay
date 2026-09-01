# ponytail-audit — bluejay

One-shot over-engineering audit, 2026-07 (ponytail-audit skill). Scope: first-party code
(`app/src`, `core/`, `feature/`, `ui/`) + build files. Git submodules (`dep/`, `assets/sources/*`)
excluded from the dead-code reference sweeps; a couple of findings carry a "confirm submodule"
caveat. Ranked biggest cut first. Nothing was applied.

## delete: dead code

1. **`views/` legacy View-system package — 5 files, zero references** (app is Compose now).
   Replace: nothing.
   [`app/src/main/java/com/tsutsen/platformplayer/views/`] — 1,003 lines incl. 948-line
   `video/datasources/JSHttpDataSource.java`, `FeedStyle.kt`, `fields/{AdvancedField,ButtonField,DropdownFieldOptionsId}.kt`
2. **`MdnsTests.kt`** — entire test body commented out; tests a `com.tsutsen.platformplayer.mdns`
   package that no longer exists. Replace: nothing.
   [`app/src/test/java/com/tsutsen/platformplayer/MdnsTests.kt`] — 397 lines
3. **`CachedSubscriptionAlgorithm` + `SimpleSubscriptionAlgorithm` + `CACHE`/`SIMPLE` enum
   entries** — `SubscriptionFetchAlgorithm.getAlgorithm` is only ever called with the hardcoded
   `SubscriptionFetchAlgorithms.SMART` (`private val` in `StateSubscriptions`, no settings path to
   change it). Replace: nothing.
   [`app/src/main/.../subscription/CachedSubscriptionAlgorithm.kt`, `SimpleSubscriptionAlgorithm.kt`,
   `SubscriptionFetchAlgorithms.kt` (enum entries)] — 209 lines
4. **Dead composables inside live files** — `SettingsTextCard`, `SettingsButtonCard` (~170),
   `LoadingContent` (~30), `CompactVideoCard` (~60). Replace: nothing.
   [`core/designsystem/.../component/SettingsCards.kt`, `LoadingContent.kt`, `VideoCard.kt`]
5. **Whole dead component files** — `TabRow.kt` (68, `BluejayTabRow`), `SettingsRow.kt` (71),
   `FilterChip.kt` (37, `BluejayFilterChip`), `NavigationState.kt` (31, old nav-chrome design +
   `rememberNavigationState`), `SettingsData.kt` (18), `VideoHelperExtensions.kt` (39),
   `ButtonDefinition.kt` (15, stub "replaces MenuBottomBarFragment.ButtonDefinition for legacy XML
   UI" that was never wired). Replace: nothing.
   [`core/designsystem/.../`, `core/navigation/NavigationState.kt`, `app/src/main/.../helpers/`,
   `app/src/main/.../navigation/`] — 279 lines
6. **`testing/DBTOs.kt`** — a `@Database` in the **main** source set (ships in the APK), referenced
   only by androidTest. Replace: move to `androidTest` or delete.
   [`app/src/main/.../testing/DBTOs.kt`] — 46 lines
7. **`DashManifestSource`, `HLSManifestSource`** — never constructed; JS sources are always built
   via `JSSource.fromV8*` type map (only `JS*` variants are instantiated).
   ⚠️ Confirm `dep/polycentricandroid` doesn't construct them. Replace: nothing.
   [`api/media/.../streams/sources/DashManifestSource.kt`, `HLSManifestSource.kt`] — 40 lines
8. **`PlatformComment`** (open class, no subtypes, no refs), **`MultiChronoContentParallelPager`**
   (only reference is a commented-out line in `RefreshChronoContentPager.kt`),
   **`ManagedDBContextPaged`**, **`ManagedDBIndexOnly`** (0 refs). Replace: nothing. — 106 lines
9. **9 dead extension functions** — `replaceTimestamps`, `replaceLinks` (Extensions_Formatting);
   `ipStringToBytes`, `textToNumericFormatV4/V6`, `parseHextet`, `parseOctet` (Extensions_Network —
   entire IP-parsing block); `V8ArrayToStringList`, `V8ObjectToHashMap` (Extensions_V8).
   Replace: nothing. — ~95 lines
10. **`QRCaptureActivity`** — manifest-registered, nothing in the app ever launches it → also drop
    `com.google.zxing:core` + `com.journeyapps:zxing-android-embedded` (its only consumer).
    ⚠️ Confirm polycentric doesn't start it by Intent. Replace: nothing.
    [`activities/QRCaptureActivity.kt` (6) + manifest entry + 2 deps]
11. **`ShortCard`, `ArticleCard`** card variants — never built or matched anywhere in the `Card`
    sealed hierarchy (VideoCard/PlaylistCard/ChannelCard/PostCard are live). Replace: nothing.
    [`core/model/Cards.kt`] — ~36 lines
12. **Stale Room schema exports** from the pre-rename `com.futo.platformplayer` package
    (unimportable history; the current `com.tsutsen...` ones are kept). Replace: nothing.
    [`app/schemas/com.futo.platformplayer.*/`] — 3 dirs
13. **`com.arthenica:smart-exception-java:0.2.1`** dep — zero usages; FFmpegKit classes come from
    the bundled `app/aar/ffmpeg-kit.aar`. Replace: nothing. [`app/build.gradle`]
14. **`media3-exoplayer-rtsp` + `media3-exoplayer-smoothstreaming`** — no `RtspMediaSource` /
    `SsMediaSource` anywhere (playback is DASH/HLS/custom SABR). Replace: nothing.
    [`app/build.gradle`]
15. **`androidx.test:monitor:1.8.0`** declared at `implementation` scope with zero main-code
    references — test lib on the runtime classpath. Replace: remove or re-scope.
    [`app/build.gradle:165`]

## native / shrink / yagni

 1. **Glide coexists with Coil** (Glide: 7 files, Coil: 18). Migrate the last call sites
    (`Utility`, `BackgroundWorker`, `MediaPlaybackService`, `StateNotifications`, `ImageVariable`)
    to Coil, then drop Glide + `glide:compiler` + `images/{BluejayAppGlideModule,
    PolycentricModelLoader}.java`. ~2 deps + ~170 lines.
 2. **Hand-rolled `debug/Stopwatch`** (31 lines) with a single consumer
    (`SubscriptionsTaskFetchAlgorithm`). `kotlin.time.Stopwatch` + the one log line, or inline it.
 3. **`IAudioUrlWidevineSource` / `IDashManifestWidevineSource` / `IVideoUrlWidevineSource`** —
    each has exactly one implementation (the matching `JS*` class). Keep shared `IWidevineSource`
    (cast type in `VideoHelper`); delete the 3 single-impl sub-interfaces. ~45 lines.

## Checked, kept (not cuttable)

- `stores/v2` — live (`ManagedStore`/`ReconstructStore` used by StateApp/Backup/Downloads/History).
- Encryption `V0` providers — still reachable: legacy backups + old plugin-auth versions,
  version-gated in code. Removable only if breaking old backups is acceptable.
- `noise/` vendored crypto (Tor-derived) — fully wired internally, used by `sync/`.
- `LittleEndian*` streams — justified, Java stdlib has no little-endian DataIn/OutputStream.
- `gson` (5 files) — intentional for anonymous/V8-remote-object JSON.
- `states/` (21 singletons) + `core/data` repositories — dual architecture mid-migration
  (see `docs/refactoring-opportunities.md`); everything still referenced. That's a refactor, not a cut.
- `grgit` (used in `build.gradle` for `gitVersionName`), `documentfile` (used in `Utility.kt`),
  `kotlin-reflect` (used in `JSClient`/`Extensions_V8`).
- `LoginDialog` + `LoginScreen` — both live (plugin login vs. app login).
- Pagers in `api/media/structures/` — all 21 have ≥1 real consumer; only
  `MultiChronoContentParallelPager` is dead (finding 8).

## Repo hygiene (not counted)

- `rmdir 'runtime/runtime-android,' 'ui/ui-android}'` — typo-named empty local dirs.
- Rename `scripts/generate-pivate-key.sh` ("pivate").
- `app/com.futo...SubscriptionStorage.json` referenced in `.gitignore` — stale entry after rename.

## Net

`net: -2,400 lines, -7 deps possible` (8 with Glide→Coil consolidation; the Glide item adds the 2
`images/*.java` files).

---

## Feature-gap analysis: plugin submodules vs. grayjay

Method: bluejay and grayjay ship the **same 22 JS source plugins** (apple-podcasts, bilibili,
bitchute, crunchyroll, curiositystream, dailymotion, fosdem, internet-archive, kick, mixcloud,
nasa-plus, nebula, odysee, patreon, peertube, radiobrowser, redbull-tv, rumble, soundcloud,
tedtalks, twitch, youtube) and the same two dep submodules (`dep/polycentricandroid`,
`dep/futopay`). grayjay's plugin submodules are not checked out locally, but its **plugin engine
is identical to bluejay's** (same 9 files in `engine/packages/`: Libcurl, PackageBridge,
PackageBrowser, PackageDOMParser, PackageHttp(Imp), PackageJSDOM, PackageUtilities, V8Package)
— so the JS API surface plugins can call is the same in both. The gap is therefore **client-side
product code**: grayjay (old `com.futo.platformplayer` View/Fragment app) has features that
bluejay (new `com.tsutsen.platformplayer` Compose multi-module app) has not ported.

bluejay's `BluejayNavGraph.kt` currently wires **24 `NavDestination`s to
`PlaceholderScreen("…", "Coming soon")`** — that list is the back-bone of this section.

### A. Engine/data layer ported, UI missing entirely

1. **Sync** — full engine present (`sync/`: SyncService socket, opcodes, sync packages, workers,
   `StateSync`, pairing-code plumbing in `sync/internal/*`) but **zero UI**: no device list,
   no pairing screen, no pairing-code display. grayjay: `SyncHomeActivity`, `SyncPairActivity`,
   `SyncShowPairingCodeActivity`. In bluejay, sync can never be started.
2. **Polycentric** — data layer present (`PolycentricStorage`, `ModerationsManager`, profile
   proto in backup, polycentric channel URLs used by `StateSubscriptions`) but **zero UI**:
   no create/import/backup profile screens, no moderation screens, no identicon, no
   Polycentric/Platform comment-section tabs (grayjay: 7 `Polycentric*Activity` files,
   `IdenticonView`, comment section stored in `StateMeta`). Comment *posting* in grayjay
   (`AddCommentView`/`CommentDialog`) is tied to polycentric's `Protocol.Reference` — bluejay is
   view-only on comments.
3. **Watch-history browsing** — `StateHistory` is a full engine (watch positions, `isHistoryWatched`,
   legacy migration, backup reconstruction, `HistoryEntity` in Room) but there is **no
   `History` NavDestination and no screen**; "history" in bluejay today means search-history only.
   grayjay: `HistoryFragment`.
4. **Watch Later** — full data layer (add/remove/reorder in `StatePlaylists`,
   `SyncWatchLaterPackage`) but the screen is a placeholder.
5. **Subscription groups** — sync model exists (`SyncSubscriptionGroupPackage`) but
   `SubscriptionGroupList`/`SubscriptionGroupDetail` are placeholders. grayjay:
   `SubscriptionGroupFragment`/`SubscriptionGroupListFragment`.
6. **Music-library sub-screens (8 placeholders)** — `LibraryAlbums`, `LibraryAlbumDetail`,
   `LibraryArtists`, `LibraryArtistDetail`, `LibraryVideos`, `LibraryFiles`, `LibrarySearch`
   (soundcloud albums/artists browsing). bluejay's library renders sections only.
7. **Import subscriptions / playlists** — placeholders; grayjay has import dialogs +
   `ImportSubscriptionsFragment`/`ImportPlaylistsFragment` (import from URL/file in the
   grayjay/backup format). bluejay keeps `ImportCache` plumbing but no flow.
8. **Buy (futopay pro license)** — dependency is wired (`com.tsutsen.futopay:app:1.0` +
   `includeBuild dep/futopay`), `StatePayment` verifies/stores license keys, but the `Buy`
   screen is a placeholder: the app can check a license but you cannot buy one in-app (grayjay:
   `BuyFragment`/`BuyView` with buy/paid buttons + paying overlay).
9. **In-app browser + web content** — `Browser` and `WebDetail` navs are placeholders; WebView is
   used only for login/CAPTCHA. grayjay: `BrowserFragment`, `WebDetailFragment`.
10. **Post / Article / Web detail screens** — all placeholders. Consequence: the **home feed only
    renders Video/Channel/Playlist cards**; posts, articles and web items returned by plugins are
    silently not shown (models exist: `JSPost`/`JSArticle`/`JSWeb`, `PostCard`/`ArticleCard`
    variants — which is also why the dead `ShortCard`/`ArticleCard` audit items above exist:
    half-built, unconnected). grayjay renders all card types with detail screens.
11. **Tutorial/onboarding** — placeholder; grayjay: `TutorialFragment`.
12. **Suggestions** — placeholder; grayjay: `SuggestionsFragment` (query-based creator/content
    suggestions). (bluejay's search shows results inline, which partially overlaps.)
13. **Announcement / update banner** — `StateAnnouncement` exists (7 refs) with no banner
    composable; grayjay: `AnnouncementView` + `UpdateBannerView`.

### B. Playback gap

 1. **DRM / Widevine playback** — grayjay wires `PluginMediaDrmCallback` into its player
    (`FutoVideoPlayerBase` passes `videoSource.licenseUri` + `getLicenseRequestExecutor()`).
    bluejay has **zero `MediaDrm` references** anywhere: the `JS*WidevineSource` models are
    parsed (and correctly excluded from downloads in `VideoHelper.isDownloadable`) but there is
    no license acquisition → **DRM-protected plugins (crunchyroll, nebula, redbull-tv) cannot
    play in bluejay**.

### C. Player / product gaps (partial features)

 1. **Shorts** — `StatePlayer._shortExoPlayer` plumbing exists (getter appears unused outside
    `StatePlayer` itself); the `Shorts` nav is a placeholder and `isClip` is only a Twitch-clip
    badge. grayjay: `ShortsFragment` + `FutoShortPlayer` + `ShortView` (vertical video feed).
 2. **Queue editor** — `removeFromQueue` (single item) exists; no in-player queue editor UI
    (reorder / remove multiple). grayjay: `QueueEditorOverlay` + `UpNextView`.
 3. **Channel monetization + store tabs** — bluejay's channel screen has Videos / Playlists /
    Shorts / About only; grayjay adds a **Monetization tab** (creator support, futopay —
    `ChannelMonetizationFragment`/`SupportView`) and a **Store tab** (`ChannelStoreFragment`).
    bluejay's only "monetization" reference is an icon name in `Icons.kt`.
 4. **Hidden videos/creators** ("not interested" filtering, `StateMeta.hiddenVideos/hiddenCreators`
    - last-comment-section memory) — no equivalent in bluejay.
 5. **Per-plugin changelog display** — plugin configs ship `changelog` but no UI shows it (grayjay:
    `ChangelogDialog`).
 6. **Chromecast setup guide** (grayjay `FCastGuideActivity`) — no equivalent (the cast sheet
    itself exists: `CastingSheet.kt`).

### D. Verified present in bluejay (not gaps)

Search (inline results + search history), plugin management (enable/disable toggles,
embedded-plugin install, per-plugin settings fields, update checks in `StatePlugins`), automatic
backup prompt (`StateApp` + `showAutomaticBackupDialog`), live chat with donations
(`LiveChatPanel`, `LiveEventDonation`), chapters, comment *viewing* (`CommentCard`,
`showComments` setting), casting sheet, notifications screen, library sections, watch-later
data, plugin browser.

### Suggested priority (by user-visible impact)

1. Widevine/DRM playback (blocks an entire class of plugins) — B14
2. Post/Article/Web rendering in home + detail screens — A10
3. Sync UI (engine already fully ported) — A1
4. Watch history + Watch Later screens (data layers exist) — A3, A4
5. Buy screen (futopay already wired) — A8
6. Polycentric UI (largest; data layer exists) — A2
7. Shorts feed, music library sub-screens, imports, tutorial, suggestions, announcements, queue
   editor, channel monetization/store, hidden-content, changelog, browser, cast guide — A5-A13, C

Note: the `dep/polycentricandroid` and `dep/futopay` submodules are the same repos grayjay pins
(different commits, same code lineage); nothing in bluejay's dep submodules is missing relative
to grayjay — the gap is entirely in the client.
