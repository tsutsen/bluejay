# Glossary

Domain terms as the codebase uses them. Each line names the code that owns the concept.

| Term | Meaning |
|------|---------|
| **video** | A playable content item: `ContentItem`/`VideoCard` in `core:model`, backed by a plugin's `IPlatformVideo`/`IPlatformVideoDetails`. Videos are identified by their content URL. |
| **channel** | A creator's page and its feed of videos/playlists; `ChannelRepository` + `IPlatformChannel` (author metadata via `PlatformAuthorLink`). |
| **playlist** | An ordered video collection. Either a plugin playlist (`IPlatformPlaylist`) or a local user playlist stored in Room (`PlaylistEntity`, `LibraryRepository.observeLocalPlaylist`). |
| **download** | The offline copy engine: `VideoDownload` (queued/in-progress work item, strategies under `app/downloads`), `VideoLocal` (completed, persisted), surfaced to the UI as `DownloadInfo`. |
| **subscription** | A channel the user follows; `SubscriptionRepository` feeds the Subscriptions tab and its filters. |
| **history** | The watch-history library section: one row per watched video with `lastPositionMs`, used for resume and watched-state marks. |
| **queue** | The playback queue ("Up next"): `PlaybackQueueRepository` orders the videos the player advances through. |
| **plugin** (a.k.a. **source**) | A scripted content provider (`IPlatformClient`, `JSClient`) loaded by the engine; "source" is the user-facing name for a plugin. |
| **cast** | Sending playback to an external receiver (Chromecast/fcast): `CastingRepository`, `PlayerState.isCasting`, and the companion/presentation display. |
| **pager** | A plugin result page (`IPager`/`PagedSource`: `getResults` returns only the last page). `PagerFeed` accumulates pages into a flat, key-deduplicated list. |
| **settings** | User preferences: `SettingsRepository`/`AppPreferences` (typed) over the legacy `Settings` singleton that remains the persisted source of truth. |
