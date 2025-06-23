package com.ncorp.voicenote.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// Room için tablo tanımı — tablo adı "voice_notes"
@Entity(tableName = "voice_notes")
data class VoiceNote(

	// Kayıt başlığı (örneğin: "Kayıt - 01")
	val title: String,

	// Ses dosyasının tam yolu (örneğin: /data/user/0/.../audio.3gp)
	val filePath: String,

	// Kayıt süresi milisaniye cinsinden (örneğin: 4520 = 4.5 saniye)
	val duration: Long,

	// Kayıt oluşturulma zamanı (timestamp - örn: System.currentTimeMillis())
	val createdAt: Long,

	// Opsiyonel: alarm zamanı (timestamp) — kullanıcı alarm kurmadıysa null olabilir
	val alarmTime: Long? = null,

	// Room tarafından otomatik artan birincil anahtar (id)
	@PrimaryKey(autoGenerate = true)
	val id: Int = 0
)
