package com.tsutsen.platformplayer.gettingstarted

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.compose.plugins.PluginDetailScene
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.groupShape
import com.tsutsen.platformplayer.core.designsystem.icon.DualScreen
import com.tsutsen.platformplayer.core.designsystem.theme.BluejayTokens
import com.tsutsen.platformplayer.core.designsystem.theme.Tokens
import com.tsutsen.platformplayer.core.model.PlayerControllerActions
import com.tsutsen.platformplayer.feature.settings.impl.ControllerBindingPopup
import com.tsutsen.platformplayer.states.StatePlatform
import com.tsutsen.platformplayer.states.StatePlugins
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-launch getting-started flow: welcome → sources → dual screen →
 * controller → done. Mounted full-screen by the app shell while
 * [AppPreferences.gettingStartedCompleted] is false; finishing or skipping
 * persists the flag, so the tour shows only once.
 *
 * Every choice persists live (except the source selection, which applies on
 * "Next" because enabling clients initialises their engines). Tapping a
 * source card opens that plugin's settings (the same detail scene the
 * plugin browser uses) without leaving the flow.
 */
@Composable
fun GettingStartedFlow(
    preferences: AppPreferences,
    settingsRepository: SettingsRepository,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // Step 1 — sources
    var sources by remember { mutableStateOf(emptyList<IPlatformClient>()) }
    var checkedSources by remember {
        mutableStateOf(StatePlatform.instance.getEnabledClients().map { it.id }.toSet())
    }
    var selectedSourceUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(step) {
        if (step == 1 && sources.isEmpty()) {
            // Built-in plugins load shortly after start; poll briefly.
            var waited = 0
            while (sources.isEmpty() && waited < 40) {
                sources = StatePlatform.instance.getAvailableClients()
                if (sources.isEmpty()) {
                    delay(500)
                    waited++
                }
            }
        }
    }

    // Step 2 — dual screen
    var dualScreen by remember { mutableStateOf(preferences.dualScreen) }
    var dualPages by remember { mutableStateOf(preferences.dualScreenPages.toSet()) }

    // Step 3 — controller
    var controllerEnabled by remember { mutableStateOf(preferences.controller.enabled) }
    var mappings by remember { mutableStateOf(preferences.controller.mappings.toMap()) }
    var bindingAction by remember { mutableStateOf<PlayerControllerActions.Action?>(null) }

    // Expressive step transition: fade + slide on each step change.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(step) {
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        // Step content: centered in a box (so it scrolls instead of
        // clipping when a step is longer than the viewport).
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = Tokens.SpaceXl,
                        end = Tokens.SpaceXl,
                        // Clear the progress dots up top and leave room
                        // above the headline on every step.
                        top = Tokens.SpaceXl * 2,
                        // Keep content clear of the bottom dock.
                        bottom = Tokens.SpaceXl * 4,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer {
                            alpha = entrance.value
                            translationY = (1f - entrance.value) * 24f
                        },
            ) {
            key(step) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> SourcesStep(
                            sources = sources,
                            checked = checkedSources,
                            onToggle = { id ->
                                checkedSources =
                                    if (id in checkedSources) checkedSources - id
                                    else checkedSources + id
                            },
                            onOpenSettings = { url -> selectedSourceUrl = url },
                        )
                    2 -> DualScreenStep(
                            enabled = dualScreen,
                            pages = dualPages,
                            onEnabledChange = { enabled ->
                                dualScreen = enabled
                                scope.launch { settingsRepository.updateGeneral("dualScreen", enabled) }
                            },
                            onPagesChange = { pages ->
                                dualPages = pages
                                scope.launch { settingsRepository.updateDualScreenPages(pages.toList()) }
                            },
                        )
                    3 -> ControllerStep(
                            enabled = controllerEnabled,
                            mappings = mappings,
                            onEnabledChange = { enabled ->
                                controllerEnabled = enabled
                                scope.launch {
                                    settingsRepository.updateControllerSettings(
                                        ControllerPreferences(
                                            enabled = enabled,
                                            mappings = mappings,
                                            seekBackSeconds = preferences.controller.seekBackSeconds,
                                            seekForwardSeconds = preferences.controller.seekForwardSeconds,
                                        )
                                    )
                                }
                            },
                            onRemap = { action -> bindingAction = action },
                        )
                    4 -> DoneStep()
                    }
                }
            }
        }

        // Bottom dock: back / next (or skip / first setup / done) on every step.
        FlowDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            back =
                when (step) {
                    0 -> Pair("Skip setup", onFinished)
                    4 -> null
                    else -> Pair("Back", { step-- })
                },
            primary =
                when (step) {
                    0 -> Pair("First setup", { step = 1 })
                    4 -> Pair("Done", onFinished)
                    else ->
                        Pair("Next", {
                            scope.launch {
                                if (step == 1) {
                                    StatePlatform.instance.selectClients(*(checkedSources.toTypedArray()))
                                }
                                step++
                            }
                        })
                },
        )

        // Progress dots (steps 1..4)
        if (step in 1..4) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Tokens.SpaceLg),
                horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceXs),
            ) {
                repeat(4) { i ->
                    Dot(active = i < step || step == 4)
                }
            }
        }
    }

    // A source card's tap: the plugin's settings, hosted in the overlay so
    // the flow stays alive underneath (same scene the plugin browser uses).
    selectedSourceUrl?.let { url ->
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PluginDetailScene(
                configUrl = url,
                installedPlugins = sources.filterIsInstance<JSClient>().map { it.config },
                onBack = {
                    selectedSourceUrl = null
                    // The detail scene can toggle the plugin's enable state —
                    // resync the flow's selection so "Next" doesn't clobber it.
                    checkedSources = StatePlatform.instance.getEnabledClients().map { it.id }.toSet()
                },
            )
        }
    }

    // Controller key capture (reuses the Settings capture popup)
    bindingAction?.let { action ->
        ControllerBindingPopup(
            action = action,
            onBound = { keyCode, deviceName ->
                val newMappings =
                    mappings + (action.id to ControllerBinding(keyCode = keyCode, deviceName = deviceName))
                mappings = newMappings
                scope.launch {
                    settingsRepository.updateControllerSettings(
                        ControllerPreferences(
                            enabled = controllerEnabled,
                            mappings = newMappings,
                            seekBackSeconds = preferences.controller.seekBackSeconds,
                            seekForwardSeconds = preferences.controller.seekForwardSeconds,
                        )
                    )
                }
                bindingAction = null
            },
            onClear = {
                val newMappings = mappings - action.id
                mappings = newMappings
                scope.launch {
                    settingsRepository.updateControllerSettings(
                        ControllerPreferences(
                            enabled = controllerEnabled,
                            mappings = newMappings,
                            seekBackSeconds = preferences.controller.seekBackSeconds,
                            seekForwardSeconds = preferences.controller.seekForwardSeconds,
                        )
                    )
                }
                bindingAction = null
            },
            onDismiss = { bindingAction = null },
        )
    }
}

