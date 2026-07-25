package com.tsutsen.platformplayer.core.database.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.tsutsen.platformplayer.core.database.entity.QueueEntity
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
public class QueueDao_Impl(
  __db: RoomDatabase,
) : QueueDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfQueueEntity: EntityInsertAdapter<QueueEntity>

  private val __deleteAdapterOfQueueEntity: EntityDeleteOrUpdateAdapter<QueueEntity>

  private val __updateAdapterOfQueueEntity: EntityDeleteOrUpdateAdapter<QueueEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfQueueEntity = object : EntityInsertAdapter<QueueEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `queue` (`id`,`contentUrl`,`title`,`author`,`thumbnailUrl`,`positionMs`,`order`,`addedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: QueueEntity) {
        statement.bindLong(1, entity.id)
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
        statement.bindLong(6, entity.positionMs)
        statement.bindLong(7, entity.order.toLong())
        statement.bindLong(8, entity.addedAt)
      }
    }
    this.__deleteAdapterOfQueueEntity = object : EntityDeleteOrUpdateAdapter<QueueEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `queue` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: QueueEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfQueueEntity = object : EntityDeleteOrUpdateAdapter<QueueEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `queue` SET `id` = ?,`contentUrl` = ?,`title` = ?,`author` = ?,`thumbnailUrl` = ?,`positionMs` = ?,`order` = ?,`addedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: QueueEntity) {
        statement.bindLong(1, entity.id)
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
        statement.bindLong(6, entity.positionMs)
        statement.bindLong(7, entity.order.toLong())
        statement.bindLong(8, entity.addedAt)
        statement.bindLong(9, entity.id)
      }
    }
  }

  public override suspend fun insert(queue: QueueEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfQueueEntity.insert(_connection, queue)
  }

  public override suspend fun insertAll(queues: List<QueueEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfQueueEntity.insert(_connection, queues)
  }

  public override suspend fun delete(queue: QueueEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfQueueEntity.handle(_connection, queue)
  }

  public override suspend fun update(queue: QueueEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfQueueEntity.handle(_connection, queue)
  }

  public override fun observeAll(): Flow<List<QueueEntity>> {
    val _sql: String = "SELECT * FROM queue ORDER BY `order` ASC"
    return createFlow(__db, false, arrayOf("queue")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfPositionMs: Int = getColumnIndexOrThrow(_stmt, "positionMs")
        val _columnIndexOfOrder: Int = getColumnIndexOrThrow(_stmt, "order")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<QueueEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: QueueEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
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
          val _tmpPositionMs: Long
          _tmpPositionMs = _stmt.getLong(_columnIndexOfPositionMs)
          val _tmpOrder: Int
          _tmpOrder = _stmt.getLong(_columnIndexOfOrder).toInt()
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = QueueEntity(_tmpId,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpPositionMs,_tmpOrder,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): QueueEntity? {
    val _sql: String = "SELECT * FROM queue WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfPositionMs: Int = getColumnIndexOrThrow(_stmt, "positionMs")
        val _columnIndexOfOrder: Int = getColumnIndexOrThrow(_stmt, "order")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: QueueEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
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
          val _tmpPositionMs: Long
          _tmpPositionMs = _stmt.getLong(_columnIndexOfPositionMs)
          val _tmpOrder: Int
          _tmpOrder = _stmt.getLong(_columnIndexOfOrder).toInt()
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result = QueueEntity(_tmpId,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpPositionMs,_tmpOrder,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPaginated(limit: Int, offset: Int): List<QueueEntity> {
    val _sql: String = "SELECT * FROM queue ORDER BY `order` ASC LIMIT ? OFFSET ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, offset.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfContentUrl: Int = getColumnIndexOrThrow(_stmt, "contentUrl")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfAuthor: Int = getColumnIndexOrThrow(_stmt, "author")
        val _columnIndexOfThumbnailUrl: Int = getColumnIndexOrThrow(_stmt, "thumbnailUrl")
        val _columnIndexOfPositionMs: Int = getColumnIndexOrThrow(_stmt, "positionMs")
        val _columnIndexOfOrder: Int = getColumnIndexOrThrow(_stmt, "order")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<QueueEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: QueueEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
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
          val _tmpPositionMs: Long
          _tmpPositionMs = _stmt.getLong(_columnIndexOfPositionMs)
          val _tmpOrder: Int
          _tmpOrder = _stmt.getLong(_columnIndexOfOrder).toInt()
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = QueueEntity(_tmpId,_tmpContentUrl,_tmpTitle,_tmpAuthor,_tmpThumbnailUrl,_tmpPositionMs,_tmpOrder,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun count(): Int {
    val _sql: String = "SELECT COUNT(*) FROM queue"
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

  public override suspend fun deleteById(id: Long) {
    val _sql: String = "DELETE FROM queue WHERE id = ?"
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

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM queue"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateOrder(id: Long, newOrder: Int) {
    val _sql: String = "UPDATE queue SET `order` = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, newOrder.toLong())
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
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
