package com.tsutsen.platformplayer.core.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.tsutsen.platformplayer.core.database.entity.SubscriptionEntity
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
public class SubscriptionDao_Impl(
  __db: RoomDatabase,
) : SubscriptionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSubscriptionEntity: EntityInsertAdapter<SubscriptionEntity>

  private val __deleteAdapterOfSubscriptionEntity: EntityDeleteOrUpdateAdapter<SubscriptionEntity>

  private val __updateAdapterOfSubscriptionEntity: EntityDeleteOrUpdateAdapter<SubscriptionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSubscriptionEntity = object : EntityInsertAdapter<SubscriptionEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `subscriptions` (`channelId`,`channelName`,`channelUrl`,`thumbnailUrl`,`subscriberCount`,`subscribedAt`) VALUES (?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SubscriptionEntity) {
        statement.bindText(1, entity.channelId)
        statement.bindText(2, entity.channelName)
        statement.bindText(3, entity.channelUrl)
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnailUrl)
        }
        val _tmpSubscriberCount: Long? = entity.subscriberCount
        if (_tmpSubscriberCount == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpSubscriberCount)
        }
        statement.bindLong(6, entity.subscribedAt)
      }
    }
    this.__deleteAdapterOfSubscriptionEntity = object : EntityDeleteOrUpdateAdapter<SubscriptionEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `subscriptions` WHERE `channelId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SubscriptionEntity) {
        statement.bindText(1, entity.channelId)
      }
    }
    this.__updateAdapterOfSubscriptionEntity = object : EntityDeleteOrUpdateAdapter<SubscriptionEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `subscriptions` SET `channelId` = ?,`channelName` = ?,`channelUrl` = ?,`thumbnailUrl` = ?,`subscriberCount` = ?,`subscribedAt` = ? WHERE `channelId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SubscriptionEntity) {
        statement.bindText(1, entity.channelId)
        statement.bindText(2, entity.channelName)
        statement.bindText(3, entity.channelUrl)
        val _tmpThumbnailUrl: String? = entity.thumbnailUrl
        if (_tmpThumbnailUrl == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnailUrl)
        }
        val _tmpSubscriberCount: Long? = entity.subscriberCount
        if (_tmpSubscriberCount == null) {
          statement.bindNull(5)
        } else {
          statement.bindLong(5, _tmpSubscriberCount)
        }
        statement.bindLong(6, entity.subscribedAt)
        statement.bindText(7, entity.channelId)
      }
    }
  }

  public override suspend fun upsert(subscription: SubscriptionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSubscriptionEntity.insert(_connection, subscription)
  }

  public override suspend fun delete(subscription: SubscriptionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfSubscriptionEntity.handle(_connection, subscription)
  }

  public override suspend fun update(subscription: SubscriptionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfSubscriptionEntity.handle(_connection, subscription)
  }

  public override fun observeAll(): Flow<List<SubscriptionEntity>> {
    val _sql: String = "SELECT * FROM subscriptions ORDER BY channelName ASC"
    return createFlow(__db, false, arrayOf("subscriptions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfChannelId: Int = getColumnIndexOrThrow(_stmt, "channelId")
        val _columnIndexOfChannelName: Int = getColumnIndexOrThrow(_stmt, "channelName")
        val _columnIndexOfChannelUrl: Int = getColumnIndexOrThrow(_stmt, "channelUrl")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfSubscriberCount: Int = getColumnIndexOrThrow(_stmt, "subscriberCount")
        val _columnIndexOfSubscribedAt: Int = getColumnIndexOrThrow(_stmt, "subscribedAt")
        val _result: MutableList<SubscriptionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SubscriptionEntity
          val _tmpChannelId: String
          _tmpChannelId = _stmt.getText(_columnIndexOfChannelId)
          val _tmpChannelName: String
          _tmpChannelName = _stmt.getText(_columnIndexOfChannelName)
          val _tmpChannelUrl: String
          _tmpChannelUrl = _stmt.getText(_columnIndexOfChannelUrl)
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpSubscriberCount: Long?
          if (_stmt.isNull(_columnIndexOfSubscriberCount)) {
            _tmpSubscriberCount = null
          } else {
            _tmpSubscriberCount = _stmt.getLong(_columnIndexOfSubscriberCount)
          }
          val _tmpSubscribedAt: Long
          _tmpSubscribedAt = _stmt.getLong(_columnIndexOfSubscribedAt)
          _item = SubscriptionEntity(_tmpChannelId,_tmpChannelName,_tmpChannelUrl,_tmpThumbnailUrl,_tmpSubscriberCount,_tmpSubscribedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getByChannelId(channelId: String): SubscriptionEntity? {
    val _sql: String = "SELECT * FROM subscriptions WHERE channelId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, channelId)
        val _columnIndexOfChannelId: Int = getColumnIndexOrThrow(_stmt, "channelId")
        val _columnIndexOfChannelName: Int = getColumnIndexOrThrow(_stmt, "channelName")
        val _columnIndexOfChannelUrl: Int = getColumnIndexOrThrow(_stmt, "channelUrl")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfSubscriberCount: Int = getColumnIndexOrThrow(_stmt, "subscriberCount")
        val _columnIndexOfSubscribedAt: Int = getColumnIndexOrThrow(_stmt, "subscribedAt")
        val _result: SubscriptionEntity?
        if (_stmt.step()) {
          val _tmpChannelId: String
          _tmpChannelId = _stmt.getText(_columnIndexOfChannelId)
          val _tmpChannelName: String
          _tmpChannelName = _stmt.getText(_columnIndexOfChannelName)
          val _tmpChannelUrl: String
          _tmpChannelUrl = _stmt.getText(_columnIndexOfChannelUrl)
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpSubscriberCount: Long?
          if (_stmt.isNull(_columnIndexOfSubscriberCount)) {
            _tmpSubscriberCount = null
          } else {
            _tmpSubscriberCount = _stmt.getLong(_columnIndexOfSubscriberCount)
          }
          val _tmpSubscribedAt: Long
          _tmpSubscribedAt = _stmt.getLong(_columnIndexOfSubscribedAt)
          _result = SubscriptionEntity(_tmpChannelId,_tmpChannelName,_tmpChannelUrl,_tmpThumbnailUrl,_tmpSubscriberCount,_tmpSubscribedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPaginated(limit: Int, offset: Int): List<SubscriptionEntity> {
    val _sql: String = "SELECT * FROM subscriptions ORDER BY channelName ASC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfChannelId: Int = getColumnIndexOrThrow(_stmt, "channelId")
        val _columnIndexOfChannelName: Int = getColumnIndexOrThrow(_stmt, "channelName")
        val _columnIndexOfChannelUrl: Int = getColumnIndexOrThrow(_stmt, "channelUrl")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfSubscriberCount: Int = getColumnIndexOrThrow(_stmt, "subscriberCount")
        val _columnIndexOfSubscribedAt: Int = getColumnIndexOrThrow(_stmt, "subscribedAt")
        val _result: MutableList<SubscriptionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SubscriptionEntity
          val _tmpChannelId: String
          _tmpChannelId = _stmt.getText(_columnIndexOfChannelId)
          val _tmpChannelName: String
          _tmpChannelName = _stmt.getText(_columnIndexOfChannelName)
          val _tmpChannelUrl: String
          _tmpChannelUrl = _stmt.getText(_columnIndexOfChannelUrl)
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpSubscriberCount: Long?
          if (_stmt.isNull(_columnIndexOfSubscriberCount)) {
            _tmpSubscriberCount = null
          } else {
            _tmpSubscriberCount = _stmt.getLong(_columnIndexOfSubscriberCount)
          }
          val _tmpSubscribedAt: Long
          _tmpSubscribedAt = _stmt.getLong(_columnIndexOfSubscribedAt)
          _item = SubscriptionEntity(_tmpChannelId,_tmpChannelName,_tmpChannelUrl,_tmpThumbnailUrl,_tmpSubscriberCount,_tmpSubscribedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun search(query: String): Flow<List<SubscriptionEntity>> {
    val _sql: String = "SELECT * FROM subscriptions WHERE channelName LIKE '%' || ? || '%' ORDER BY channelName ASC"
    return createFlow(__db, false, arrayOf("subscriptions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, query)
        val _columnIndexOfChannelId: Int = getColumnIndexOrThrow(_stmt, "channelId")
        val _columnIndexOfChannelName: Int = getColumnIndexOrThrow(_stmt, "channelName")
        val _columnIndexOfChannelUrl: Int = getColumnIndexOrThrow(_stmt, "channelUrl")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfSubscriberCount: Int = getColumnIndexOrThrow(_stmt, "subscriberCount")
        val _columnIndexOfSubscribedAt: Int = getColumnIndexOrThrow(_stmt, "subscribedAt")
        val _result: MutableList<SubscriptionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SubscriptionEntity
          val _tmpChannelId: String
          _tmpChannelId = _stmt.getText(_columnIndexOfChannelId)
          val _tmpChannelName: String
          _tmpChannelName = _stmt.getText(_columnIndexOfChannelName)
          val _tmpChannelUrl: String
          _tmpChannelUrl = _stmt.getText(_columnIndexOfChannelUrl)
          val _tmpThumbnailUrl: String?
          if (_stmt.isNull(_columnIndexOfThumbnailUrl)) {
            _tmpThumbnailUrl = null
          } else {
            _tmpThumbnailUrl = _stmt.getText(_columnIndexOfThumbnailUrl)
          }
          val _tmpSubscriberCount: Long?
          if (_stmt.isNull(_columnIndexOfSubscriberCount)) {
            _tmpSubscriberCount = null
          } else {
            _tmpSubscriberCount = _stmt.getLong(_columnIndexOfSubscriberCount)
          }
          val _tmpSubscribedAt: Long
          _tmpSubscribedAt = _stmt.getLong(_columnIndexOfSubscribedAt)
          _item = SubscriptionEntity(_tmpChannelId,_tmpChannelName,_tmpChannelUrl,_tmpThumbnailUrl,_tmpSubscriberCount,_tmpSubscribedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM subscriptions"
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

  public override suspend fun deleteByChannelId(channelId: String) {
    val _sql: String = "DELETE FROM subscriptions WHERE channelId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, channelId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM subscriptions"
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
