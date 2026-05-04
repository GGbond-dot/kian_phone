package com.kian.khup.core.data.db

import androidx.room.TypeConverter

/** Room 用 TypeConverter 把 EventType 枚举存为 String。 */
class Converters {
    @TypeConverter
    fun eventTypeToString(value: EventType): String = value.name

    @TypeConverter
    fun stringToEventType(value: String): EventType = EventType.valueOf(value)
}
