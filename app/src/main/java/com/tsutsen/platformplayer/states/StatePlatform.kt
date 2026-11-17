package com.tsutsen.platformplayer.states

import android.content.Context
import androidx.collection.LruCache
import com.tsutsen.platformplayer.di.SettingsRepositoryImpl
import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.IPluginSourced
import com.tsutsen.platformplayer.api.media.PlatformMultiClientPool
import com.tsutsen.platformplayer.api.media.exceptions.NoPlatformClientException
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContentDetails
import com.tsutsen.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.tsutsen.platformplayer.api.media.models.video.IPlatformVideo
import com.tsutsen.platformplayer.api.media.platforms.js.DevJSClient
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginDescriptor
import com.tsutsen.platformplayer.api.media.platforms.local.LocalClient
import com.tsutsen.platformplayer.api.media.structures.EmptyPager
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.api.media.structures.MultiDistributionContentPager
import com.tsutsen.platformplayer.api.media.structures.PlaceholderPager
import com.tsutsen.platformplayer.api.media.structures.RefreshDistributionContentPager
import com.tsutsen.platformplayer.awaitFirstNotNullDeferred
import com.tsutsen.platformplayer.constructs.BatchedTaskHandler
import com.tsutsen.platformplayer.constructs.Event0
import com.tsutsen.platformplayer.constructs.Event1
import com.tsutsen.platformplayer.engine.exceptions.ScriptReloadRequiredException
import com.tsutsen.platformplayer.fromPool
import com.tsutsen.platformplayer.getNowDiffSeconds
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.models.ImageVariable
import com.tsutsen.platformplayer.stores.FragmentedStorage
import com.tsutsen.platformplayer.stores.StringArrayStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.Thread.sleep
import java.time.OffsetDateTime
import kotlin.streams.asSequence

/***
 * Used to interact with sources/clients
 */
class StatePlatform {
    // internal (not private): search/content extension files in this package log with it
    internal val TAG = "StatePlatform";
    private val VIDEO_CACHE = 100;

    // internal (not private): used by the content extensions in StatePlatformContent.kt
    internal val _scope = CoroutineScope(Dispatchers.IO);

    //Caches
    private data class CachedPlatformContent(val video: IPlatformContentDetails, val creationTime: OffsetDateTime = OffsetDateTime.now());
    private val _cacheExpirationSeconds = 60 * 5;
    private val _cache : LruCache<String, CachedPlatformContent> = LruCache<String, CachedPlatformContent>(VIDEO_CACHE);

    //Clients
    private val _localClient = LocalClient();
    private val _enabledClientsPersistent = FragmentedStorage.get<StringArrayStorage>("enabledClients");
    private val _platformOrderPersistent = FragmentedStorage.get<StringArrayStorage>("platformOrder");
    private val _clientsLock = Object();
    private val _selectClientsMutex = Mutex();
    private val _availableClients : ArrayList<IPlatformClient> = ArrayList();
    private val _enabledClients : ArrayList<IPlatformClient> = ArrayList();

    //Client pools are internal (not private): used by the content extensions in
    //StatePlatformContent.kt. They are immutable references; no behavioral change.
    //ClientPools are used to isolate plugin usage of certain components from others
    //This prevents for example a background task like subscriptions from blocking a user from opening a video
    //It also allows parallel usage of plugins that would otherwise be impossible.
    //Pools always follow the behavior of the base client. So if user disables a plugin, it kills all pooled clients.
    //Each pooled client adds additional memory usage.
    //WARNING: Be careful with pooling some calls, as they might use the plugin subsequently afterwards. For example pagers might block plugins in future calls.
    internal val _mainClientPool = PlatformMultiClientPool("Main", 2); //Used for all main user events, generally user critical
    internal val _pagerClientPool = PlatformMultiClientPool("Pagers", 2); //Used primarily for calls that result in front-end pagers, preventing them from blocking other calls.
    internal val _channelClientPool = PlatformMultiClientPool("Channels", 15); //Used primarily for subscription/background channel fetches
    internal val _trackerClientPool = PlatformMultiClientPool("Trackers", 1); //Used exclusively for playback trackers
    internal val _liveEventClientPool = PlatformMultiClientPool("LiveEvents", 1); //Used exclusively for live events
    internal val _privateClientPool = PlatformMultiClientPool("Private", 2, true); //Used primarily for calls if in incognito mode
    internal val _instantClientPool = PlatformMultiClientPool("Instant", 1, false, true); //Used for all instant calls


