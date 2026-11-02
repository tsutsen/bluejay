package com.tsutsen.platformplayer.states

import com.tsutsen.platformplayer.api.media.IPlatformClient

/**
 * The single query seam for "which enabled plugin applies to me".
 * StatePlatform owns the client set (and its pooled URL matchers); this
 * object expresses the shared policy over it. The pure overloads take the
 * client list + query params so the rules are unit-testable without Android;
 * the live functions read the current enabled set from StatePlatform.
 */
object EnabledClientResolver {
    // ---- Pure policy over an enabled client list ----

    /** Clients ordered by the user's platform order (matched by name), remaining clients keep enabled order after. */
    fun orderedClients(clients: List<IPlatformClient>, platformOrder: List<String>): List<IPlatformClient> {
        val ordered = platformOrder.mapNotNull { order ->
            clients.firstOrNull { it.name == order }
        }
        val remaining = clients.filter { it !in ordered };
        return ordered + remaining;
    }

    fun clientIds(clients: List<IPlatformClient>): List<String> = clients.map { it.id };
    fun clientIdSet(clients: List<IPlatformClient>): Set<String> = clients.map { it.id }.toSet();
    fun isClientEnabled(clients: List<IPlatformClient>, id: String): Boolean = clients.any { it.id == id };
    fun isClientEnabled(clients: List<IPlatformClient>, client: IPlatformClient): Boolean = clients.contains(client);

    /** More than one enabled source: source badges only earn their place with multiple sources. */
    fun isMultiSource(clients: List<IPlatformClient>): Boolean = clients.size > 1;

    // ---- Live queries; StatePlatform stays the data owner ----

    fun enabledClients(): List<IPlatformClient> = StatePlatform.instance.getEnabledClients();

    /** Enabled clients in the user's platform order (see [orderedClients]). */
    fun orderedEnabledClients(): List<IPlatformClient> =
        orderedClients(StatePlatform.instance.getEnabledClients(), StatePlatform.instance.platformOrder);

    fun enabledClientIds(): List<String> = clientIds(enabledClients());
    fun enabledClientIdSet(): Set<String> = clientIdSet(enabledClients());
    fun isClientEnabled(id: String): Boolean = isClientEnabled(enabledClients(), id);
    fun isClientEnabled(client: IPlatformClient): Boolean = isClientEnabled(enabledClients(), client);
    fun isMultiSourceEnabled(): Boolean = isMultiSource(enabledClients());

    /** First enabled client owning the url: channel, then playlist, then content (pooled matching). */
    fun clientForUrl(url: String): IPlatformClient? =
        StatePlatform.instance.getChannelClientOrNull(url)
            ?: StatePlatform.instance.getPlaylistClientOrNull(url)
            ?: StatePlatform.instance.getContentClientOrNull(url);

    fun channelClientForUrl(url: String): IPlatformClient? = StatePlatform.instance.getChannelClientOrNull(url);
    fun contentClientForUrl(url: String): IPlatformClient? = StatePlatform.instance.getContentClientOrNull(url);
    fun hasChannelClientForUrl(url: String): Boolean = StatePlatform.instance.getChannelClientOrNull(url) != null;
}
