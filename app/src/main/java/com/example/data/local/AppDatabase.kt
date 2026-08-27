package com.example.data.local

import android.content.Context
import androidx.room.*
import com.example.data.model.*

class AppTypeConverters {
    @TypeConverter
    fun fromRole(role: Role): String = role.name

    @TypeConverter
    fun toRole(name: String): Role = try {
        Role.valueOf(name)
    } catch (e: Exception) {
        Role.C
    }

    @TypeConverter
    fun fromRisk(risk: RiskLevel): String = risk.name

    @TypeConverter
    fun toRisk(name: String): RiskLevel = try {
        RiskLevel.valueOf(name)
    } catch (e: Exception) {
        RiskLevel.MEDIO
    }

    @TypeConverter
    fun fromConfidence(confidence: ConfidenceLevel): String = confidence.name

    @TypeConverter
    fun toConfidence(name: String): ConfidenceLevel = try {
        ConfidenceLevel.valueOf(name)
    } catch (e: Exception) {
        ConfidenceLevel.ALTA
    }

    @TypeConverter
    fun fromWatchlistTag(tag: WatchlistTag): String = tag.name

    @TypeConverter
    fun toWatchlistTag(name: String): WatchlistTag = try {
        WatchlistTag.valueOf(name)
    } catch (e: Exception) {
        WatchlistTag.TARGET
    }
}

@Database(
    entities = [
        PlayerEntity::class,
        TeamEntity::class,
        AuctionEventEntity::class,
        LeagueConfig::class,
        WatchlistEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao
    abstract fun auctionEventDao(): AuctionEventDao
    abstract fun leagueDao(): LeagueDao
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fanta_asta_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
