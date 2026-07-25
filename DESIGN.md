# Bluejay Fork — Design Specification

> **Status**: Draft  
> **Last updated**: 2026-07-23  
> **Target**: AYN Thor dual-screen handheld (API 33+)  
> **Scope**: Complete UI/UX design for Compose migration — screens, components, layouts

---

## 1. Chrome / Navigation

### AppLayout

Single Compose layout that detects orientation and renders NavigationRail (landscape) or bottom bar (portrait).

**No top bars** on any main tab screen. Content starts immediately after nav chrome.

#### Navigation destinations (6 tabs)

| Icon | Route | Label |
|------|-------|-------|
| Home | `home` | Home |
| Search | `search` | Search |
| Subscriptions | `subscriptions` | Subs |
| Library | `library` | Library |
| Notifications | `notifications` | 🔔 |
| Settings | `settings` | Settings |

#### Portrait layout

```
┌─────────────────────────────────────────┐
│                                         │
│  [Screen content — no top bar]          │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

#### Landscape layout

```
┌──┬────────────────────────────────────────────────────────────┐
│  │  [◉ Home]  [🔍 Search]  [◉ Subs]  [📚 Lib]  [🔔]  [⚙️]      │
│  ├────────────────────────────────────────────────────────────┤
│  │                                                            │
│  │  [Screen content]                                          │
│  │                                                            │
└──┴────────────────────────────────────────────────────────────┘
```

| Zone | Portrait | Landscape |
|------|----------|-----------|
| Nav style | Bottom NavigationBar (56dp tall) | Side NavigationRail (56dp wide) |
| Nav items | 6 icons with labels | Same 6 icons, no labels |
| Content area | Full width × remaining height | 100% width × full height |

---

## 2. Home Screen — Feed

Single infinite scroll feed. No sections, no subscriptions, no top bar.

### Portrait

```
┌─────────────────────────────────────────┐
│                                         │
│  [VideoCard]                            │
│  [VideoCard]                            │
│  [VideoCard]                            │
│  [VideoCard]                            │
│  ... (infinite scroll)                  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Landscape

```
┌─────────────────────────────────────────────────────────────────────┐
│ │  [◉ Home]  [🔍 Search]  [◉ Subs]  [📚 Lib]  [🔔]  [⚙️]        │
│ ├───────────────────────────────────────────────────────────────────┤
│ │                                                                 │
│ │  [VideoCard] [VideoCard] [VideoCard]                            │
│ │  [VideoCard] [VideoCard] [VideoCard]                            │
│ │  [VideoCard] [VideoCard] [VideoCard]                            │
│ │  ... (3-col grid, infinite scroll)                              │
│ │                                                                 │
└─────────────────────────────────────────────────────────────────────┘
```

**ViewModel:** `HomeViewModel` — sealed `UiState` (Loading/Success/Error)

---

## 3. Search Screen

### Layout

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─ Search Bar ──────────────────────┐  │
│  │  🔍 [Search...              ]  🗑️ │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Filter Badges ───────────────────┐  │
│  │  [All ●]  [YouTube ●]  [SoundCloud ●]│
│  │  [Twitch]  [History ●] [Playlists ●]│
│  │  [Watch Later ●]                    │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Results ─────────────────────────┐  │
│  │  [VideoCard]                       │  │
│  │  [ChannelCard]                     │  │
│  │  [VideoCard]                       │  │
│  │  [PlaylistCard]                    │  │
│  │  ...                               │  │
│  └─────────────────────────────────────┘  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Search Bar

- Full-width `TextField`, rounded corners (12dp)
- Search icon on left, clear icon on right (visible when text ≠ empty)
- Triggers search on submit or after ~300ms debounce

### Filter Badges

`FilterChip` style, horizontally scrollable.

| Badge | Meaning |
|-------|---------|
| **All** | Master toggle — when off, no sources searched |
| **YouTube** | Toggle YouTube source search |
| **SoundCloud** | Toggle SoundCloud source search |
| **Twitch** | Toggle Twitch source search |
| **...** | One per configured source plugin |
| **History** | Toggle searching watch history |
| **Playlists** | Toggle searching user playlists |
| **Watch Later** | Toggle searching watch later list |

**Badge states:**
- Active (●): Filled/highlighted chip — included in search
- Inactive: Outlined/ghost chip — excluded

### Search Results

Mixed content types in a single scrollable list:

| Type | Layout |
|------|--------|
| **Video** | Standard video card |
| **Channel** | Channel card (avatar + name + subscribers) |
| **Playlist** | Playlist card (cover, name, meta) |
| **Article** | Article card (thumbnail + title + author) |
| **Post** | Social post card (avatar + text preview) |

### Empty States