/** Bottom action dock: a surface bar with the secondary action left, primary right. */
@Composable
private fun FlowDock(
    back: Pair<String, () -> Unit>?,
    primary: Pair<String, () -> Unit>,
    modifier: Modifier = Modifier,
) {
    val radius = BluejayTokens().radius.lg
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    RoundedCornerShape(topStart = radius, topEnd = radius),
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.SpaceXl, vertical = Tokens.SpaceMd),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            back?.let {
                TextButton(onClick = it.second) { Text(it.first) }
                Spacer(Modifier.weight(1f))
            }
            Button(onClick = primary.second) {
                Text(primary.first)
            }
        }
    }
}

/** Step header: the step icon alongside the title, not above it. */
@Composable
private fun StepHeader(icon: ImageVector, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconMd),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun Dot(active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(Tokens.SpaceSm)
                .background(
                    shape = RoundedCornerShape(50),
                    color =
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                ),
    )
}

@Composable
private fun WelcomeStep(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The real launcher icon, rendered from the package manager.
    val launcherIcon =
        remember {
            val drawable: android.graphics.drawable.Drawable =
                context.packageManager.getApplicationIcon(context.packageName)
            val sizePx = 96
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = launcherIcon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(Tokens.AvatarXl)
                    .clip(RoundedCornerShape(BluejayTokens().radius.sm)),
        )
        Spacer(Modifier.height(Tokens.SpaceLg))
        Text(
            "Welcome to Bluejay the media app!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Sources as a grid of cards (icon, name, description, enable checkbox).
 * Tapping a card opens that plugin's settings.
 */
@Composable
private fun SourcesStep(
    sources: List<IPlatformClient>,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StepHeader(Icons.Default.Extension, "Choose your sources")
        Spacer(Modifier.height(Tokens.SpaceSm))
        Text(
            "Pick the sources you want to watch. You can change this later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tokens.SpaceLg))
        if (sources.isEmpty()) {
            Text(
                "No sources available yet — you can continue and enable them later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceMd)) {
                sources.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
                    ) {
                        row.forEach { source ->
                            SourceCard(
                                source = source,
                                checked = source.id in checked,
                                onToggle = { onToggle(source.id) },
                                onOpenSettings = {
                                    (source as? JSClient)
                                        ?.config
                                        ?.sourceUrl
                                        ?.let(onOpenSettings)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: IPlatformClient,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    FlowCard(
        icon = Icons.Default.Extension,
        title = source.name,
        subtitle = (source as? JSClient)?.config?.description,
        iconUrl = StatePlugins.instance.getPluginIconUriOrNull(source.id),
        onClick = onOpenSettings,
        modifier = modifier,
        trailing = {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            // Settings cards' language: the chevron marks a card you can open.
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * The flow's option card: the settings cards' visual language (icon tile,
 * title, subtitle) with a caller-provided trailing control and optional
 * card-level tap.
 */
@Composable
private fun FlowCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconUrl: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    groupPosition: GroupPosition = GroupPosition.Single,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = groupShape(groupPosition),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Tokens.SpaceMd),
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(Tokens.AvatarMd)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(BluejayTokens().radius.sm),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (iconUrl != null) {
                    AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.small),
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Tokens.IconMd),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.let { it() }
        }
    }
}

@Composable
private fun DualScreenStep(
    enabled: Boolean,
    pages: Set<String>,
    onEnabledChange: (Boolean) -> Unit,
    onPagesChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StepHeader(Icons.Outlined.StayCurrentPortrait, "Enable dual screen mode?")
        Spacer(Modifier.height(Tokens.SpaceSm))
        Text(
            "If your device has a second screen, this app can show additional controls and info there!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        FlowCard(
            icon = DualScreen,
            title = "Dual screen",
            subtitle = "Show a second display",
            groupPosition =
                if (enabled) GroupPosition.First else GroupPosition.Single,
            trailing = {
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            },
        )
        if (enabled) {
            Spacer(Modifier.height(Tokens.SpaceSm))
            Text(
                "Pages to show on the second screen:",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Tokens.SpaceSm))
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                dualPageIcons.forEach { (id, page) ->
                    FlowCard(
                        icon = page.first,
                        title = page.second,
                        groupPosition =
                            GroupPosition.fromIndex(
                                dualPageIcons.keys.indexOf(id),
                                dualPageIcons.size,
                            ),
                        trailing = {
                            Switch(
                                checked = id in pages,
                                onCheckedChange = {
                                    onPagesChange(
                                        if (id in pages) pages - id else pages + id
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControllerStep(
    enabled: Boolean,
    mappings: Map<String, ControllerBinding>,
    onEnabledChange: (Boolean) -> Unit,
    onRemap: (PlayerControllerActions.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StepHeader(Icons.Outlined.SportsEsports, "Enable controller support?")
        Spacer(Modifier.height(Tokens.SpaceSm))
        Text(
            "Control playback with a gamepad or TV remote.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        FlowCard(
            icon = Icons.Outlined.SportsEsports,
            title = "Controller",
            subtitle = "Use a gamepad or TV remote",
            groupPosition =
                if (enabled) GroupPosition.First else GroupPosition.Single,
            trailing = {
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            },
        )
        if (enabled) {
            Spacer(Modifier.height(Tokens.SpaceSm))
            Text(
                "Default key mappings:",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Tokens.SpaceSm))
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpaceXs)) {
                PlayerControllerActions.ALL.forEachIndexed { index, action ->
                    val bound = mappings[action.id]
                    val keyLabel =
                        PlayerControllerActions.labelFor(bound?.keyCode ?: action.defaultKeyCode)
                    FlowCard(
                        icon = Icons.Filled.Keyboard,
                        title = action.label,
                        groupPosition = GroupPosition.fromIndex(index, PlayerControllerActions.ALL.size),
                        trailing = {
                            Text(
                                keyLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Tokens.SpaceSm))
                            TextButton(onClick = { onRemap(action) }) { Text("Remap") }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(Tokens.AvatarXl),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Tokens.SpaceLg))
        Text(
            "All set up!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        Text(
            "You can log into your sources and start browsing.\nAnd do take a look on the settings tab, there are many more things to customize!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val dualPageIcons: Map<String, Pair<ImageVector, String>> =
    mapOf(
        "video" to (Icons.Filled.PlayArrow to "Video page"),
        "library" to (Icons.Filled.QueueMusic to "Library page"),
        "home" to (Icons.Filled.Home to "Home page"),
        "dash" to (Icons.Filled.Dashboard to "Dash page"),
    )
