package com.ncorp.voicenote.view

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.ncorp.voicenote.alarm.AlarmReceiver
import com.ncorp.voicenote.databinding.FragmentNoteListBinding
import com.ncorp.voicenote.databinding.DialogRecordNameBinding
import com.ncorp.voicenote.databinding.DialogDeleteConfirmationBinding
import com.ncorp.voicenote.model.VoiceNote
import com.ncorp.voicenote.database.AppDatabase
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteListFragment : Fragment() {

	private var _binding: FragmentNoteListBinding? = null
	private val binding get() = _binding!!
	private val disposable = CompositeDisposable()
	private lateinit var adapter: VoiceNoteAdapter
	private lateinit var db: AppDatabase
	private var recorder: MediaRecorder? = null
	private var isRecording = false
	private var audioFilePath: String? = null
	private var mediaPlayer: MediaPlayer? = null
	private var progressRunnable: Runnable? = null
	private val handler = android.os.Handler(android.os.Looper.getMainLooper())
	private val RECORD_AUDIO_PERMISSION_CODE = 101
	private var recordingStartTime = 0L
	private val NOTIFICATION_PERMISSION_CODE = 102

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentNoteListBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		if (!checkAudioPermission()) {
			requestAudioPermission()
		}
		if (!checkNotificationPermission()) {
			requestNotificationPermission()
		}
		db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "VoiceNotes.db").build()
		binding.recordRecyclerView.layoutManager = LinearLayoutManager(requireContext())
		getVoiceNotes()
		binding.micButton.setOnTouchListener { v, event ->
			when(event.action) {
				MotionEvent.ACTION_DOWN -> {
					v.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
					v.isPressed = true
					if (!isRecording) startRecording()
					true
				}
				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
					v.isPressed = false
					if (isRecording) stopRecording()
					true
				}
				else -> false
			}
		}
	}

	private fun getFormattedCurrentDateTime(): String {
		val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
		return sdf.format(Date())
	}

	private fun getVoiceNotes() {
		disposable.add(
			db.voiceNoteDao().getAll()
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe { notes ->
					adapter = VoiceNoteAdapter(
						notes,
						onPlayClicked = { note -> playAudio(note) },
						onAlarmClicked = { note -> setAlarm(note) },
						onStopClicked = { note -> stopAudio() },
						onDeleteClicked = { note -> deleteNote(note) }
					)
					binding.recordRecyclerView.adapter = adapter
				}
		)
	}

	private fun playAudio(note: VoiceNote) {
		mediaPlayer?.release()
		mediaPlayer = null
		progressRunnable?.let { handler.removeCallbacks(it) }
		try {
			mediaPlayer = MediaPlayer().apply {
				setDataSource(note.filePath)
				prepare()
				start()
				setOnCompletionListener {
					it.release()
					mediaPlayer = null
					progressRunnable?.let { handler.removeCallbacks(it) }
					adapter.updateProgress(note.id, 0, note.duration.toInt())
				}
			}
			progressRunnable = object : Runnable {
				override fun run() {
					val currentPosition = mediaPlayer?.currentPosition ?: 0
					val duration = mediaPlayer?.duration ?: 1
					adapter.updateProgress(note.id, currentPosition, duration)
					handler.postDelayed(this, 500)
				}
			}
			handler.post(progressRunnable!!)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun setAlarm(note: VoiceNote) {
		val now = java.util.Calendar.getInstance()

		// Önce tarih seçimi
		val datePicker = android.app.DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
			// Sonra saat seçimi
			val timePicker = android.app.TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
				val calendar = java.util.Calendar.getInstance().apply {
					set(year, month, dayOfMonth, hourOfDay, minute, 0)
				}

				if (calendar.timeInMillis <= System.currentTimeMillis()) {
					Toast.makeText(requireContext(), "Lütfen gelecekte bir zaman seçin", Toast.LENGTH_LONG).show()
					return@TimePickerDialog
				}

				val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
					if (!alarmManager.canScheduleExactAlarms()) {
						val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
						startActivity(intent)
						Toast.makeText(requireContext(), "Alarm izni gerekli", Toast.LENGTH_LONG).show()
						return@TimePickerDialog
					}
				}

				val intent = Intent(requireContext(), AlarmReceiver::class.java).apply {
					putExtra("title", note.title)
					putExtra("filePath", note.filePath)
				}

				val pendingIntent = PendingIntent.getBroadcast(
					requireContext(),
					note.id,
					intent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)

				alarmManager.setExact(
					AlarmManager.RTC_WAKEUP,
					calendar.timeInMillis,
					pendingIntent
				)

				Toast.makeText(requireContext(), "Alarm kuruldu: ${calendar.time}", Toast.LENGTH_SHORT).show()

			}, now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), true)

			timePicker.show()

		}, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH))

		datePicker.show()
	}


	override fun onDestroyView() {
		super.onDestroyView()
		disposable.clear()
		mediaPlayer?.release()
		mediaPlayer = null
		recorder?.release()
		recorder = null
		_binding = null
	}

	private fun startRecording() {
		try {
			val audioDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
			val fileName = "Kayıt_${getFormattedCurrentDateTime()}.3gp"
			val audioFile = File(audioDir, fileName)
			audioFilePath = audioFile.absolutePath

			recorder = MediaRecorder().apply {
				setAudioSource(MediaRecorder.AudioSource.MIC)
				setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
				setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
				setOutputFile(audioFilePath)
				prepare()
				start()
			}

			recordingStartTime = System.currentTimeMillis()
			isRecording = true
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun stopRecording() {
		val recordingDuration = System.currentTimeMillis() - recordingStartTime
		if (recordingDuration < 1000) {
			recorder?.apply {
				try { stop() } catch (_: Exception) {}
				release()
			}
			recorder = null
			isRecording = false
			audioFilePath?.let { File(it).delete() }
			Toast.makeText(requireContext(), "Kayıt çok kısa, kaydedilmedi", Toast.LENGTH_SHORT).show()
			return
		}

		try {
			if (isRecording && recorder != null) recorder?.stop()
		} catch (e: RuntimeException) {
			e.printStackTrace()
			audioFilePath?.let { path -> File(path).takeIf { it.exists() }?.delete() }
			return
		} finally {
			recorder?.release()
			recorder = null
			isRecording = false
		}

		audioFilePath?.let { filePath ->
			val duration = getAudioDuration(filePath)
			showSaveNameDialog(filePath, duration)
		}
	}

	private fun showSaveNameDialog(filePath: String, duration: Long) {
		val dialogBinding = DialogRecordNameBinding.inflate(layoutInflater)
		val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
			.setView(dialogBinding.root)
			.setCancelable(false)
			.create()

		dialogBinding.btnCancelRecordName.setOnClickListener {
			File(filePath).takeIf { it.exists() }?.delete()
			dialog.dismiss()
		}

		dialogBinding.btnSaveRecordName.setOnClickListener {
			val enteredName = dialogBinding.editTextRecordName.text.toString().trim()
			val title = if (enteredName.isNotEmpty()) enteredName else "Kayıt - ${getFormattedCurrentDateTime()}"
			val newNote = VoiceNote(
				title = title,
				filePath = filePath,
				duration = duration,
				createdAt = System.currentTimeMillis(),
				alarmTime = null
			)
			saveVoiceNoteToDb(newNote)
			dialog.dismiss()
		}

		dialog.show()
	}

	private fun getAudioDuration(filePath: String): Long {
		val player = MediaPlayer()
		player.setDataSource(filePath)
		player.prepare()
		val duration = player.duration.toLong()
		player.release()
		return duration
	}

	private fun saveVoiceNoteToDb(note: VoiceNote) {
		disposable.add(
			db.voiceNoteDao().insert(note)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe { getVoiceNotes() }
		)
	}

	private fun checkAudioPermission(): Boolean {
		return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
	}

	private fun requestAudioPermission() {
		ActivityCompat.requestPermissions(
			requireActivity(),
			arrayOf(Manifest.permission.RECORD_AUDIO),
			RECORD_AUDIO_PERMISSION_CODE
		)
	}
	private fun checkNotificationPermission(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
		} else {
			true
		}
	}

	private fun requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ActivityCompat.requestPermissions(
				requireActivity(),
				arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
				NOTIFICATION_PERMISSION_CODE
			)
		}
	}
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (requestCode == NOTIFICATION_PERMISSION_CODE) {
			if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
				Toast.makeText(requireContext(), "Bildirim izni verildi", Toast.LENGTH_SHORT).show()
			} else {
				Toast.makeText(requireContext(), "Bildirim izni reddedildi", Toast.LENGTH_SHORT).show()
			}
		}
	}


	private fun stopAudio() {
		mediaPlayer?.let {
			if (it.isPlaying) {
				it.stop()
				it.release()
				mediaPlayer = null
				progressRunnable?.let { handler.removeCallbacks(it) }
				adapter.updateProgress(0, 0, 0)
			}
		}
	}

	private fun deleteNote(note: VoiceNote) {
		val dialog = android.app.Dialog(requireContext())
		val dialogBinding = DialogDeleteConfirmationBinding.inflate(layoutInflater)
		dialog.setContentView(dialogBinding.root)
		dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

		dialogBinding.btnConfirm.setOnClickListener {
			if (mediaPlayer?.isPlaying == true) stopAudio()
			disposable.add(
				db.voiceNoteDao().delete(note)
					.subscribeOn(Schedulers.io())
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe {
						File(note.filePath).takeIf { it.exists() }?.delete()
						getVoiceNotes()
					}
			)
			dialog.dismiss()
		}

		dialogBinding.btnCancel.setOnClickListener {
			dialog.dismiss()
		}

		dialog.show()
	}
}