| State | Display |
|-------|---------|
| No query | "Recent searches" list |
| Searching | Shimmer skeleton cards |
| No results | "No results found" + "Try different keywords" |
| No sources selected | "Turn on at least one source to search" (distinct from "No results" — avoids implying the query itself was bad) |
| Error | "Couldn't search — tap to retry" |

---

## 4. Subscriptions Screen

### Portrait Layout

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─ Creator Avatars (horizontal scroll)┐│
│  │  [Avatar] [Avatar] [Avatar] [A]    ││
│  │  [Avatar] [Avatar] [Avatar] [A]    ││
│  └─────────────────────────────────────┘│
│                                         │
│  ┌─ Filters ─────────────────────────┐  │
│  │  [Watched ●] [Continue] [Video]  │  │
│  │  [Streams] [YouTube ●] [SC ●]     │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Videos ──────────────────────────┐  │
│  │  [VideoCard]                       │  │
│  │  [VideoCard]                       │  │
│  │  [VideoCard]                       │  │
│  └─────────────────────────────────────┘  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Landscape Layout

Creators strip on the **right side** (reverse navbar), videos + filters in center.

```
┌─────────────────────────────────────────────────────────────────────┐
│ │  [◉ Home]  [🔍 Search]  [◉ Subs]  [📚 Lib]  [🔔]  [⚙️]        │
│ ├───────────────────────────────────────────────────────────────────┤
│ │                                                                 │
│ │  ┌─ Filters ─────────────────────────────────────────────────┐   │
│ │  │  [Watched ●] [Continue] [Video ●] [Streams] [YT ●] ... │   │
│ │  └───────────────────────────────────────────────────────────┘   │
│ │                                                                 │
│ │  ┌─ Videos (3-col grid) ─────────────────────────────────────┐   │
│ │  │  [VideoCard] [VideoCard] [VideoCard]                      │   │
│ │  │  [VideoCard] [VideoCard] [VideoCard]                      │   │
│ │  └───────────────────────────────────────────────────────────┘   │
│ │                                                                 │
│ │  ┌─────────────────────────────────────────────────────────┐    │
│ │  │  [Avatar]  ← Vertical scroll                            │    │
│ │  │  [Avatar]                                                  │    │
│ │  │  [Avatar]                                                  │    │
│ │  │  [Avatar]                                                  │    │
│ │  │  [Avatar]                                                  │    │
│ │  │  [Avatar]                                                  │    │
│ │  └─────────────────────────────────────────────────────────┘    │
│ │                                                                 │
└─────────────────────────────────────────────────────────────────────┘
```

### Creator Avatar Strip

**Portrait:** Horizontal scroll, avatars 48dp circles, 8dp gap. Selected avatar = 52dp with accent ring. "All" avatar at start.

**Landscape:** Vertical scroll on right side, 64dp wide column, same avatar sizing.

**States:**
| State | Visual |
|-------|--------|
| Selected | Accent ring, 52dp |
| Unselected | Flat, 48dp |
| Has new content | Small dot indicator |
| Source label | Below avatar (portrait only) |

### Filter Badges

| Badge | Meaning |
|-------|---------|
| **Watched ●** | Videos where user watched ≥95% of duration |
| **Continue ●** | Videos with watchtime >1s but <95% of duration |
| **Video ●** | Regular videos |
| **Streams** | Live/recent streams |
| **YouTube ●** | Filter to YouTube source only |
| **SoundCloud ●** | Filter to SoundCloud source only |
| **Twitch** | Filter to Twitch source only |

**Rules:**
- Watched / Continue: mutually exclusive
- Video / Streams: mutually exclusive
- Source badges: independent, combinable

**Default:** Watched ●, Video ●, all sources active.

---

## 5. Library Screen

### Layout

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─ History  ─────────────────────── >  │
│  │  [Card] [Card] [Card] [Card] [Card] │  │  ← Horizontal scroll strip
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Watch Later  ─────────────────── >  │
│  │  [Card] [Card] [Card] [Card]          │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Playlists  ───────────────────── >  │
│  │  [PCard] [PCard] [PCard] [PCard]     │  │
│  └─────────────────────────────────────┘  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Strips

Three horizontal-scroll strips, each with a section title and ">" chevron.

| Strip | Card type | Content |
|-------|-----------|---------|
| **History** | VideoCard | Recently watched videos |
| **Watch Later** | VideoCard | Saved videos |
| **Playlists** | PlaylistCard | User-created playlists |

### Section Header + ">"

```
┌─ History  >  ──────────────────────────────────────────────┐
```

- Section title (e.g. "History", "Watch Later", "Playlists")
- ">" chevron on the right
- Tap ">" → opens full view

### Full View (reusable, triggered by ">")

Same layout as Home feed — single infinite scroll of cards.

**History full view:**

```
┌─────────────────────────────────────────┐
│  ← History                              │
├─────────────────────────────────────────┤
│  [VideoCard]                            │
│  [VideoCard]                            │
│  [VideoCard]                            │
│  ... (infinite scroll)                  │
└─────────────────────────────────────────┘
```

