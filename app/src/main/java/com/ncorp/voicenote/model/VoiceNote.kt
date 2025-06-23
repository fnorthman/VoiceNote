package com.ncorp.voicenote.model



import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNote(
	val title: String,                 // Başlık (örneğin: Kayıt - 01)
	val filePath: String,             // Kayıt dosyasının yolu (örneğin: /data/user/0/.../audio.mp3)
	val duration: Long,               // Kayıt süresi (milisaniye cinsinden)
	val createdAt: Long,              // Oluşturulma zamanı (timestamp)
	val alarmTime: Long?,             // Ayarlanmışsa alarm zamanı (timestamp)
	@PrimaryKey(autoGenerate = true) val id: Int = 0
)

