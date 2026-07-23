package com.futo.platformplayer.core.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.futo.platformplayer.core.database.entity.PlaylistEntity
import com.futo.platformplayer.core.database.entity.PlaylistVideoEntity
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class PlaylistDao_Impl(
  __db: RoomDatabase,
) : PlaylistDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfPlaylistEntity: EntityInsertAdapter<PlaylistEntity>

  private val __insertAdapterOfPlaylistVideoEntity: EntityInsertAdapter<PlaylistVideoEntity>

  private val __deleteAdapterOfPlaylistEntity: EntityDeleteOrUpdateAdapter<PlaylistEntity>

  private val __deleteAdapterOfPlaylistVideoEntity: EntityDeleteOrUpdateAdapter<PlaylistVideoEntity>

  private val __updateAdapterOfPlaylistEntity: EntityDeleteOrUpdateAdapter<PlaylistEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfPlaylistEntity = object : EntityInsertAdapter<PlaylistEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `playlists` (`id`,`name`,`description`,`thumbnailUrl`,`videoCount`,`createdAt`,`updatedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpDescription)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnailUrl)
        }
        statement.bindLong(5, entity.videoCount.toLong())
        statement.bindLong(6, entity.createdAt)
        statement.bindLong(7, entity.updatedAt)
      }
    }
    this.__insertAdapterOfPlaylistVideoEntity = object : EntityInsertAdapter<PlaylistVideoEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `playlist_videos` (`playlistId`,`videoOrder`,`contentUrl`,`title`,`author`,`thumbnailUrl`,`addedAt`) VALUES (?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistVideoEntity) {
        statement.bindLong(1, entity.playlistId)
        statement.bindLong(2, entity.videoOrder.toLong())
        statement.bindText(3, entity.contentUrl)
        statement.bindText(4, entity.title)
        val _tmpAuthor: String? = entity.author
        if (_tmpAuthor == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpAuthor)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpThumbnailUrl)
        }
        statement.bindLong(7, entity.addedAt)
      }
    }
    this.__deleteAdapterOfPlaylistEntity = object : EntityDeleteOrUpdateAdapter<PlaylistEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `playlists` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__deleteAdapterOfPlaylistVideoEntity = object : EntityDeleteOrUpdateAdapter<PlaylistVideoEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `playlist_videos` WHERE `playlistId` = ? AND `videoOrder` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistVideoEntity) {
        statement.bindLong(1, entity.playlistId)
        statement.bindLong(2, entity.videoOrder.toLong())
      }
    }
    this.__updateAdapterOfPlaylistEntity = object : EntityDeleteOrUpdateAdapter<PlaylistEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `playlists` SET `id` = ?,`name` = ?,`description` = ?,`thumbnailUrl` = ?,`videoCount` = ?,`createdAt` = ?,`updatedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        val _tmpDescription: String? = entity.description
        if (_tmpDescription == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpDescription)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnailUrl)
        }
        statement.bindLong(5, entity.videoCount.toLong())
        statement.bindLong(6, entity.createdAt)
        statement.bindLong(7, entity.updatedAt)
        statement.bindLong(8, entity.id)
      }
    }
  }

  public override suspend fun insert(playlist: PlaylistEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfPlaylistEntity.insertAndReturnId(_connection, playlist)
    _result
  }

  public override suspend fun insertVideo(video: PlaylistVideoEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPlaylistVideoEntity.insert(_connection, video)
  }

  public override suspend fun insertVideos(videos: List<PlaylistVideoEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPlaylistVideoEntity.insert(_connection, videos)
  }

  public override suspend fun delete(playlist: PlaylistEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfPlaylistEntity.handle(_connection, playlist)
  }

  public override suspend fun deleteVideo(video: PlaylistVideoEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfPlaylistVideoEntity.handle(_connection, video)
  }

  public override suspend fun update(playlist: PlaylistEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfPlaylistEntity.handle(_connection, playlist)
  }

  public override fun observeAll(): Flow<List<PlaylistEntity>> {
    val _sql: String = "SELECT * FROM playlists ORDER BY updatedAt DESC"
    return createFlow(__db, false, arrayOf("playlists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfVideoCount: Int = getColumnIndexOrThrow(_stmt, "videoCount")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<PlaylistEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlaylistEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpVideoCount: Int
          _tmpVideoCount = _stmt.getLong(_columnIndexOfVideoCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = PlaylistEntity(_tmpId,_tmpName,_tmpDescription,_tmpThumbnailUrl,_tmpVideoCount,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): PlaylistEntity? {
    val _sql: String = "SELECT * FROM playlists WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfVideoCount: Int = getColumnIndexOrThrow(_stmt, "videoCount")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: PlaylistEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpVideoCount: Int
          _tmpVideoCount = _stmt.getLong(_columnIndexOfVideoCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = PlaylistEntity(_tmpId,_tmpName,_tmpDescription,_tmpThumbnailUrl,_tmpVideoCount,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPaginated(limit: Int, offset: Int): List<PlaylistEntity> {
    val _sql: String = "SELECT * FROM playlists ORDER BY updatedAt DESC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfVideoCount: Int = getColumnIndexOrThrow(_stmt, "videoCount")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updatedAt")
        val _result: MutableList<PlaylistEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlaylistEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpDescription: String?
          if (_stmt.isNull(_columnIndexOfDescription)) {
            _tmpDescription = null
          } else {
            _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpVideoCount: Int
          _tmpVideoCount = _stmt.getLong(_columnIndexOfVideoCount).toInt()
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = PlaylistEntity(_tmpId,_tmpName,_tmpDescription,_tmpThumbnailUrl,_tmpVideoCount,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeVideos(playlistId: Long): Flow<List<PlaylistVideoEntity>> {
    val _sql: String = "SELECT * FROM playlist_videos WHERE playlistId = ? ORDER BY videoOrder ASC"
    return createFlow(__db, false, arrayOf("playlist_videos")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        val _columnIndexOfPlaylistId: Int = getColumnIndexOrThrow(_stmt, "playlistId")
        val _columnIndexOfVideoOrder: Int = getColumnIndexOrThrow(_stmt, "videoOrder")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<PlaylistVideoEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlaylistVideoEntity
          val _tmpPlaylistId: Long
          _tmpPlaylistId = _stmt.getLong(_columnIndexOfPlaylistId)
          val _tmpVideoOrder: Int
          _tmpVideoOrder = _stmt.getLong(_columnIndexOfVideoOrder).toInt()
          val _tmpContentUrl: String
          _tmpContentUrl = _stmt.getText(_columnIndexOfContentUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpAuthor: String?
          if (_stmt.isNull(_columnIndexOfAuthor)) {
            _tmpAuthor = null
          } else {
            _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = PlaylistVideoEntity(_tmpPlaylistId,_tmpVideoOrder,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getVideosPaginated(
    playlistId: Long,
    limit: Int,
    offset: Int,
  ): List<PlaylistVideoEntity> {
    val _sql: String = "SELECT * FROM playlist_videos WHERE playlistId = ? ORDER BY videoOrder ASC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 3
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfPlaylistId: Int = getColumnIndexOrThrow(_stmt, "playlistId")
        val _columnIndexOfVideoOrder: Int = getColumnIndexOrThrow(_stmt, "videoOrder")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<PlaylistVideoEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: PlaylistVideoEntity
          val _tmpPlaylistId: Long
          _tmpPlaylistId = _stmt.getLong(_columnIndexOfPlaylistId)
          val _tmpVideoOrder: Int
          _tmpVideoOrder = _stmt.getLong(_columnIndexOfVideoOrder).toInt()
          val _tmpContentUrl: String
          _tmpContentUrl = _stmt.getText(_columnIndexOfContentUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpAuthor: String?
          if (_stmt.isNull(_columnIndexOfAuthor)) {
            _tmpAuthor = null
          } else {
            _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = PlaylistVideoEntity(_tmpPlaylistId,_tmpVideoOrder,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getVideoInPlaylist(playlistId: Long, contentUrl: String): PlaylistVideoEntity? {
    val _sql: String = "SELECT * FROM playlist_videos WHERE playlistId = ? AND contentUrl = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _argIndex = 2
        _stmt.bindText(_argIndex, contentUrl)
        val _columnIndexOfPlaylistId: Int = getColumnIndexOrThrow(_stmt, "playlistId")
        val _columnIndexOfVideoOrder: Int = getColumnIndexOrThrow(_stmt, "videoOrder")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: PlaylistVideoEntity?
        if (_stmt.step()) {
          val _tmpPlaylistId: Long
          _tmpPlaylistId = _stmt.getLong(_columnIndexOfPlaylistId)
          val _tmpVideoOrder: Int
          _tmpVideoOrder = _stmt.getLong(_columnIndexOfVideoOrder).toInt()
          val _tmpContentUrl: String
          _tmpContentUrl = _stmt.getText(_columnIndexOfContentUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpAuthor: String?
          if (_stmt.isNull(_columnIndexOfAuthor)) {
            _tmpAuthor = null
          } else {
            _tmpAuthor = _stmt.getText(_columnIndexOfAuthor)
          }
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result = PlaylistVideoEntity(_tmpPlaylistId,_tmpVideoOrder,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun countVideos(playlistId: Long): Int {
    val _sql: String = "SELECT COUNT(*) FROM playlist_videos WHERE playlistId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM playlists WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteVideoFromPlaylist(playlistId: Long, contentUrl: String) {
    val _sql: String = "DELETE FROM playlist_videos WHERE playlistId = ? AND contentUrl = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _argIndex = 2
        _stmt.bindText(_argIndex, contentUrl)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun reorderVideo(
    playlistId: Long,
    oldOrder: Int,
    newOrder: Int,
  ) {
    val _sql: String = "UPDATE playlist_videos SET videoOrder = ? WHERE playlistId = ? AND videoOrder = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, newOrder.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, playlistId)
        _argIndex = 3
        _stmt.bindLong(_argIndex, oldOrder.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
