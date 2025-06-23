package com.ncorp.voicenote.database
import androidx.room.Database
import androidx.room.RoomDatabase
import com.ncorp.voicenote.model.VoiceNote


@Database(entities = [VoiceNote::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
	abstract fun voiceNoteDao(): VoiceNoteDao
}