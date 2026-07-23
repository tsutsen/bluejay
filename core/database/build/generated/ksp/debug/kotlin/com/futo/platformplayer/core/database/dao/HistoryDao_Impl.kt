package com.futo.platformplayer.core.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.futo.platformplayer.core.database.entity.HistoryEntity
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
public class HistoryDao_Impl(
  __db: RoomDatabase,
) : HistoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfHistoryEntity: EntityInsertAdapter<HistoryEntity>

  private val __deleteAdapterOfHistoryEntity: EntityDeleteOrUpdateAdapter<HistoryEntity>

  private val __updateAdapterOfHistoryEntity: EntityDeleteOrUpdateAdapter<HistoryEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfHistoryEntity = object : EntityInsertAdapter<HistoryEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `history` (`contentUrl`,`title`,`author`,`thumbnailUrl`,`lastPositionMs`,`totalDurationMs`,`watchedAt`,`viewedAt`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: HistoryEntity) {
        statement.bindText(1, entity.contentUrl)
        statement.bindText(2, entity.title)
        val _tmpAuthor: String? = entity.author
        if (_tmpAuthor == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpAuthor)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnailUrl)
        }
        statement.bindLong(5, entity.lastPositionMs)
        statement.bindLong(6, entity.totalDurationMs)
        statement.bindLong(7, entity.watchedAt)
        statement.bindLong(8, entity.viewedAt)
      }
    }
    this.__deleteAdapterOfHistoryEntity = object : EntityDeleteOrUpdateAdapter<HistoryEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `history` WHERE `contentUrl` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: HistoryEntity) {
        statement.bindText(1, entity.contentUrl)
      }
    }
    this.__updateAdapterOfHistoryEntity = object : EntityDeleteOrUpdateAdapter<HistoryEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `history` SET `contentUrl` = ?,`title` = ?,`author` = ?,`thumbnailUrl` = ?,`lastPositionMs` = ?,`totalDurationMs` = ?,`watchedAt` = ?,`viewedAt` = ? WHERE `contentUrl` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: HistoryEntity) {
        statement.bindText(1, entity.contentUrl)
        statement.bindText(2, entity.title)
        val _tmpAuthor: String? = entity.author
        if (_tmpAuthor == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpAuthor)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnailUrl)
        }
        statement.bindLong(5, entity.lastPositionMs)
        statement.bindLong(6, entity.totalDurationMs)
        statement.bindLong(7, entity.watchedAt)
        statement.bindLong(8, entity.viewedAt)
        statement.bindText(9, entity.contentUrl)
      }
    }
  }

  public override suspend fun upsert(history: HistoryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfHistoryEntity.insert(_connection, history)
  }

  public override suspend fun delete(history: HistoryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfHistoryEntity.handle(_connection, history)
  }

  public override suspend fun update(history: HistoryEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfHistoryEntity.handle(_connection, history)
  }

  public override fun observeAll(): Flow<List<HistoryEntity>> {
    val _sql: String = "SELECT * FROM history ORDER BY watchedAt DESC"
    return createFlow(__db, false, arrayOf("history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfLastPositionMs: Int = getColumnIndexOrThrow(_stmt, "lastPositionMs")
        val _columnIndexOfTotalDurationMs: Int = getColumnIndexOrThrow(_stmt, "totalDurationMs")
        val _columnIndexOfWatchedAt: Int = getColumnIndexOrThrow(_stmt, "watchedAt")
        val _columnIndexOfViewedAt: Int = getColumnIndexOrThrow(_stmt, "viewedAt")
        val _result: MutableList<HistoryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HistoryEntity
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
          val _tmpLastPositionMs: Long
          _tmpLastPositionMs = _stmt.getLong(_columnIndexOfLastPositionMs)
          val _tmpTotalDurationMs: Long
          _tmpTotalDurationMs = _stmt.getLong(_columnIndexOfTotalDurationMs)
          val _tmpWatchedAt: Long
          _tmpWatchedAt = _stmt.getLong(_columnIndexOfWatchedAt)
          val _tmpViewedAt: Long
          _tmpViewedAt = _stmt.getLong(_columnIndexOfViewedAt)
          _item = HistoryEntity(_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpLastPositionMs,_tmpTotalDurationMs,_tmpWatchedAt,_tmpViewedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByUrl(url: String): HistoryEntity? {
    val _sql: String = "SELECT * FROM history WHERE contentUrl = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, url)
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfLastPositionMs: Int = getColumnIndexOrThrow(_stmt, "lastPositionMs")
        val _columnIndexOfTotalDurationMs: Int = getColumnIndexOrThrow(_stmt, "totalDurationMs")
        val _columnIndexOfWatchedAt: Int = getColumnIndexOrThrow(_stmt, "watchedAt")
        val _columnIndexOfViewedAt: Int = getColumnIndexOrThrow(_stmt, "viewedAt")
        val _result: HistoryEntity?
        if (_stmt.step()) {
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
          val _tmpLastPositionMs: Long
          _tmpLastPositionMs = _stmt.getLong(_columnIndexOfLastPositionMs)
          val _tmpTotalDurationMs: Long
          _tmpTotalDurationMs = _stmt.getLong(_columnIndexOfTotalDurationMs)
          val _tmpWatchedAt: Long
          _tmpWatchedAt = _stmt.getLong(_columnIndexOfWatchedAt)
          val _tmpViewedAt: Long
          _tmpViewedAt = _stmt.getLong(_columnIndexOfViewedAt)
          _result = HistoryEntity(_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpLastPositionMs,_tmpTotalDurationMs,_tmpWatchedAt,_tmpViewedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPaginated(limit: Int, offset: Int): List<HistoryEntity> {
    val _sql: String = "SELECT * FROM history ORDER BY watchedAt DESC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfLastPositionMs: Int = getColumnIndexOrThrow(_stmt, "lastPositionMs")
        val _columnIndexOfTotalDurationMs: Int = getColumnIndexOrThrow(_stmt, "totalDurationMs")
        val _columnIndexOfWatchedAt: Int = getColumnIndexOrThrow(_stmt, "watchedAt")
        val _columnIndexOfViewedAt: Int = getColumnIndexOrThrow(_stmt, "viewedAt")
        val _result: MutableList<HistoryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HistoryEntity
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
          val _tmpLastPositionMs: Long
          _tmpLastPositionMs = _stmt.getLong(_columnIndexOfLastPositionMs)
          val _tmpTotalDurationMs: Long
          _tmpTotalDurationMs = _stmt.getLong(_columnIndexOfTotalDurationMs)
          val _tmpWatchedAt: Long
          _tmpWatchedAt = _stmt.getLong(_columnIndexOfWatchedAt)
          val _tmpViewedAt: Long
          _tmpViewedAt = _stmt.getLong(_columnIndexOfViewedAt)
          _item = HistoryEntity(_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpLastPositionMs,_tmpTotalDurationMs,_tmpWatchedAt,_tmpViewedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun observeContinueWatching(): Flow<List<HistoryEntity>> {
    val _sql: String = "SELECT * FROM history WHERE lastPositionMs > 0 ORDER BY watchedAt DESC"
    return createFlow(__db, false, arrayOf("history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfLastPositionMs: Int = getColumnIndexOrThrow(_stmt, "lastPositionMs")
        val _columnIndexOfTotalDurationMs: Int = getColumnIndexOrThrow(_stmt, "totalDurationMs")
        val _columnIndexOfWatchedAt: Int = getColumnIndexOrThrow(_stmt, "watchedAt")
        val _columnIndexOfViewedAt: Int = getColumnIndexOrThrow(_stmt, "viewedAt")
        val _result: MutableList<HistoryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HistoryEntity
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
          val _tmpLastPositionMs: Long
          _tmpLastPositionMs = _stmt.getLong(_columnIndexOfLastPositionMs)
          val _tmpTotalDurationMs: Long
          _tmpTotalDurationMs = _stmt.getLong(_columnIndexOfTotalDurationMs)
          val _tmpWatchedAt: Long
          _tmpWatchedAt = _stmt.getLong(_columnIndexOfWatchedAt)
          val _tmpViewedAt: Long
          _tmpViewedAt = _stmt.getLong(_columnIndexOfViewedAt)
          _item = HistoryEntity(_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpLastPositionMs,_tmpTotalDurationMs,_tmpWatchedAt,_tmpViewedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM history"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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

  public override suspend fun deleteByUrl(url: String) {
    val _sql: String = "DELETE FROM history WHERE contentUrl = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, url)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM history"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
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