**Playlists full view:**

```
┌─────────────────────────────────────────┐
│  ← Playlists                            │
├─────────────────────────────────────────┤
│  [PlaylistCard]                         │
│  [PlaylistCard]                         │
│  [PlaylistCard]                         │
│  [+ Create Playlist]                    │
└─────────────────────────────────────────┘
```

### Playlist Detail (opens from full view)

```
┌─────────────────────────────────────────┐
│  ← Playlist Name                        │
├─────────────────────────────────────────┤
│  [Icon]  Playlist Name                  │
│  N videos • Total duration              │
│  [▶ Play All]                           │
│                                         │
│  [VideoCard]                            │
│  [VideoCard]                            │
│  [VideoCard]                            │
│  ...                                    │
└─────────────────────────────────────────┘
```

---

## 6. Settings Screen

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─ Appearance ──────────────────────┐  │
│  │  Theme Mode          [Auto ▾]     │  │
│  │  Font              [Inter ▾]      │  │
│  │  Icon Style          [Rounded ▾]  │  │
│  │  Contrast            [Standard ▾] │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Playback ────────────────────────┐  │
│  │  Auto-play              [ON/OFF]  │  │
│  │  Quality preference     [720p ▾]  │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Plugins ─────────────────────────┐  │
│  │  [Open Plugin Browser →]          │  │
│  │  [Manage Installed Sources]       │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ About ───────────────────────────┐  │
│  │  Version            v0.x.x         │  │
│  │  Open Source License              │  │
│  │  [Open GitHub Repo →]             │  │
│  └─────────────────────────────────────┘  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Preference controls

- Dropdowns for enum values (ThemeMode, FontChoice, IconStyle, Contrast)
- Toggles for booleans
- Text/links for About section

---

## 7. Video Detail Screen

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌───────────────────────────────────┐  │
│  │      [Video Player / Poster]      │  │
│  └───────────────────────────────────┘  │
│                                         │
│  [Video Title - full text, multi-line]  │
│                                         │
│  [12K views]  •  [3 days ago]          │
│                                         │
│  ┌─ Channel Row ─────────────────────┐  │
│  │  ┌────┐                             │  │
│  │  │    │  Channel Name               │  │
│  │  │Avtr│  125K subscribers            │  │
│  │  └────┘  [Subscribe]                │  │
│  │           [👍 1.2K]  [👎 45]        │  │
│  │           [Watch Later] [Share] [⋮] │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Description ─────────────────────┐  │
│  │  Description text...               │  │
│  │  [Show more ▼]                     │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ [Comments]  [Recommended] ───────┐  │
│  │                                       │
│  │  [Comment card - username, time,      │
│  │   text, like count, reply, copy,      │
│  │   expandable replies]                │
│  │                                       │
│  │  [Comment card]                       │
│  │                                       │
│  └───────────────────────────────────────┘
└─────────────────────────────────────────┘
```

### Video Player

See §10 for full player specification.

### Title + Meta

- Title: `headlineSmall`, multi-line
- Views + time: `bodySmall`, separated by •

### Channel Row

| Element | Detail |
|---------|--------|
| Avatar | 48dp circle |
| Channel name | `titleMedium`, bold |
| Sub count | `bodySmall`, `onSurfaceVariant` |
| Subscribe button | Filled pill (accent) or outlined |
| Like/Dislike | Inline text badges, `bodySmall` |
| Watch Later | Icon button |
| Share | Icon button → system share sheet |
| ⋮ More | Menu: Add to playlist, Add to favorites, Download, PiP |

### Description

- Expandable: 2-3 lines → full text
- "Show more" / "Show less" toggle
- Clickable links if present

### Comments Tab

```
┌─ Comment ──────────────────────────────┐
│  username  •  2h ago                    │
│  👍 42   💬 Reply   📋 Copy             │
│                                         │
│  Comment text goes here...              │
│                                         │
│  3 replies ▼                            │
└─────────────────────────────────────────┘
```

| Element | Detail |
|---------|--------|
| Username | `bodyMedium`, bold |
| Relative time | `bodySmall`, `onSurfaceVariant` |
| Like count | `bodySmall`, with thumb icon |
| Reply button | Text button, opens inline reply composer |
| Copy button | Copies text to clipboard, shows toast |
| Comment text | `bodyMedium`, multi-line |
| Replies | Indented, same structure, expandable |

### Recommended Tab

Same reusable video container as Home feed.

---

## 8. Channel Detail Screen

```
┌─────────────────────────────────────────┐
│  ←                                      │
├─────────────────────────────────────────┤
│                                         │
│  ┌─ Hero ────────────────────────────┐  │
│  │  [Channel Banner Image - full]    │  │
│  │  ┌────┐                             │  │
│  │  │    │  Channel Name               │  │
│  │  │Avtr│  125K subscribers           │  │
│  │  │    │  [Subscribe]                │  │
│  │  └────┘                             │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Tabs ────────────────────────────┐  │
│  │  [Videos]  [Shorts]  [Playlists]  │  │
│  │               [About]              │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Videos Tab ──────────────────────┐  │
│  │  [VideoCard]                       │  │
│  │  [VideoCard]                       │  │
│  │  [VideoCard]                       │  │
│  └─────────────────────────────────────┘  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Hero

