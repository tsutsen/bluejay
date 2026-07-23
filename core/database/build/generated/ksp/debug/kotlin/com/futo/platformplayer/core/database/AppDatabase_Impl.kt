package com.futo.platformplayer.core.database

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.futo.platformplayer.core.database.dao.HistoryDao
import com.futo.platformplayer.core.database.dao.HistoryDao_Impl
import com.futo.platformplayer.core.database.dao.HomeFeedCacheDao
import com.futo.platformplayer.core.database.dao.HomeFeedCacheDao_Impl
import com.futo.platformplayer.core.database.dao.PlaylistDao
import com.futo.platformplayer.core.database.dao.PlaylistDao_Impl
import com.futo.platformplayer.core.database.dao.QueueDao
import com.futo.platformplayer.core.database.dao.QueueDao_Impl
import com.futo.platformplayer.core.database.dao.SubscriptionDao
import com.futo.platformplayer.core.database.dao.SubscriptionDao_Impl
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _queueDao: Lazy<QueueDao> = lazy {
    QueueDao_Impl(this)
  }

  private val _historyDao: Lazy<HistoryDao> = lazy {
    HistoryDao_Impl(this)
  }

  private val _playlistDao: Lazy<PlaylistDao> = lazy {
    PlaylistDao_Impl(this)
  }

  private val _homeFeedCacheDao: Lazy<HomeFeedCacheDao> = lazy {
    HomeFeedCacheDao_Impl(this)
  }

  private val _subscriptionDao: Lazy<SubscriptionDao> = lazy {
    SubscriptionDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "228ad704a48252258a4c24950535d65b", "f92aa2daa0b1de64588d4d58c2e1ac9e") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `queue` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contentUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `thumbnailUrl` TEXT, `positionMs` INTEGER NOT NULL, `order` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `history` (`contentUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `thumbnailUrl` TEXT, `lastPositionMs` INTEGER NOT NULL, `totalDurationMs` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, `viewedAt` INTEGER NOT NULL, PRIMARY KEY(`contentUrl`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `thumbnailUrl` TEXT, `videoCount` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `playlist_videos` (`playlistId` INTEGER NOT NULL, `videoOrder` INTEGER NOT NULL, `contentUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `thumbnailUrl` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `videoOrder`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_videos_playlistId` ON `playlist_videos` (`playlistId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `home_feed_cache` (`cacheKey` TEXT NOT NULL, `contentUrl` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `thumbnailUrl` TEXT, `contentType` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions` (`channelId` TEXT NOT NULL, `channelName` TEXT NOT NULL, `channelUrl` TEXT NOT NULL, `thumbnailUrl` TEXT, `subscriberCount` INTEGER, `subscribedAt` INTEGER NOT NULL, PRIMARY KEY(`channelId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '228ad704a48252258a4c24950535d65b')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `queue`")
        connection.execSQL("DROP TABLE IF EXISTS `history`")
        connection.execSQL("DROP TABLE IF EXISTS `playlists`")
        connection.execSQL("DROP TABLE IF EXISTS `playlist_videos`")
        connection.execSQL("DROP TABLE IF EXISTS `home_feed_cache`")
        connection.execSQL("DROP TABLE IF EXISTS `subscriptions`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsQueue: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsQueue.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("contentUrl", TableInfo.Column("contentUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("author", TableInfo.Column("author", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("positionMs", TableInfo.Column("positionMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("order", TableInfo.Column("order", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsQueue.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysQueue: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesQueue: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoQueue: TableInfo = TableInfo("queue", _columnsQueue, _foreignKeysQueue, _indicesQueue)
        val _existingQueue: TableInfo = read(connection, "queue")
        if (!_infoQueue.equals(_existingQueue)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |queue(com.futo.platformplayer.core.database.entity.QueueEntity).
              | Expected:
              |""".trimMargin() + _infoQueue + """
              |
              | Found:
              |""".trimMargin() + _existingQueue)
        }
        val _columnsHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsHistory.put("contentUrl", TableInfo.Column("contentUrl", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("author", TableInfo.Column("author", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("lastPositionMs", TableInfo.Column("lastPositionMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("totalDurationMs", TableInfo.Column("totalDurationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("watchedAt", TableInfo.Column("watchedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHistory.put("viewedAt", TableInfo.Column("viewedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoHistory: TableInfo = TableInfo("history", _columnsHistory, _foreignKeysHistory, _indicesHistory)
        val _existingHistory: TableInfo = read(connection, "history")
        if (!_infoHistory.equals(_existingHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |history(com.futo.platformplayer.core.database.entity.HistoryEntity).
              | Expected:
              |""".trimMargin() + _infoHistory + """
              |
              | Found:
              |""".trimMargin() + _existingHistory)
        }
        val _columnsPlaylists: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlaylists.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylists.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylists.put("description", TableInfo.Column("description", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylists.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylists.put("videoCount", TableInfo.Column("videoCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylists.put("createdAt", TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylists.put("updatedAt", TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlaylists: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPlaylists: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPlaylists: TableInfo = TableInfo("playlists", _columnsPlaylists, _foreignKeysPlaylists, _indicesPlaylists)
        val _existingPlaylists: TableInfo = read(connection, "playlists")
        if (!_infoPlaylists.equals(_existingPlaylists)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |playlists(com.futo.platformplayer.core.database.entity.PlaylistEntity).
              | Expected:
              |""".trimMargin() + _infoPlaylists + """
              |
              | Found:
              |""".trimMargin() + _existingPlaylists)
        }
        val _columnsPlaylistVideos: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlaylistVideos.put("playlistId", TableInfo.Column("playlistId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistVideos.put("videoOrder", TableInfo.Column("videoOrder", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistVideos.put("contentUrl", TableInfo.Column("contentUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistVideos.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistVideos.put("author", TableInfo.Column("author", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistVideos.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistVideos.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlaylistVideos: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysPlaylistVideos.add(TableInfo.ForeignKey("playlists", "CASCADE", "NO ACTION", listOf("playlistId"), listOf("id")))
        val _indicesPlaylistVideos: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesPlaylistVideos.add(TableInfo.Index("index_playlist_videos_playlistId", false, listOf("playlistId"), listOf("ASC")))
        val _infoPlaylistVideos: TableInfo = TableInfo("playlist_videos", _columnsPlaylistVideos, _foreignKeysPlaylistVideos, _indicesPlaylistVideos)
        val _existingPlaylistVideos: TableInfo = read(connection, "playlist_videos")
        if (!_infoPlaylistVideos.equals(_existingPlaylistVideos)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |playlist_videos(com.futo.platformplayer.core.database.entity.PlaylistVideoEntity).
              | Expected:
              |""".trimMargin() + _infoPlaylistVideos + """
              |
              | Found:
              |""".trimMargin() + _existingPlaylistVideos)
        }
        val _columnsHomeFeedCache: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsHomeFeedCache.put("cacheKey", TableInfo.Column("cacheKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("contentUrl", TableInfo.Column("contentUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("author", TableInfo.Column("author", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("contentType", TableInfo.Column("contentType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("cachedAt", TableInfo.Column("cachedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsHomeFeedCache.put("expiresAt", TableInfo.Column("expiresAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysHomeFeedCache: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesHomeFeedCache: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoHomeFeedCache: TableInfo = TableInfo("home_feed_cache", _columnsHomeFeedCache, _foreignKeysHomeFeedCache, _indicesHomeFeedCache)
        val _existingHomeFeedCache: TableInfo = read(connection, "home_feed_cache")
        if (!_infoHomeFeedCache.equals(_existingHomeFeedCache)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |home_feed_cache(com.futo.platformplayer.core.database.entity.HomeFeedCacheEntity).
              | Expected:
              |""".trimMargin() + _infoHomeFeedCache + """
              |
              | Found:
              |""".trimMargin() + _existingHomeFeedCache)
        }
        val _columnsSubscriptions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSubscriptions.put("channelId", TableInfo.Column("channelId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSubscriptions.put("channelName", TableInfo.Column("channelName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSubscriptions.put("channelUrl", TableInfo.Column("channelUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSubscriptions.put("thumbnailUrl", TableInfo.Column("thumbnailUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSubscriptions.put("subscriberCount", TableInfo.Column("subscriberCount", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSubscriptions.put("subscribedAt", TableInfo.Column("subscribedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSubscriptions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSubscriptions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSubscriptions: TableInfo = TableInfo("subscriptions", _columnsSubscriptions, _foreignKeysSubscriptions, _indicesSubscriptions)
        val _existingSubscriptions: TableInfo = read(connection, "subscriptions")
        if (!_infoSubscriptions.equals(_existingSubscriptions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |subscriptions(com.futo.platformplayer.core.database.entity.SubscriptionEntity).
              | Expected:
              |""".trimMargin() + _infoSubscriptions + """
              |
              | Found:
              |""".trimMargin() + _existingSubscriptions)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "queue", "history", "playlists", "playlist_videos", "home_feed_cache", "subscriptions")
  }

  public override fun clearAllTables() {
    super.performClear(true, "queue", "history", "playlists", "playlist_videos", "home_feed_cache", "subscriptions")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(QueueDao::class, QueueDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(HistoryDao::class, HistoryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(PlaylistDao::class, PlaylistDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(HomeFeedCacheDao::class, HomeFeedCacheDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SubscriptionDao::class, SubscriptionDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun queueDao(): QueueDao = _queueDao.value

  public override fun historyDao(): HistoryDao = _historyDao.value

  public override fun playlistDao(): PlaylistDao = _playlistDao.value

  public override fun homeFeedCacheDao(): HomeFeedCacheDao = _homeFeedCacheDao.value

  public override fun subscriptionDao(): SubscriptionDao = _subscriptionDao.value
}