    private val _icons : HashMap<String, ImageVariable> = HashMap();
    private val _iconsByName : HashMap<String, ImageVariable> = HashMap();

    val hasClients: Boolean get() = _availableClients.size > 0;

    val onSourceDisabled = Event1<IPlatformClient>();

    /** Emitted after the set of enabled clients changes (selectClients). */
    val onEnabledClientsChanged = Event0();

    val onDevSourceChanged = Event0();

    //TODO: Remove after verifying that enabled clients are already in persistent order
    val platformOrder get() = _platformOrderPersistent.values.toList();

    //Batched Requests
    private val _batchTaskGetVideoDetails: BatchedTaskHandler<String, IPlatformContentDetails> = BatchedTaskHandler<String, IPlatformContentDetails>(_scope,
        { url ->

            Logger.i(StatePlatform::class.java.name, "Fetching video details [${url}]");
            if(!StateApp.instance.privateMode) {
                _enabledClients.find { _instantClientPool.getClientPooled(it).isContentDetailsUrl(url) }?.let {
                    _mainClientPool.getClientPooled(it).getContentDetails(url)
                }
                    ?: (if(_localClient.isContentDetailsUrl(url)) _localClient.getContentDetails(url) else null)
                    ?: throw NoPlatformClientException("No client enabled that supports this url ($url)");
            }
            else {
                Logger.i(TAG, "Fetching details with private client");
                _enabledClients.find { _instantClientPool.getClientPooled(it).isContentDetailsUrl(url) }?.let {
                    _privateClientPool.getClientPooled(it).getContentDetails(url)
                }
                    ?: (if(_localClient.isContentDetailsUrl(url)) _localClient.getContentDetails(url) else null)
                    ?: throw NoPlatformClientException("No client enabled that supports this url ($url)");
            }
        },
        {
            if(!SettingsRepositoryImpl.instance.preferences.value.browsing.videoCache || StateApp.instance.privateMode)
                return@BatchedTaskHandler null;
            else {
                val cached = synchronized(_cache) { _cache.get(it); } ?: return@BatchedTaskHandler null;
                Logger.i(TAG, "Video Cache Hit [${cached.video.name}]");
                if (cached.creationTime.getNowDiffSeconds() > _cacheExpirationSeconds) {
                    Logger.i(TAG, "Invalidated cache for [${it}]");
                    synchronized(_cache) {
                        _cache.remove(it);
                    }
                    return@BatchedTaskHandler null;
                }
                return@BatchedTaskHandler cached.video;
            }
        },
        { para, result ->
            if(!SettingsRepositoryImpl.instance.preferences.value.browsing.videoCache || (result is IPlatformVideo && result.isLive) || StateApp.instance.privateMode)
                return@BatchedTaskHandler
            else {
                Logger.i(TAG, "Caching [${para}]");
                if (result.datetime == null || result.datetime!! < OffsetDateTime.now())
                    synchronized(_cache) {
                        _cache.put(para, CachedPlatformContent(result))
                    }
            }
        });

    constructor() {
        onSourceDisabled.subscribe {
            synchronized(_cache) {
                for(item in _cache.snapshot()) {
                    if(item.value.video is IPluginSourced)
                        if(it.id == (item.value.video as IPluginSourced).sourceConfig.id) {
                            Logger.i(TAG, "Removing [${item.value.video.name}] from cache because plugin disabled");
                            _cache.remove(item.key);

                        }
                }
            }
        };
    }