- Banner: full-width, 120dp tall
- Avatar: 56dp circle, overlaps banner bottom
- Channel name: `headlineSmall`, bold
- Sub count: `bodySmall`, `onSurfaceVariant`
- Subscribe button: filled or outlined pill

### Tabs

| Tab | Content |
|-----|---------|
| **Videos** | Full-length videos, standard video cards |
| **Shorts** | Short-form videos, 9:16 vertical cards |
| **Playlists** | Channel's playlists, playlist cards |
| **About** | Description, joined date, source, links |

### Videos Tab

Standard video cards, batch-paginated (see §11.1 `pagination = true`) — Previous/Next controls at the bottom, not infinite scroll. Tap a card → Video Detail.

### Shorts Tab

```
┌─────────────────────────────────────────┐
│  ┌────┐  ┌────┐  ┌────┐                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  │    │  │    │  │    │                │
│  └────┘  └────┘  └────┘                │
│  Title 1  Title 2  Title 3              │
│  12K      45K      8K                   │
└─────────────────────────────────────────┘
```

- 9:16 vertical cards, ~120dp wide × 213dp tall
- Thumbnail fills top ~80%, title + view count below
- Tap → Shorts player (full-screen vertical)
- Swipe up/down between shorts
- Landscape: single column

### Playlists Tab

Playlist cards (same style as Library), batch-paginated (see §11.1 `pagination = true`), tap → Playlist Detail.

### About Tab

- Collapsible description
- Metadata: joined date, source, location
- External links (if available)

---

## 9. Notifications Screen

```
┌─────────────────────────────────────────┐
│                                         │
│  ┌─ Today ───────────────────────────┐  │
│  │  ┌─ Notification ──────────────┐  │  │
│  │  │  [Avatar]  Channel Name     │  │  │
│  │  │  uploaded a video           │  │  │
│  │  │  2h ago                     │  │  │
│  │  │  [VideoCard - compact]      │  │  │
│  │  └──────────────────────────────┘  │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Yesterday ───────────────────────┐  │
│  │  [Notification cards...]            │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ This Week ───────────────────────┐  │
│  │  [Notification cards...]            │  │
│  └─────────────────────────────────────┘  │
│                                         │
├─────────────────────────────────────────┤
│  ◉ Home  🔍 Search  ◉ Subs  📚 Lib  🔔  ⚙️  │
└─────────────────────────────────────────┘
```

### Notification types

| Type | Content | Preview |
|------|---------|---------|
| **New video** | Channel uploaded a video | Video card |
| **Live stream** | Channel went live | Live badge |
| **Stream ended** | Channel's stream ended | Text only |
| **Upload scheduled** | Channel scheduled content | Text only |
| **Track released** (SoundCloud) | New track from followed creator | Track card |
| **Reply** (Polycentric) | Someone replied to your post | Post preview |
| **Like/Boost** (Polycentric) | Someone liked/boosted your post | Text only |

### Notification card

```
┌─────────────────────────────────────────┐
│  ┌────┐                                 │
│  │    │  Channel Name                    │
│  │Avtr│  uploaded a video                │
│  │    │  2h ago                          │
│  └────┘                                 │
│                                         │
│  [VideoCard - compact preview]           │
│                                         │
│  [🔕 Mute]  [Dismiss]                   │
└─────────────────────────────────────────┘
```

### Grouping

| Bucket | Range |
|--------|-------|
| **Today** | Last 24 hours |
| **Yesterday** | 24-48 hours ago |
| **This Week** | 2-7 days ago |
| **Earlier** | 7+ days ago |

### Unread indicator

🔔 icon in nav bar shows red dot when there are unread notifications.

---

## 10. Video Player

> **Scope note**: everything in this section — overlays, edge-swipe gestures for
> minimize/exit — describes the **fullscreen player**. The player embedded inline
> at the top of the Video Detail screen (§7) is a separate, reduced-chrome variant:
> it must not claim the top/bottom 80dp edge-swipe zones described below, since
> those would conflict with normal page scrolling on that screen. The inline
> variant's exact control set (persistent play/pause + progress bar? tap-to-expand
> only?) isn't specified yet — flagging as open rather than assuming.

### Layout

