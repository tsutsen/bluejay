# Deep theming — investigation

Goal: let users theme surfaces themselves — custom colors, outlines, textures
for dock, buttons, tiles, video cards, settings rows — plus optional filters
over video thumbnails (color overlay / shader), and maybe custom app-icon sets
and fonts. This is an investigation only; nothing here is implemented yet.

## What already exists (and is reusable as-is)

- **Theme engine**: `core/designsystem/.../theme/ThemeEngine.kt` generates a
  full 40-role M3 `ColorScheme` from key colors (material-color-utilities,
  TONAL_SPOT/VIBRANT/EXPRESSIVE/FRUIT_SALAD styles, contrast slider). Editor
  lives in `ThemeSettings.kt`; themes persist in `Settings.AppearanceSettings`
  and re-compose the whole tree live (MainActivity → BluejayTheme). No restart.
- **Tokens**: `BluejayTokens` + `LocalBluejayTokens` (radius scale driven by the
  "UI rounding" slider) — every component reads `BluejayTokens().radius.*`
  instead of `MaterialTheme.shapes`. This is the natural home for outline-width
  and texture tokens too.
- **Second color seam**: `LocalSemanticColors` (warning pair) — precedent for
  adding `LocalSurfaceSkin`-style composition locals.
- **Fonts**: the full Inter family is already bundled in `app/res/font/` but
  unwired (`BluejayTypography` still uses `FontFamily.Default`). Dead
  scaffolding exists: `AppearancePreferences.fontFamily` (DEFAULT/INTER/…),
  `fontSizeScale`, `IconStyle` (ROUNDED/SHARP/OUTLINED) — stored but read by
  nothing.

## Gaps (mapped)

1. **No per-surface token layer.** Components read `MaterialTheme.colorScheme`
   directly (surface/surfaceContainer/primaryContainer/...). A "surface skin"
   layer would slot in cleanly: add a `LocalSurfaceSkin` providing a
   `SurfaceSkin(color overrides, outline width/color, texture descriptor)` per
   logical surface key (dock, tile, videoCard, settingsRow, button...), and
   route the shared components through it. Only ~7 hardcoded colors exist
   (VideoCard ClipBadge, QueueStrip/QueueList dark plates, subtitle backdrop,
   gesture editor) — small migration.
2. **No texture/pipeline.** Zero `RenderEffect`/`ColorFilter`/`BlendMode` usage
   today. Textures = tiled `ImageBitmap` brush or shader `Brush.linearShader`
   (AGSL, API 33+) behind the skin descriptor; `RenderEffect` only where a
   layer exists already.
3. **Thumbnails greenfield.** Two seams: shared `core/ui/AsyncImage.kt`
   wrapper (no filter params today) and the app `ImageLoaderFactory`
   (`PlatformPlayerApp`). Per-user thumbnail filter: add an optional
   `ColorFilter`/`Modifier.graphicsLayer { renderEffect }` param to the
   wrapper and feed it from a settings-backed composition local; a global
   Coil `Interceptor`/transformation covers every image without touching call
   sites. AGSL RuntimeShader overlay needs API 33+ (fallback: ColorMatrix).
4. **Icons static.** Single adaptive icon, no `activity-alias`. Icon sets =
   N aliases + an enable/disable switch; the launcher rereads aliases only on
   next launcher refresh.
5. **Color picker limited.** Preset swatch grid only; needs hex/HSL entry for
   user-provided palettes.

## Suggested phasing

1. Wire the dead font prefs (fontFamily + fontSizeScale → BluejayTypography)
   — cheapest visible win, plumbing exists.
2. `LocalSurfaceSkin` tokens (color + outline width/color per surface key) +
   migrate shared components (SettingsCards, VideoCard/CardContainer, dock,
   buttons) + an Appearance "Surface styles" editor.
3. Textures: descriptor (res id / tile mode / alpha) in the skin, drawn as a
   brush behind the color.
4. Thumbnail filters: global Coil interceptor (color matrix / AGSL) + preview
   picker in Appearance.
5. Icon sets via `activity-alias` (last, least value).

## Risks

- Skin overrides interact with dynamic Material You + custom themes: define
  precedence explicitly (user skin > custom theme > Material You > default).
- AGSL shaders are API 33+; the AYN Thor is fine, older devices need a
  ColorMatrix fallback path.
- Per-surface overrides multiply the theme matrix; keep the editor
  surface-keyed (6–8 keys), not per-component.
