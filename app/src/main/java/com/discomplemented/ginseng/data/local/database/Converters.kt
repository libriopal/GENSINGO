package com.discomplemented.ginseng.data.local.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class Converters {
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? {
        return uuidString?.let { UUID.fromString(it) }
    }

    @TypeConverter
    fun fromMap(map: Map<String, String>?): String? {
        return Gson().toJson(map)
    }

    @TypeConverter
    fun toMap(mapString: String?): Map<String, String>? {
        if (mapString == null) return null
        val type = object : TypeToken<Map<String, String>>() {}.type
        return Gson().fromJson(mapString, type)
    }
}