```
┌─────────────────────────────────────────┐
│  ┌───────────────────────────────────┐  │
│  │      [Video Content]              │  │
│  └───────────────────────────────────┘  │
│                                         │
│  ┌─ Top Overlay ─────────────────────┐  │
│  │  [⬇ Minimize]                     │  │
│  │  [Video Title]                    │  │
│  │  [Channel Name]                   │  │
│  │                          [🔁] [🕐] [⚙]│  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Bottom Overlay ──────────────────┐  │
│  │  ━━━━━━━━━━━━━━━○  3:42/5:00     │  │
│  │  [⏮]  [⏯]  [⏭]          [📖] [⛶]│  │
│  └─────────────────────────────────────┘  │
│                                         │
└─────────────────────────────────────────┘
```

### Top Overlay

| Element | Position | Detail |
|---------|----------|--------|
| Minimize | Top-left | `⬇` icon, taps to minimize to companion |
| Title | Below minimize, left | `bodyMedium`, white, 1 line max |
| Channel name | Below title, left | `bodySmall`, white, 80% opacity |
| Replay | Top-right | `🔁` icon, toggle on/off |
| Watch Later | Top-right | `🕐` icon |
| Options | Top-right | `⚙` icon → opens modal |

### Options Modal

```
┌─────────────────────────────────────────┐
│  Speed                                  │
│  [0.5x]  [0.75x]  [1x ●]  [1.25x]     │
│  [1.5x]  [2x]                           │
│                                         │
│  Quality                                │
│  [Auto ●]  [1080p]  [720p]             │
│  [480p]  [360p]                         │
│                                         │
│  Audio                                  │
│  [Original ●]  [English]               │
│  [Spanish]                              │
└─────────────────────────────────────────┘
```

### Bottom Overlay

| Element | Position | Detail |
|---------|----------|--------|
| Timeline | Full width | Progress bar, current time / total duration |
| Previous | Bottom-left | `⏮` icon, skip to previous in queue |
| Play/Pause | Bottom-left | `⏯` icon, large |
| Next | Bottom-left | `⏭` icon, skip to next in queue |
| Chapters | Bottom-right | `📖` icon, opens chapter list |
| Fullscreen | Bottom-right | `⛶` icon, toggles fullscreen |

### Chapters

```
┌─ Chapters ─────────────────────────────────┐
│  0:00  Intro                               │
│  0:45  Section One                         │
│  2:30  Section Two                         │
│  4:15  Section Three                       │
│  5:00  Outro                               │
└─────────────────────────────────────────────┘
```

- Current chapter highlighted
- Tap to seek to chapter start
- Only shows when chapters are available

### Auto-hide

- Overlay fades after 3s inactivity while playing
- Tap anywhere to toggle visibility

---

### Gesture Controls

| Gesture | Region | Action |
|---------|--------|--------|
| Swipe down | Left half | Decrease brightness |
| Swipe up | Left half | Increase brightness |
| Swipe down | Right half | Decrease volume |
| Swipe up | Right half | Increase volume |
| Swipe down from top edge | Full screen | Minimize to companion |
| Swipe up from bottom edge | Full screen | Exit fullscreen → detail page |
| Double-tap | Left half | Seek back 10s |
| Double-tap | Right half | Seek forward 10s |
| Single tap | Anywhere | Toggle overlay visibility |

### Brightness Indicator (left side)

```
☀
═════════○
═════════

75%
```

- Sun icon (changes with level)
- Vertical slider, 4dp wide, accent color
- Percentage text below
- 32dp from left edge
- Fades out 1.5s after gesture ends

### Volume Indicator (right side)

```
              🔊
              ══════○
              ══════

              60%
```

- Speaker icon (changes with level)
- Vertical slider, 4dp wide, accent color
- Percentage text below
- 32dp from right edge
- Fades out 1.5s after gesture ends

### Minimize Gesture (top edge)

- Trigger zone: top 80dp of screen
- Swipe down from top edge
- Video follows finger, scales down as you drag
- Threshold: drag past 50% → snap to minimize
- Below threshold: snap back to full screen

### Exit Fullscreen Gesture (bottom edge)

- Trigger zone: bottom 80dp of screen
- Swipe up from bottom edge
- Video scales down, revealing detail page behind
- Threshold: drag past 30% → snap to detail view
- Below threshold: snap back to fullscreen

---

## 11. Reusable Components

### 11.1 VideoContainer

Arranges cards in configurable layouts. Type-agnostic — accepts any `Card` subtype.

#### Configuration

| Parameter | Values | Default | Description |
|-----------|--------|---------|-------------|
| **layout** | `grid`, `list` | `list` | Card arrangement |
| **scrollDirection** | `horizontal`, `vertical` | `vertical` | Scroll axis |
| **columns** | Any int ≥ 1 | `1` / `3` | Grid columns |
| **rows** | Any int ≥ 1 or `Int.MAX_VALUE` | `1` / `∞` | Grid rows |
| **pagination** | `true`, `false` | `false` | Batch loading vs infinite scroll |
| **pageSize** | Any int ≥ 1 | `20` | Videos per batch |

