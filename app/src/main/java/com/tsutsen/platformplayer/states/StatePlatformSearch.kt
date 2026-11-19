package com.tsutsen.platformplayer.states

import com.tsutsen.platformplayer.UIDialogs
import com.tsutsen.platformplayer.api.media.IPlatformClient
import com.tsutsen.platformplayer.api.media.models.FilterGroup
import com.tsutsen.platformplayer.api.media.models.PlatformAuthorLink
import com.tsutsen.platformplayer.api.media.models.ResultCapabilities
import com.tsutsen.platformplayer.api.media.models.contents.IPlatformContent
import com.tsutsen.platformplayer.api.media.models.contents.PlatformContentPlaceholder
import com.tsutsen.platformplayer.api.media.platforms.js.JSClient
import com.tsutsen.platformplayer.api.media.structures.EmptyPager
import com.tsutsen.platformplayer.api.media.structures.IPager
import com.tsutsen.platformplayer.api.media.structures.MultiDistributionChannelPager
import com.tsutsen.platformplayer.api.media.structures.MultiDistributionContentPager
import com.tsutsen.platformplayer.api.media.structures.PlaceholderPager
import com.tsutsen.platformplayer.api.media.structures.RefreshDistributionContentPager
import com.tsutsen.platformplayer.awaitFirstNotNullDeferred
import com.tsutsen.platformplayer.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

// Search-related concerns extracted verbatim from StatePlatform.kt as extension
// functions. Bodies are unchanged; only the receiver qualification changed.
// Depends on StatePlatform internal members: TAG.

