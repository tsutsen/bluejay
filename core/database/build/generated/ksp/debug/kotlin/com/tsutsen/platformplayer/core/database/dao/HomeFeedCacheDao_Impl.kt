package com.tsutsen.platformplayer.core.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.tsutsen.platformplayer.core.database.entity.HomeFeedCacheEntity
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
public class HomeFeedCacheDao_Impl(
  __db: RoomDatabase,
) : HomeFeedCacheDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfHomeFeedCacheEntity: EntityInsertAdapter<HomeFeedCacheEntity>

  private val __deleteAdapterOfHomeFeedCacheEntity: EntityDeleteOrUpdateAdapter<HomeFeedCacheEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfHomeFeedCacheEntity = object : EntityInsertAdapter<HomeFeedCacheEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `home_feed_cache` (`cacheKey`,`contentUrl`,`title`,`author`,`thumbnailUrl`,`contentType`,`cachedAt`,`expiresAt`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: HomeFeedCacheEntity) {
        statement.bindText(1, entity.cacheKey)
        statement.bindText(2, entity.contentUrl)
        statement.bindText(3, entity.title)
        val _tmpAuthor: String? = entity.author
        if (_tmpAuthor == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpAuthor)
        }
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpThumbnailUrl)
        }
        statement.bindText(6, entity.contentType)
        statement.bindLong(7, entity.cachedAt)
        statement.bindLong(8, entity.expiresAt)
      }
    }
    this.__deleteAdapterOfHomeFeedCacheEntity = object : EntityDeleteOrUpdateAdapter<HomeFeedCacheEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `home_feed_cache` WHERE `cacheKey` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: HomeFeedCacheEntity) {
        statement.bindText(1, entity.cacheKey)
      }
    }
  }

  public override suspend fun insert(cache: HomeFeedCacheEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfHomeFeedCacheEntity.insert(_connection, cache)
  }

  public override suspend fun insertAll(caches: List<HomeFeedCacheEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfHomeFeedCacheEntity.insert(_connection, caches)
  }

  public override suspend fun delete(cache: HomeFeedCacheEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfHomeFeedCacheEntity.handle(_connection, cache)
  }

  public override fun observeValidCache(currentTime: Long): Flow<List<HomeFeedCacheEntity>> {
    val _sql: String = "SELECT * FROM home_feed_cache WHERE expiresAt > ? ORDER BY cachedAt DESC"
    return createFlow(__db, false, arrayOf("home_feed_cache")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, currentTime)
        val _columnIndexOfCacheKey: Int = getColumnIndexOrThrow(_stmt, "cacheKey")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfContentType: Int = getColumnIndexOrThrow(_stmt, "contentType")
        val _columnIndexOfCachedAt: Int = getColumnIndexOrThrow(_stmt, "cachedAt")
        val _columnIndexOfExpiresAt: Int = getColumnIndexOrThrow(_stmt, "expiresAt")
        val _result: MutableList<HomeFeedCacheEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HomeFeedCacheEntity
          val _tmpCacheKey: String
          _tmpCacheKey = _stmt.getText(_columnIndexOfCacheKey)
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
          val _tmpContentType: String
          _tmpContentType = _stmt.getText(_columnIndexOfContentType)
          val _tmpCachedAt: Long
          _tmpCachedAt = _stmt.getLong(_columnIndexOfCachedAt)
          val _tmpExpiresAt: Long
          _tmpExpiresAt = _stmt.getLong(_columnIndexOfExpiresAt)
          _item = HomeFeedCacheEntity(_tmpCacheKey,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpContentType,_tmpCachedAt,_tmpExpiresAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCached(key: String, currentTime: Long): HomeFeedCacheEntity? {
    val _sql: String = "SELECT * FROM home_feed_cache WHERE cacheKey = ? AND expiresAt > ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, key)
        _argIndex = 2
        _stmt.bindLong(_argIndex, currentTime)
        val _columnIndexOfCacheKey: Int = getColumnIndexOrThrow(_stmt, "cacheKey")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfContentType: Int = getColumnIndexOrThrow(_stmt, "contentType")
        val _columnIndexOfCachedAt: Int = getColumnIndexOrThrow(_stmt, "cachedAt")
        val _columnIndexOfExpiresAt: Int = getColumnIndexOrThrow(_stmt, "expiresAt")
        val _result: HomeFeedCacheEntity?
        if (_stmt.step()) {
          val _tmpCacheKey: String
          _tmpCacheKey = _stmt.getText(_columnIndexOfCacheKey)
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
          val _tmpContentType: String
          _tmpContentType = _stmt.getText(_columnIndexOfContentType)
          val _tmpCachedAt: Long
          _tmpCachedAt = _stmt.getLong(_columnIndexOfCachedAt)
          val _tmpExpiresAt: Long
          _tmpExpiresAt = _stmt.getLong(_columnIndexOfExpiresAt)
          _result = HomeFeedCacheEntity(_tmpCacheKey,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpContentType,_tmpCachedAt,_tmpExpiresAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPaginatedByType(
    type: String,
    limit: Int,
    offset: Int,
    currentTime: Long,
  ): List<HomeFeedCacheEntity> {
    val _sql: String = "SELECT * FROM home_feed_cache WHERE contentType = ? AND expiresAt > ? ORDER BY cachedAt DESC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, type)
        _argIndex = 2
        _stmt.bindLong(_argIndex, currentTime)
        _argIndex = 3
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 4
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfCacheKey: Int = getColumnIndexOrThrow(_stmt, "cacheKey")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfContentType: Int = getColumnIndexOrThrow(_stmt, "contentType")
        val _columnIndexOfCachedAt: Int = getColumnIndexOrThrow(_stmt, "cachedAt")
        val _columnIndexOfExpiresAt: Int = getColumnIndexOrThrow(_stmt, "expiresAt")
        val _result: MutableList<HomeFeedCacheEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: HomeFeedCacheEntity
          val _tmpCacheKey: String
          _tmpCacheKey = _stmt.getText(_columnIndexOfCacheKey)
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
          val _tmpContentType: String
          _tmpContentType = _stmt.getText(_columnIndexOfContentType)
          val _tmpCachedAt: Long
          _tmpCachedAt = _stmt.getLong(_columnIndexOfCachedAt)
          val _tmpExpiresAt: Long
          _tmpExpiresAt = _stmt.getLong(_columnIndexOfExpiresAt)
          _item = HomeFeedCacheEntity(_tmpCacheKey,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpContentType,_tmpCachedAt,_tmpExpiresAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM home_feed_cache"
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

  public override suspend fun deleteExpired(currentTime: Long) {
    val _sql: String = "DELETE FROM home_feed_cache WHERE expiresAt <= ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, currentTime)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM home_feed_cache"
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
