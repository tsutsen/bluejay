package com.tsutsen.platformplayer.core.database

import androidx.room.TypeConverter
import com.tsutsen.platformplayer.core.model.SavedVideoType

class SavedVideoTypeConverter {
    @TypeConverter
    fun fromType(value: SavedVideoType): String = value.name

    @TypeConverter
    fun toType(value: String): SavedVideoType = SavedVideoType.valueOf(value)
}