//Search
    fun StatePlatform.searchSuggestions(query: String): Array<String> {
        Logger.i(TAG, "Platform - searchSuggestions");
        //TODO: hasSearchSuggestions
        return getEnabledClients().firstOrNull()?.searchSuggestions(query) ?: arrayOf();
    }

    fun StatePlatform.search(query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search");
        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        val clients = if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
            else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        for (c in clients) {
            Logger.i(TAG, "Client enabled for search: " + c.name)
        }

        clients.parallelStream().forEach {
            val searchCapabilities = it.getSearchCapabilities();
            val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();
            pagers.put(it.search(query, type, sort, mappedFilters), 1f);
        };

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }
    fun StatePlatform.searchPlaylist(query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search playlist");
        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        val clients = if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
        else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        for (c in clients) {
            Logger.i(TAG, "Client enabled for search: " + c.name)
        }

        clients.filter { it.capabilities.hasSearchPlaylists }.parallelStream().forEach {
            val searchCapabilities = it.getSearchCapabilities();
            val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();
            pagers.put(it.searchPlaylists(query, type, sort, mappedFilters), 1f);
        };

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }
    suspend fun StatePlatform.searchRefresh(scope: CoroutineScope, query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search (refresh)");
        val clients =
            if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
            else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        for (c in clients) {
            Logger.i(TAG, "Client enabled for search: " + c.name)
        }

        val deferred: List<Pair<IPlatformClient, Deferred<IPager<IPlatformContent>?>>> = clients.map {
            return@map Pair(it, scope.async(Dispatchers.IO) {
                try {
                    val searchCapabilities = it.getSearchCapabilities();
                    val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();
                    val searchResult = it.search(query, type, sort, mappedFilters);
                    return@async searchResult;
                } catch(ex: Throwable) {
                    Logger.e(TAG, "searchRefresh", ex);
                    return@async null;
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

    fun StatePlatform.searchChannel(channelUrl: String, query: String, type: String? = null, sort: String? = null, filters: Map<String, List<String>> = mapOf(), clientIds: List<String>? = null): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - search channel $channelUrl");

        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        val clients = if (clientIds != null) getSortedEnabledClient().filter { (if (it is JSClient) it.enableInSearch else true) && clientIds.contains(it.id) }
            else getSortedEnabledClient().filter { if (it is JSClient) it.enableInSearch else true };

        clients.parallelStream().forEach {
            val searchCapabilities = it.getSearchChannelContentsCapabilities();
            val mappedFilters = filters.map { pair -> Pair(pair.key, pair.value.map { v -> searchCapabilities.filters.first { g -> g.idOrName == pair.key }.filters.first { f -> f.idOrName == v }.value }) }.toMap();

            if (it.isChannelUrl(channelUrl)) {
                pagers.put(it.searchChannelContents(channelUrl, query, type, sort, mappedFilters), 1f);
            }
        };

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }

    fun StatePlatform.getCommonSearchCapabilities(clientIds: List<String>): ResultCapabilities? {
        return getCommonSearchCapabilitiesType(clientIds){
            it.getSearchCapabilities()
        };
    }
    fun StatePlatform.getCommonSearchChannelContentsCapabilities(clientIds: List<String>): ResultCapabilities? {
        return getCommonSearchCapabilitiesType(clientIds){
            it.getSearchChannelContentsCapabilities()
        };
    }

    fun StatePlatform.getCommonSearchCapabilitiesType(clientIds: List<String>, capabilitiesGetter: (client: IPlatformClient)-> ResultCapabilities): ResultCapabilities? {
        try {
            Logger.i(TAG, "Platform - getCommonSearchCapabilities");

            val clients = getEnabledClients().filter { clientIds.contains(it.id) };
            val c = clients.firstOrNull() ?: return null;
            val cap = capabilitiesGetter(c)//c.getSearchCapabilities();

            //var types = arrayListOf<String>();
            var sorts = cap.sorts.toMutableList();
            var filters = cap.filters.toMutableList();

            val sortsToRemove = arrayListOf<Int>();
            val filtersToRemove = arrayListOf<Int>();

            for (i in 1 until clients.size) {
                val clientSearchCapabilities = capabilitiesGetter(clients[i]);//.getSearchCapabilities();

                for (j in 0 until sorts.size) {
                    if (!clientSearchCapabilities.sorts.contains(sorts[j])) {
                        sortsToRemove.add(j);
                    }
                }

                sorts = sorts.filterIndexed { index, _ -> index !in sortsToRemove }.toMutableList();

                for (k in 0 until filters.size) {
                    val matchingFilterGroup = clientSearchCapabilities.filters.firstOrNull { f -> filters[k].idOrName == f.idOrName };
                    if (matchingFilterGroup == null) {
                        filtersToRemove.add(k);
                    } else {
                        val currentFilterGroup = filters[k];
                        filters[k] = FilterGroup(currentFilterGroup.name, currentFilterGroup.filters.filter { a -> matchingFilterGroup.filters.any { b -> a.idOrName == b.idOrName } }
                            , currentFilterGroup.isMultiSelect, currentFilterGroup.id);
                    }
                }

                filters = filters.filterIndexed { index, _ -> index !in filtersToRemove }.toMutableList();
            }

            return ResultCapabilities(listOf(), sorts, filters);
        } catch (e: Throwable) {
            Logger.w(TAG, "Failed to get common search capabilities.", e);
            return null;
        }
    }

    fun StatePlatform.isSearchChannelsAvailable(): Boolean {
        return getEnabledClients().any { it.capabilities.hasChannelSearch };
    }
    fun StatePlatform.searchChannels(query: String): IPager<PlatformAuthorLink> {
        Logger.i(TAG, "Platform - searchChannels");
        val pagers = mutableMapOf<IPager<PlatformAuthorLink>, Float>();
        getSortedEnabledClient().parallelStream().forEach {
            try {
                if (it.capabilities.hasChannelSearch)
                    pagers.put(it.searchChannels(query), 1f);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed search channels", ex)
                UIDialogs.toast("Failed search channels on [${it.name}]\n(${ex.message})");
            }
        };
        if(pagers.isEmpty())
            return EmptyPager<PlatformAuthorLink>();

        val pager = MultiDistributionChannelPager(pagers);
        pager.initialize();
        return pager;
    }

    fun StatePlatform.searchChannelsAsContent(query: String): IPager<IPlatformContent> {
        Logger.i(TAG, "Platform - searchChannels");
        val pagers = mutableMapOf<IPager<IPlatformContent>, Float>();
        getSortedEnabledClient().parallelStream().forEach {
            try {
                if (it.capabilities.hasChannelSearch)
                    pagers.put(it.searchChannelsAsContent(query), 1f);
            }
            catch(ex: Throwable) {
                Logger.e(TAG, "Failed search channels", ex)
                UIDialogs.toast("Failed search channels on [${it.name}]\n(${ex.message})");
            }
        };
        if(pagers.isEmpty())
            return EmptyPager<IPlatformContent>();

        val pager = MultiDistributionContentPager(pagers);
        pager.initialize();
        return pager;
    }
