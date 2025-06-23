package com.ncorp.voicenote.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ncorp.voicenote.R
import com.ncorp.voicenote.view.MainActivity // Ana aktivitenin tam yolu
import java.util.*
import android.os.Vibrator

class AlarmReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		val title = intent.getStringExtra("title") ?: "Kayıt"

		Toast.makeText(context, "Alarm: $title", Toast.LENGTH_LONG).show()
		//Tireşim
		val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// Modern API için
			vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
		} else {
			// Eski API için
			vibrator.vibrate(1000) // 1000 ms titreşim
		}


		// Sesli uyarı
		try {
			val mediaPlayer = MediaPlayer.create(context, R.raw.alarm_sound)
			mediaPlayer?.start()
		} catch (e: Exception) {
			e.printStackTrace()
		}

		// Bildirim göster
		showNotification(context, title)
	}

	private fun showNotification(context: Context, title: String) {
		val channelId = "voice_note_alarm_channel"
		val channelName = "Voice Note Alarmları"

		// Bildirim yönlendirmesi (örn. uygulama açılır)
		val intent = Intent(context, MainActivity::class.java)
		val pendingIntent = PendingIntent.getActivity(
			context,
			0,
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val builder = NotificationCompat.Builder(context, channelId)
			.setSmallIcon(R.drawable.ic_baseline_alarm_24) // drawable içinde uygun bir ikon
			.setContentTitle("Voice Note Alarm")
			.setContentText("Kayıt: $title")
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setAutoCancel(true)
			.setContentIntent(pendingIntent)

		val notificationManager =
			context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		// Android 8.0+ için kanal oluştur
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				channelId, channelName,
				NotificationManager.IMPORTANCE_HIGH
			)
			notificationManager.createNotificationChannel(channel)
		}

		// Bildirimi göster
		notificationManager.notify(Random().nextInt(), builder.build())
	}
}