#### Layout modes

**List (vertical, single column):**
```
[Card]
[Card]
[Card]
...
```

**List (horizontal, single row):**
```
← Horizontal scroll →
[Card] [Card] [Card] [Card] [Card]
```

**Grid (vertical, X columns):**
```
[Card] [Card] [Card]
[Card] [Card] [Card]
[Card] [Card] [Card]
...
```

**Grid (horizontal, X columns × Y rows):**
```
← Horizontal scroll →
┌─────────────┐  ┌─────────────┐
│ [Card] [Card]│  │ [Card] [Card]│
│ [Card] [Card]│  │ [Card] [Card]│
└─────────────┘  └─────────────┘
```

#### Pagination

**Pagination = false (infinite scroll):**
- Reaches bottom → auto-fetches next page
- No UI indicator
- Used by: Home feed, Search results, Library full views

**Pagination = true (batch boundaries):**
- Shows fixed batch (e.g. 20 items)
- Pagination controls at bottom: Previous / Page indicator / Next
- Tap Next → fetches next batch, replaces content
- Used by: Channel Videos tab, Channel Playlists tab

```
[Card]  batch 1 of 5
[Card]
[Card]

────────────────────────────────
[◀ Previous]  [ 1 / 5 ]  [Next ▶]
```

#### Usage examples

```kotlin
// Home Feed (infinite scroll, grid)
VideoContainer(
    layout = LayoutMode.Grid,
    scrollDirection = ScrollDirection.Vertical,
    columns = 3,
    pagination = false,
    cards = homeViewModel.uiState.sections.flatMap { it.cards }
) { card -> navigateToVideoDetail(card) }

// Library History Strip (horizontal, list)
VideoContainer(
    layout = LayoutMode.List,
    scrollDirection = ScrollDirection.Horizontal,
    pagination = false,
    cards = libraryViewModel.historyCards
) { card -> navigateToVideoDetail(card) }

// Search Results (mixed types, vertical list)
VideoContainer(
    layout = LayoutMode.List,
    scrollDirection = ScrollDirection.Vertical,
    pagination = false,
    cards = searchViewModel.results
) { card -> /* route based on card type */ }

// Channel Videos Tab (batch pagination, grid)
VideoContainer(
    layout = LayoutMode.Grid,
    scrollDirection = ScrollDirection.Vertical,
    columns = 3,
    pagination = true,
    pageSize = 20,
    cards = channelViewModel.currentPageCards
) { card -> navigateToVideoDetail(card) }
```

#### Empty / Loading states

| State | Display |
|-------|---------|
| Initial loading | Shimmer skeleton cards in layout pattern |
| Loading next batch | Shimmer placeholders at bottom |
| Error | "Couldn't load" with retry button |
| Empty | "No videos" / "No results" |
| No more pages | Pagination controls hide or show "End of results" |

---

### 11.2 Card System

#### Card (sealed interface — data only)

Card types hold **data only**, no rendering logic. Rendering lives in a single
top-level `@Composable` dispatcher (see "Rendering" below) rather than as a method
on each subtype. This keeps the model stable/equatable for Compose's recomposition
skipping and makes each card type trivially testable and previewable without a
Compose host — an abstract `Content()` method per subtype loses both properties.

```kotlin
sealed interface Card {
    val id: String
    val title: String
    val subtitle: String?
    val metadata: List<String>
}
```

#### Unified Card Dimensions

All cards share the same outer box. Only the thumbnail aspect ratio and inner layout differ.

```
┌──────────────────────────────────┐
│  ┌────────────────────────────┐  │
│  │                            │  │  ← Thumbnail area (60% of height)
│  │      Thumbnail Area        │  │
│  │                            │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │  Title (2 lines max)    ⋮  │  │  ← Text area (40% of height)
│  │  Meta line                 │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| **Width** | Full container width (list) or column width (grid) |
| **Height** | Fixed — thumbnail (60%) + text (40%) |
| **Rounded corners** | 8dp |
| **Padding** | 0dp inner, gap between cards handled by container |

#### Thumbnail aspect ratios per type

| Card type | Thumbnail ratio | Duration pill |
|-----------|----------------|---------------|
| **Video** | 16:9 | Yes, bottom-left |
| **Short** | 9:16 | No |
| **Playlist** | 16:9 | Yes (total duration), bottom-left |
| **Channel** | 1:1 (circle crop) | No |

#### Data classes

> **Stability note**: `video`, `short`, and `playlist` below hold references to
> engine types (`IPlatformVideo`, `Playlist`) from Bluejay-core. If those types
> aren't `@Immutable`/`@Stable` (or plain `data class`es with stable fields),
> Compose can't skip recomposition for a card even when nothing displayed on it
> changed — which undercuts the "uniform card dimensions, 3-col grid, infinite
> scroll" performance goal in §14. Confirm their stability during Phase 0's engine
> audit (see ARCHITECTURE.md §17); if unstable, consider holding only the display
> fields these UI cards need rather than the whole engine object.

```kotlin
data class VideoCard(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val metadata: List<String>,
    val channelName: String,
    val viewCount: String,
    val postedAt: String,
    val duration: String?,
    val thumbnailUrl: String?,
    val video: IPlatformVideo
) : Card