    suspend fun updateAvailableClients(context: Context, reloadPlugins: Boolean = false) {
        if(reloadPlugins) {
            StatePlugins.instance.reloadPluginFile();
        }

        withContext(Dispatchers.IO) {
            val toDispose = mutableListOf<IPlatformClient>();
            var enabled: Array<String>;
            synchronized(_clientsLock) {
                val previousAvailable = _availableClients.toList();
                val reusableByDescriptor = HashMap<SourcePluginDescriptor, JSClient>();
                for (prev in previousAvailable) {
                    if (prev is JSClient)
                        reusableByDescriptor[prev.descriptor] = prev;
                }

                _enabledClients.clear();
                _availableClients.clear();

                _icons.clear();
                _iconsByName.clear()
                _icons[StateDeveloper.DEV_ID] = ImageVariable(null, android.R.drawable.ic_dialog_alert);

                StatePlugins.instance.updateEmbeddedPlugins(context);
                StatePlugins.instance.installMissingEmbeddedPlugins(context);

                for (plugin in StatePlugins.instance.getPlugins()) {
                    try {
                        val reused = reusableByDescriptor[plugin];
                        val isReused = reused != null && reused.descriptor === plugin;
                        val client: JSClient = if (isReused) {
                            reused!!;
                        } else {
                            JSClient(context, plugin).also { fresh ->
                                fresh.onCaptchaException.subscribe { c, ex ->
                                    StateApp.instance.handleCaptchaException(c, ex);
                                }
                            }
                        }

                        _icons[plugin.config.id] = StatePlugins.instance.getPluginIconOrNull(plugin.config.id) ?:
                                ImageVariable(plugin.config.absoluteIconUrl, null);
                        _iconsByName[plugin.config.name.lowercase()] = StatePlugins.instance.getPluginIconOrNull(plugin.config.id) ?:
                                ImageVariable(plugin.config.absoluteIconUrl, null);
                        _availableClients.add(client);

                        if (isReused)
                            reusableByDescriptor.remove(plugin);
                    }
                    catch(ex: Throwable) {
                        Logger.e(TAG, "Failed to initialize plugin [${plugin.config.name}]", ex);
                        StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                            UIDialogs.toast("Plugin ${plugin.config.name} failed to load\n${ex.message}");
                        }
                    }
                }

                toDispose.addAll(reusableByDescriptor.values);

                if(_availableClients.distinctBy { it.id }.count() < _availableClients.size) {
                    val dups = _availableClients.filter { x-> _availableClients.count { it.id == x.id } > 1 };
                    val overrideClients = _availableClients.distinctBy { it.id }
                    _availableClients.clear();
                    _availableClients.addAll(overrideClients);

                    StateApp.instance.scopeOrNull?.launch(Dispatchers.Main) {
                        UIDialogs.showDialog(context, android.R.drawable.ic_dialog_alert, "Duplicate plugin ids detected", "This can cause unexpected behavior, ideally uninstall duplicate plugins (ids)",
                            dups.map { it.name }.joinToString("\n"), 0, UIDialogs.Action("Ok", { }));
                    }

                    //throw IllegalStateException("Attempted to add 2 clients with the same ID");
                }

                enabled = _enabledClientsPersistent.getAllValues()
                    .filter { _availableClients.any { ac -> ac.id == it } }
                    .toTypedArray();
                if(enabled.isEmpty()) {
                    enabled = StatePlugins.instance.getEmbeddedSourcesDefault(context)
                        .filter { id -> _availableClients.any { it.id == id } }
                        .toTypedArray();
                }
            }
            selectClients(*enabled);

            for(toDisable in toDispose) {
                launch(Dispatchers.IO) {
                    try {
                        toDisable.disable();
                        onSourceDisabled.emit(toDisable);
                    }
                    catch(ex: Throwable) {
                        Logger.e(TAG, "FAILED TO DISABLE CLIENT [${toDisable?.name}] AFTER UpdateAvailableClients", ex);
                    }
                }
            }
        };
    }

    fun getAvailableClients(): List<IPlatformClient> {
        synchronized(_clientsLock) {
            return _availableClients.toList();
        }
    }
    fun getEnabledClients(): List<IPlatformClient> {
        synchronized(_clientsLock) {
            return _enabledClients.toList();
        }
    }

    //TODO: getEnabledClients should already be ordered, remove after verify that that is the case
    fun getSortedEnabledClient(): List<IPlatformClient> {
        synchronized(_clientsLock) {
            return EnabledClientResolver.orderedClients(_enabledClients, platformOrder);
        }
    }
    fun getClientOrNull(id: String): IPlatformClient? {
        synchronized(_clientsLock) {
            return _availableClients.find { it.id == id };
        }
    }
    fun getClient(id: String): IPlatformClient {
        return getClientOrNull(id) ?: throw IllegalArgumentException("Client with id $id does not exist");
    }

    fun getClientsByClaimType(claimType: Int): List<IPlatformClient> {
        return getEnabledClients().filter { it.isClaimTypeSupported(claimType) };
    }
    fun getClientByClaimTypeOrNull(claimType: Int): IPlatformClient? {
        return getEnabledClients().firstOrNull { it.isClaimTypeSupported(claimType) };
    }
    fun getDevClient() : DevJSClient? {
        return getClientOrNull(StateDeveloper.DEV_ID) as DevJSClient?;
    }

    fun getPlatformIcon(type: String?) : ImageVariable? {
        if(type == null)
            return null;
        if(_icons.containsKey(type))
            return _icons[type];
        return null;
    }

    fun getPlatformIconByName(name: String?) : ImageVariable? {
        if(name == null)
            return null;
        val nameLower = name.lowercase()
        if(_iconsByName.containsKey(nameLower))
            return _iconsByName[nameLower];
        return null;
    }

    fun setPlatformOrder(platformOrder: List<String>) {
        _platformOrderPersistent.values.clear();
        _platformOrderPersistent.values.addAll(platformOrder);
        _platformOrderPersistent.save();
    }

    fun handleReloadRequired(reloadRequiredException: ScriptReloadRequiredException, afterReload: (() -> Unit)? = null) {
        val id = if(reloadRequiredException.config is SourcePluginConfig) reloadRequiredException.config.id else "";
        UIDialogs.appToast("Reloading [${reloadRequiredException.config.name}] by plugin request");
        StateApp.instance.scopeOrNull?.launch(Dispatchers.IO) {
            if(!reloadRequiredException.reloadData.isNullOrEmpty())
                reEnableClientWithData(id, reloadRequiredException.reloadData, afterReload);
            else
                reEnableClient(id, afterReload);
        }
    }

    suspend fun reloadClient(context: Context, id: String, afterReload: (()->Unit)? = null) : JSClient? {
        return withContext(Dispatchers.IO) {
            val client = getClient(id);
            if (client !is JSClient)
                return@withContext null; //TODO: Error?

            Logger.i(TAG, "Reloading plugin ${client.name}");

            val newClient = if (client is DevJSClient)
                client.recreate(context);
            else
                JSClient(context,
                    StatePlugins.instance.getPlugin(id)
                        ?: throw IllegalStateException("Client existed, but plugin config didn't")
                );
            newClient.onCaptchaException.subscribe { c, ex ->
                StateApp.instance.handleCaptchaException(c, ex);
            }

            var toDisable: IPlatformClient? = null;
            synchronized(_clientsLock) {
                if (_enabledClients.contains(client)) {
                    _enabledClients.remove(client);
                    toDisable = client;
                    newClient.initialize();
                    _enabledClients.add(newClient);
                }

                _availableClients.removeIf { it.id == id };
                _availableClients.add(newClient);
            }
            if(toDisable != null) {
                launch(Dispatchers.IO) {
                    try {
                        toDisable?.disable();
                        onSourceDisabled.emit(client);
                    }
                    catch (ex: Throwable) {
                        Logger.e(TAG, "FAILED TO DISABLE CLIENT [${toDisable?.name}] AFTER RELOAD", ex);
                    }
                }
            }

            // Login (and any other auth change) lands here: the home feed's
            // source list re-publishes so the "log in?" notice flips away.
            onEnabledClientsChanged.emit();
            afterReload?.invoke();
            return@withContext newClient;
        };
    }

    suspend fun reEnableClientWithData(id: String, data: String? = null, afterReload: (()->Unit)? = null) {
        val enabledBefore = getEnabledClients().map { it.id };
        if(data != null) {
            val client = getClientOrNull(id);
            if(client != null && client is JSClient)
                client.setReloadData(data);
        }
        selectClients({
            _scope.launch(Dispatchers.IO) {
                selectClients({
                    afterReload?.invoke();
                }, *(enabledBefore).distinct().toTypedArray());
            }
        }, *(enabledBefore.filter { it != id }).distinct().toTypedArray())
    }
    suspend fun reEnableClient(id: String, afterReload: (()->Unit)? = null) = reEnableClientWithData(id, null, afterReload);

    suspend fun enableClient(ids: List<String>) {
        val currentClients = getEnabledClients().map { it.id };
        selectClients(*(currentClients + ids).distinct().toTypedArray());
    }
    /**
     * Selects the enabled clients, meaning all clients that data is actively requested from.
     * If a client is disabled, NO requests are made to said client
     */
    suspend fun selectClients(vararg ids: String) {
        selectClients(null, *ids);
    }
    suspend fun selectClients(afterLoad: (() -> Unit)?, vararg ids: String) {
        withContext(Dispatchers.IO) {
            _selectClientsMutex.withLock {
                var removed: MutableList<IPlatformClient>;
                synchronized(_clientsLock) {
                    removed = _enabledClients.toMutableList();
                    _enabledClients.clear();
                    for (id in ids) {
                        val client = getClient(id);
                        try {
                            if (!removed.removeIf { it == client })
                                client.initialize();
                            _enabledClients.add(client);
                        }
                        catch(ex: Exception) {
                            Logger.e(TAG, "Plugin ${client.name} failed to load\n${ex.message}", ex)
                            UIDialogs.toast("Plugin ${client.name} failed to load\n${ex.message}");
                        }
                    }
                    _enabledClientsPersistent.set(*ids);
                    _enabledClientsPersistent.save();
                }

                for (oldClient in removed) {
                    oldClient.disable();
                    onSourceDisabled.emit(oldClient);
                }
                afterLoad?.invoke();
            }
        };
        onEnabledClientsChanged.emit();
    }

    fun getHome(): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - getHome");
        var clientIdsOngoing = mutableListOf<String>();
        val clients = getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };

        StateApp.instance.scopeOrNull?.let {
            it.launch(Dispatchers.Default) {
                try {
                    delay(5000);
                    val slowClients = synchronized(clientIdsOngoing) {
                        return@synchronized clients.filter { clientIdsOngoing.contains(it.id) };
                    };
                    for(client in slowClients)
                        UIDialogs.toast("${client.name} is still loading..\nConsider disabling it for Home", android.widget.Toast.LENGTH_LONG);
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to show toast for slow source.", e)
                }
            }
        };

        val pages = clients.parallelStream()
            .map {
                Logger.i(TAG, "getHome - ${it.name}")
                synchronized(clientIdsOngoing) {
                    clientIdsOngoing.add(it.id);
                }
                val homeResult = it.fromPool(_pagerClientPool).getHome();
                synchronized(clientIdsOngoing) {
                    clientIdsOngoing.remove(it.id);
                }
                return@map homeResult;
            }
            .asSequence()
            .toList()
            .associateWith { 1f };

        val pager = MultiDistributionContentPager(pages);
        pager.initialize();
        return pager;
    }
    fun getShorts(): IPager<IPlatformVideo> {
        Logger.i(TAG, "Platform - getShorts");
        var clientIdsOngoing = mutableListOf<String>();
        val clients = getSortedEnabledClient().filter { if (it is JSClient) it.enableInShorts else true };

        StateApp.instance.scopeOrNull?.let {
            it.launch(Dispatchers.Default) {
                try {
                    // plugins that take longer than 5 seconds to load are considered "slow"
                    delay(5000);
                    val slowClients = synchronized(clientIdsOngoing) {
                        return@synchronized clients.filter { clientIdsOngoing.contains(it.id) };
                    };
                    for(client in slowClients)
                        UIDialogs.toast("${client.name} is still loading..\nConsider disabling it for Home", android.widget.Toast.LENGTH_LONG);
                } catch (e: Throwable) {
                    Logger.e(TAG, "Failed to show toast for slow source.", e)
                }
            }
        }

        val pages = clients.parallelStream()
            .map {
                Logger.i(TAG, "getShorts - ${it.name}")
                synchronized(clientIdsOngoing) {
                    clientIdsOngoing.add(it.id);
                }
                val shortsResult = it.fromPool(_pagerClientPool).getShorts();
                synchronized(clientIdsOngoing) {
                    clientIdsOngoing.remove(it.id);
                }
                return@map shortsResult;
            }
            .asSequence()
            .toList()
            .associateWith { 1f };

        val pager = MultiDistributionContentPager(pages, 2);
        pager.initialize();
        return pager;
    }
    suspend fun getHomeRefresh(scope: CoroutineScope): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - getHome (Refresh)");
        val clients = getSortedEnabledClient().filter { if (it is JSClient) it.enableInHome else true };

        val deferred: List<Pair<IPlatformClient, Deferred<IPager<IPlatformContent>?>>> = clients.map {
            return@map Pair(it, scope.async(Dispatchers.IO) {
                try {
                    var searchResult = it.fromPool(_pagerClientPool).getHome();
                    if(searchResult.getResults().size == 0) {
                        Logger.i(TAG, "No home results, retrying");
                        sleep(500);
                        searchResult = it.fromPool(_pagerClientPool).getHome();
                    }
                    return@async searchResult;
                } catch(ex: Throwable) {
                    Logger.e(TAG, "getHomeRefresh", ex);
                    //throw ex;
                    //return@async null;
                    return@async PlaceholderPager(10, { PlatformContentPlaceholder(it.id, ex) });
                }
            });
        }.toList();

        val finishedPager = deferred.map { it.second }.awaitFirstNotNullDeferred() ?: return EmptyPager();
        val toAwait = deferred.filter { it.second != finishedPager.first };

        return RefreshDistributionContentPager(
            listOf(finishedPager.second),
            toAwait.map { it.second },
            toAwait.map { PlaceholderPager(5, { PlatformContentPlaceholder(it.first.id) }) });
    }



    //Video
    fun getContentDetails(url: String, forceRefetch: Boolean = false): Deferred<IPlatformContentDetails> {
        Logger.i(TAG, "Platform - getContentDetails (${url})");
        if(forceRefetch)
            clearContentDetailCache(url);

        return _batchTaskGetVideoDetails.execute(url);
    }
    fun clearContentDetailCache(url: String) {
        if(_cache.get(url) != null) {
            Logger.i(TAG, "Force clearing cache (${url})");
            _cache.remove(url);
        }
    }




    fun injectDevPlugin(source: SourcePluginConfig, script: String): String? {
        var devId: String? = null;
        synchronized(_clientsLock) {
            val enabledExisting = _enabledClients.filter { it is DevJSClient };
            val isEnabled = !enabledExisting.isEmpty()

            for (enabled in enabledExisting) {
                enabled.disable();
            }

            //Remove existing dev clients
            _enabledClients.removeIf { it is DevJSClient };
            _availableClients.removeIf { it is DevJSClient };

            source.id = StateDeveloper.DEV_ID;
            val newClient = DevJSClient(StateApp.instance.context, source, script);
            devId = newClient.devID;
            try {
                StateDeveloper.instance.initializeDev(devId!!);
                if (isEnabled) {
                    _enabledClients.add(newClient);
                    newClient.initialize();
                }
                _availableClients.add(newClient);
            } catch (ex: Exception) {
                Logger.e("StatePlatform", "Failed to initialize DevPlugin: ${ex.message}", ex);
                StateDeveloper.instance.logDevException(devId!!, "Failed to initialize due to: ${ex.message}");
            }
        }
        onDevSourceChanged.emit();
        return devId;
    }

    //TODO: Be careful with calling this unless you know for sure you're not gonna need the current app state anymore
    fun disableAllClients() {
        synchronized(_clientsLock) {
            val enabledClients = _enabledClients;
            _enabledClients.clear();
            for(enabled in enabledClients) {
                Logger.i(TAG, "Disabling plugin [${enabled.name}]");
                try {
                    enabled.disable();
                }
                catch (ex: Throwable) {
                    Logger.e(TAG, "FAILED TO DISABLE CLIENT [${enabled.name}] IN disableAllClients", ex);
                }
            }
        }
    }



    companion object {
        private var _instance : StatePlatform? = null;
        val instance : StatePlatform
            get(){
            if(_instance == null)
                _instance = StatePlatform();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
                it._scope.cancel("PlatformState finished");
            }
        }
    }
}
