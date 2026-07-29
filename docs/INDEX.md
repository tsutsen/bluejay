# Bluejay Documentation Index

> **For agents:** Use this index to find the right doc without reading them all.
> Each entry has a 1-line description and a "read when" trigger.

---

## Project Root (read these first)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`README.md`](../README.md) | Project overview, build instructions | Starting any session |
| [`ARCHITECTURE.md`](../ARCHITECTURE.md) | Module map, dependency graph, tech stack, file counts | Adding modules, understanding where code lives |
| [`CONVENTIONS.md`](../CONVENTIONS.md) | Naming rules, directory layout, coding standards | Creating new files, code review |
| [`DESIGN.md`](../DESIGN.md) | UI/UX design spec — all screens, components, layouts | Building or modifying any UI screen |
| [`CONTRIBUTION.md`](../CONTRIBUTION.md) | How to contribute to the project | First-time contributors |

---

## Compose & Architecture (`docs/`)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`compose-architecture.md`](compose-architecture.md) | Compose-first architecture, MVVM patterns, navigation3, creating screens | Adding a new Compose screen, state management questions |
| [`data-pagers.md`](data-pagers.md) | Pager API for paginated data (ContentPager, ChannelPager, etc.) | Working with paginated feeds, plugin data loading |
| [`refactoring-opportunities.md`](refactoring-opportunities.md) | Gap analysis vs Google's architecture-samples | Planning refactors, architecture decisions |
| [`video-playback-troubleshooting.md`](video-playback-troubleshooting.md) | Resolved investigation: UMP/Sabr streaming support | Debugging video playback issues |

---

## Features — Player (`docs/features/player/`)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`player-morph-overhaul-plan.md`](features/player/player-morph-overhaul-plan.md) | High-level plan for smooth player morph transitions | Planning player UI work |
| [`morph-transition-polish.md`](features/player/morph-transition-polish.md) | Diagnosed bugs, 8 architectural rules, phased plan | Fixing morph pop-in, alpha snap, gesture conflicts |
| [`phase-1-helpers-config.md`](features/player/phase-1-helpers-config.md) | Phase 1: PlayerMorphConfig + pure helper functions | Implementing Phase 1 |
| [`phase-2-alpha-source.md`](features/player/phase-2-alpha-source.md) | Phase 2: Single source of truth for alpha values | Implementing Phase 2 |
| [`phase-3-animated-visibility.md`](features/player/phase-3-animated-visibility.md) | Phase 3: AnimatedVisibility + composition audit | Implementing Phase 3 |
| [`phase-4-code-org.md`](features/player/phase-4-code-org.md) | Phase 4: Extract PlayerView state holders | Implementing Phase 4 |
| [`phase-5-gesture-unification.md`](features/player/phase-5-gesture-unification.md) | Phase 5: One gesture recognizer per region | Implementing Phase 5 |
| [`gesture-system-handoff.md`](features/player/gesture-system-handoff.md) | Gesture system redesign design doc (from prior session) | Working on gesture handling |
| [`casting.md`](features/player/casting.md) | Cast/Chromecast support in the player | Working on casting features |

---

## Features — Other (`docs/features/`)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`subscriptions-tab.md`](features/subscriptions-tab.md) | Subscriptions tab implementation details, filter logic | Working on subscriptions screen |
| [`authentication-plugin-login.md`](features/authentication-plugin-login.md) | Plugin authentication system — login flow, header/cookie behavior | Implementing plugin auth, debugging login |

---

## Plugins (`docs/plugins/`)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`plugin-development.md`](plugins/plugin-development.md) | Complete plugin development guide — config, lifecycle, API | Writing a new plugin |
| [`example-plugin.md`](plugins/example-plugin.md) | Minimal plugin template / starting point | Bootstrapping a plugin |
| [`http-package.md`](plugins/http-package.md) | Http package — web requests, websockets, auth clients | Making network requests from plugins |
| [`script-signing.md`](plugins/script-signing.md) | Plugin script signing for update verification | Publishing plugin updates |
| [`profile-linking.md`](plugins/profile-linking.md) | Linking user profiles with Harbor on Polycentric | Profile linking features |

---

## Integrations (`docs/integrations/`)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`content-types.md`](integrations/content-types.md) | Supported content types (video, audio, text, images, articles) | Adding new content type support |
| [`polycentric.md`](integrations/polycentric.md) | Polycentric for comments and ratings — why and how | Working on social features, comments |

---

## References (`docs/references/`)

| Doc | What it covers | Read when |
|-----|----------------|-----------|
| [`material-icons-codepoints.md`](references/material-icons-codepoints.md) | Material Symbols Rounded icon codepoint reference | Adding custom icons |

---

## Cross-Reference Map

**"I'm working on X, which docs do I need?"**

| Task | Primary doc | Also read |
|------|-------------|-----------|
| Add a new screen | `compose-architecture.md` | `CONVENTIONS.md`, `DESIGN.md` |
| Modify player UI | `morph-transition-polish.md` | `DESIGN.md §10` |
| Write a plugin | `plugin-development.md` | `example-plugin.md` |
| Fix video playback | `video-playback-troubleshooting.md` | — |
| Work on navigation | `compose-architecture.md` | `ARCHITECTURE.md §8` |
| Add authentication | `authentication-plugin-login.md` | `http-package.md` |
| Modify subscriptions | `subscriptions-tab.md` | `DESIGN.md §4` |
| Work on gestures | `gesture-system-handoff.md` | `morph-transition-polish.md §5` |
| Add new content type | `content-types.md` | `CONVENTIONS.md` |
| Refactor architecture | `refactoring-opportunities.md` | `CONVENTIONS.md §4` |

---

## Naming Convention

All docs use **kebab-case** filenames. No spaces, no PascalCase, no ALL_CAPS.

| Category | Location |
|----------|----------|
| Project-wide | Root: `ARCHITECTURE.md`, `CONVENTIONS.md`, `DESIGN.md` |
| Compose/Architecture | `docs/*.md` |
| Feature-specific | `docs/features/<area>/*.md` |
| Plugin dev | `docs/plugins/*.md` |
| External integrations | `docs/integrations/*.md` |
| Reference data | `docs/references/*.md` |
