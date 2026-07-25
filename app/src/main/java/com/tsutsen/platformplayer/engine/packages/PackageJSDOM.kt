package com.tsutsen.platformplayer.engine.packages

import com.tsutsen.platformplayer.engine.IV8PluginConfig
import com.tsutsen.platformplayer.engine.V8Plugin
import com.tsutsen.platformplayer.states.StateApp


class PackageJSDOM : V8Package {
    @Transient
    private val _config: IV8PluginConfig;

    override val name: String get() = "JSDOM";
    override val variableName: String get() = "packageJSDOM";

    constructor(plugin: V8Plugin, config: IV8PluginConfig): super(plugin) {
        _config = config;
        plugin.withDependency(StateApp.instance.contextOrNull ?: return, "scripts/JSDOM.js");
    }

}