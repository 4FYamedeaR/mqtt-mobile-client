package com.mqttmobile.app.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "mqtt_messages")
data class MessageEntity(
    @PrimaryKey val id: Long,
    val profileId: String,
    val direction: String,
    val topic: String,
    val payload: ByteArray,
    val qos: Int,
    val retain: Boolean,
    val duplicate: Boolean,
    val receivedAt: Long,
    val propertiesJson: String
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM mqtt_messages ORDER BY receivedAt DESC")
    fun all(): List<MessageEntity>

    @Query("SELECT * FROM mqtt_messages WHERE profileId = :profileId ORDER BY receivedAt DESC")
    fun forProfile(profileId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM mqtt_messages WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM mqtt_messages WHERE profileId = :profileId")
    fun deleteForProfile(profileId: String)
}

@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class MqttDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}

object MqttDatabaseProvider {
    @Volatile
    private var instance: MqttDatabase? = null

    fun get(context: Context): MqttDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            MqttDatabase::class.java,
            "mqtt_mobile.db"
        ).fallbackToDestructiveMigration().allowMainThreadQueries().build().also { instance = it }
    }
}