data class ShortCard(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val metadata: List<String>,
    val viewCount: String,
    val duration: String?,
    val thumbnailUrl: String?,
    val short: IPlatformVideo
) : Card

data class PlaylistCard(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val metadata: List<String>,
    val videoCount: Int,
    val totalDuration: String,
    val thumbnailUrl: String?,
    val playlist: Playlist
) : Card

data class ChannelCard(
    override val id: String,
    override val title: String,
    override val subtitle: String?,
    override val metadata: List<String>,
    val subscribers: String,
    val thumbnailUrl: String?
) : Card
```

#### Rendering

A single top-level `@Composable` dispatches on card type — no per-subtype method
required, and each branch is independently previewable:

```kotlin
@Composable
fun CardContent(
    card: Card,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMore: () -> Unit
) {
    when (card) {
        is VideoCard -> CardContainer(
            modifier = modifier,
            thumbnail = {
                Box {
                    AsyncImage(model = card.thumbnailUrl)
                    card.duration?.let { Text(it, /* duration pill */) }
                }
            },
            title = card.title,
            metadata = listOf("${card.channelName} • ${card.viewCount} • ${card.postedAt}"),
            onClick = onClick,
            onMore = onMore
        )

        is ShortCard -> CardContainer(
            modifier = modifier,
            thumbnail = { AsyncImage(model = card.thumbnailUrl) },
            title = card.title,
            metadata = listOf(card.viewCount),
            showMore = false,
            onClick = onClick,
            onMore = onMore
        )

        is PlaylistCard -> CardContainer(
            modifier = modifier,
            thumbnail = {
                Box {
                    AsyncImage(model = card.thumbnailUrl)
                    Text(card.totalDuration, /* duration pill */)
                }
            },
            title = card.title,
            metadata = listOf("${card.videoCount} videos • ${card.totalDuration}"),
            onClick = onClick,
            onMore = onMore
        )

        is ChannelCard -> CardContainer(
            modifier = modifier,
            thumbnail = {
                AsyncImage(
                    model = card.thumbnailUrl,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            },
            title = card.title,
            metadata = listOf("${card.subscribers} subscribers"),
            showMore = false,
            onClick = onClick,
            onMore = onMore
        )
    }
}
```

---

### 11.3 ModalBottomSheet

Vertical list of buttons for actions.

```
┌─────────────────────────────────────────┐
│                                         │
│  [Play Next in Queue]                   │
│  [Watch Later]                          │
│  [Download]                             │
│  [Share]                                │
│  [Add to Playlist]                      │
│                                         │
│  ─────────────────────────────────      │
│  [Cancel]                               │
│                                         │
└─────────────────────────────────────────┘
```

| Usage | Entries |
|-------|---------|
| **Video card ⋮** | Play Next in Queue, Watch Later, Download, Share, Add to Playlist |
| **Video detail ⋮** | Add to Playlist, Add to Favorites, Download, PiP |
| **Playlist card ⋮** | Edit, Rename, Delete, Share, Clear playlist |

---

### 11.4 TabRow

Simple tab bar with indicator.

```
┌─ [Videos]  [Shorts]  [Playlists]  [About] ──────────────────┐
```

- Underlined indicator for selected tab
- Horizontal scroll if tabs overflow
- Used in: Video Detail (Comments/Recommended), Channel Detail (Videos/Shorts/Playlists/About), Library (Artists/Albums/Playlists)

---

### 11.5 SectionHeader

```
┌─ History  >  ──────────────────────────────────────────────┐
```

- Title text + ">" chevron on right
- Tap chevron → opens full view
- Used in: Library tab strips

---

### 11.6 EmptyState

```
┌─────────────────────────────────────────┐
│                                         │
│  🔔                                     │
│                                         │
│  No notifications yet                     │
│  When channels you follow post or go    │
│  live, you'll see them here.            │
│                                         │
└─────────────────────────────────────────┘
```

- Icon (top)
- Message (center)
- Optional action button

Used in: Search (no results), Notifications (empty), Library (no history)

---

### 11.7 LoadingSkeleton

Shimmer placeholder matching the layout pattern of the target content.

- Grid layout → shimmer grid cells
- List layout → shimmer full-width bars
- Used everywhere content loads

---

### 11.8 Comment

```
┌─ Comment ──────────────────────────────┐
│  username  •  2h ago                    │
│  👍 42   💬 Reply   📋 Copy             │
│                                         │
│  Comment text goes here...              │
│                                         │
│  3 replies ▼                            │
└─────────────────────────────────────────┘
```

- Username + relative time header
- Like count, reply button, copy button
- Comment text body
- Expandable replies

---

### 11.9 ChannelHero

```
┌─────────────────────────────────────────┐
│  [Channel Banner Image - full, 120dp]   │
│  ┌────┐                                 │
│  │    │  Channel Name                    │
│  │Avtr│  125K subscribers                │
│  │    │  [Subscribe]                     │
│  └────┘                                 │
└─────────────────────────────────────────┘
```

- Banner: full-width, 120dp
- Avatar: 56dp circle, overlaps banner
- Channel name, sub count, subscribe button

---

### 11.10 DescriptionExpandable

```
┌─ Description ─────────────────────────┐
│  First 2-3 lines of text...            │
│  [Show more ▼]                         │
└─────────────────────────────────────────┘

── Expanded ──

┌─ Description ─────────────────────────┐
│  Full description text...              │
│  Link: example.com                     │
│  Follow: @handle                       │
│  [Show less ▲]                         │
└─────────────────────────────────────────┘
```

---

### 11.11 PaginationControls

```
[◀ Previous]  [ 1 / 5 ]  [Next ▶]
```

- Previous: disabled on page 1
- Page indicator: current / total
- Next: disabled on last page

---

### 11.12 SettingsRow

```
┌─ Theme Mode          [Auto ▾] ──────────────────────┐
```

- Label + control (dropdown, toggle, switch, or link)
- Used in: Settings screen

---

### 11.13 NotificationCard

```
┌─ Notification ─────────────────────────────────────┐
│  [Avatar]  Channel Name                             │
│  uploaded a video                                   │
│  2h ago                                             │
│  [VideoCard - compact preview]                      │
│  [🔕 Mute]  [Dismiss]                              │
└─────────────────────────────────────────────────────┘
```

---

### 11.14 CreatorAvatar

```
┌────┐
│    │  ← 48dp circle (selected: 52dp with accent ring)
│    │
└────┘
```

- Circular avatar with optional source label
- Selection state with accent ring
- Has-new-content dot indicator

Used in: Subscriptions tab (horizontal portrait / vertical right landscape)

---

## 12. Companion Screen (Secondary Display)

```
┌─────────────────────────────────────────┐
│  [← Back to main]                       │
├─────────────────────────────────────────┤
│                                         │
│  ┌─ Player Controls ─────────────────┐  │
│  │  [Thumbnail]  Video Title          │  │
│  │  [Channel Name]                    │  │
│  │  ━━━━━━━━━━━━━━━━━━━○  3:42/5:00  │  │
│  │  [⏮]  [⏯]  [⏭]                   │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Tabs ────────────────────────────┐  │
│  │  [Recs]  [Comments]  [Polycentric] │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Recs Tab ────────────────────────┐  │
│  │  [CompactVideoCard]                │  │
│  │  [CompactVideoCard]                │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Comments Tab ────────────────────┐  │
│  │  [Comment card]                    │  │
│  │  [Comment card]                    │  │
│  └─────────────────────────────────────┘  │
│                                         │
│  ┌─ Polycentric Tab ─────────────────┐  │
│  │  [Post card]                       │  │
│  │  [Post card]                       │  │
│  └─────────────────────────────────────┘  │
│                                         │
└─────────────────────────────────────────┘
```

- Player controls: thumbnail, title, channel, progress bar, playback controls
- Three tabs: Recs, Comments, Polycentric
- Compact video cards for recommendations

---

## 13. Relative Time Format

| Time elapsed | Display |
|-------------|---------|
| < 60 min | `45m ago` |
| 1–23 hours | `3h ago` |
| 1–30 days | `12d ago` |
| 1–11 months | `3mo ago` |
| 1+ years | `1y ago` |

---

## 14. Summary of Design Decisions

| Decision | Rationale |
|----------|-----------|
| 6-tab nav chrome | Home, Search, Subs, Library, Notifications, Settings |
| No top bars on main tabs | Maximum content area on small screen |
| Landscape = 3-col grid | Efficient use of 1600px width |
| Portrait = single column | Standard feed experience |
| Uniform card dimensions | Mixed-content grids, consistent layout |
| Polymorphic Card system | Type-agnostic container, flexible composition |
| Pagination toggle | Feeds auto-load, channels/browsing use batches |
| Right-side creator strip (landscape) | Separate browsing (avatars) from content (videos) |
| Gesture controls on player | Native feel, no extra UI clutter |
| Companion has its own tabs | Minimal but useful on secondary screen |
| Material You dynamic color | Adaptive theming |
| Inter font + Material Symbols | Consistent typography/iconography |
