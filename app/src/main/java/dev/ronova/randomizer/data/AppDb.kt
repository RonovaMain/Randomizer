package dev.ronova.randomizer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ListEntity::class, ElementEntity::class, StageEntity::class,
        ProfileEntity::class, RuleEntity::class, HistoryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun create(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "randomizer.db").build()
    }
}
