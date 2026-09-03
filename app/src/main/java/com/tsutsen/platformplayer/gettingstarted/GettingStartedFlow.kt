package com.tsutsen.platformplayer.gettingstarted

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.core.data.repository.SettingsRepository
import com.tsutsen.platformplayer.core.datastore.model.AppPreferences
import com.tsutsen.platformplayer.core.datastore.model.ControllerBinding
import com.tsutsen.platformplayer.core.datastore.model.ControllerPreferences
import com.tsutsen.platformplayer.core.designsystem.component.GroupPosition
import com.tsutsen.platformplayer.core.designsystem.component.SettingsSwitchOptionCard
import com.tsutsen.platformplayer.core.designsystem.component.rememberIsWide
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
 * "Next" because enabling clients initialises their engines).
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
        // clipping when a step is longer than the viewport), capped at
        // half the width on wide screens.
        val wide = rememberIsWide()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = Tokens.SpaceXl,
                        end = Tokens.SpaceXl,
                        top = Tokens.SpaceXl,
                        // Keep content clear of the bottom dock.
                        bottom = Tokens.SpaceXl * 3,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(if (wide) 0.5f else 1f)
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
                    0 -> Pair("Skip tour and accept defaults", onFinished)
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (back != null) {
                TextButton(onClick = back.second) { Text(back.first) }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Button(onClick = primary.second, modifier = Modifier.weight(2f)) {
                Text(primary.first)
            }
        }
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
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(Tokens.AvatarXl),
            tint = MaterialTheme.colorScheme.primary,
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

/** Same cards as the settings plugins page: icon, name, switch. */
@Composable
private fun SourcesStep(
    sources: List<IPlatformClient>,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconMd),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        Text("Choose your sources", style = MaterialTheme.typography.headlineSmall)
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
            sources.forEachIndexed { index, source ->
                SettingsSwitchOptionCard(
                    icon = Icons.Default.Extension,
                    iconUrl = StatePlugins.instance.getPluginIconUriOrNull(source.id),
                    title = source.name,
                    subtitle = "",
                    checked = source.id in checked,
                    onCheckedChange = { onToggle(source.id) },
                    onClick = { onToggle(source.id) },
                    groupPosition = GroupPosition.fromIndex(index, sources.size),
                )
            }
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
        Icon(
            Icons.Outlined.StayCurrentPortrait,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconMd),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        Text("Enable dual screen mode?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Tokens.SpaceSm))
        Text(
            "Show the app on a second display, like a tablet or phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Dual screen",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            Spacer(Modifier.height(Tokens.SpaceMd))
            Text("Pages to show on the second screen:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Tokens.SpaceSm))
            dualPageNames.forEach { (id, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = id in pages,
                        onCheckedChange = {
                            onPagesChange(
                                if (id in pages) pages - id else pages + id
                            )
                        },
                    )
                    Spacer(Modifier.width(Tokens.SpaceXs))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
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
        Icon(
            Icons.Outlined.SportsEsports,
            contentDescription = null,
            modifier = Modifier.size(Tokens.IconMd),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        Text("Enable controller support?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Tokens.SpaceSm))
        Text(
            "Control playback with a gamepad or TV remote.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Tokens.SpaceMd))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Controller",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            Spacer(Modifier.height(Tokens.SpaceMd))
            Text("Default key mappings:", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Tokens.SpaceSm))
            PlayerControllerActions.ALL.forEach { action ->
                val bound = mappings[action.id]
                val keyLabel =
                    PlayerControllerActions.labelFor(bound?.keyCode ?: action.defaultKeyCode)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Tokens.SpaceXxs),
                ) {
                    Text(
                        action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        keyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Tokens.SpaceMd))
                    TextButton(onClick = { onRemap(action) }) { Text("Remap") }
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
            "You can log into your sources and start browsing.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val dualPageNames =
    mapOf(
        "video" to "Video page",
        "library" to "Library page",
        "home" to "Home page",
        "dash" to "Dash page",
    )
