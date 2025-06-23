package com.ncorp.voicenote.view

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.TimePickerDialog
import android.content.Intent
import java.util.Calendar

import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.AlarmClock
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
	private var pulseAnimator: ObjectAnimator? = null
	private var lastPausedPosition = 0
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
			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					// Sonsuz tekrar eden kalp atışı animasyonu
					pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
						v,
						PropertyValuesHolder.ofFloat("scaleX", 1f, 2f, 1f),
						PropertyValuesHolder.ofFloat("scaleY", 1f, 2f, 1f)
					).apply {
						duration = 800
						repeatCount = ValueAnimator.INFINITE
						repeatMode = ValueAnimator.RESTART
						start()
					}

					v.isPressed = true
					if (!isRecording) startRecording()
					true
				}
				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					// Animasyonu durdur ve ölçeği normale döndür
					pulseAnimator?.cancel()
					pulseAnimator = null
					v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()

					v.isPressed = false
					if (isRecording) stopRecording()
					true
				}
				else -> false
			}
		}
	}
	private fun resumeAudio(note: VoiceNote) {
		mediaPlayer?.let {
			if (!it.isPlaying) {
				it.seekTo(lastPausedPosition)
				it.start()
				// İlerleme güncellemesini yeniden başlat
				startProgressUpdater(note)
			}
		}
	}

	private fun startProgressUpdater(note: VoiceNote) {
		progressRunnable = object : Runnable {
			override fun run() {
				val currentPosition = mediaPlayer?.currentPosition ?: 0
				val duration = mediaPlayer?.duration ?: 1
				adapter.updateProgress(note.id, currentPosition, duration)
				handler.postDelayed(this, 500)
			}
		}
		handler.post(progressRunnable!!)
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
						onAlarmClicked = { note -> setAlarm(note) },  // Burada setAlarm çağrılıyor
						onStopClicked = { note -> stopAudio() },
						onDeleteClicked = { note -> deleteNote(note) } ,
						onResumeClicked = { note -> resumeAudio(note) }
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
		val calendar = Calendar.getInstance()
		val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
			// Alarm Intent oluştur
			val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
				putExtra(AlarmClock.EXTRA_HOUR, hourOfDay)
				putExtra(AlarmClock.EXTRA_MINUTES, minute)
				putExtra(AlarmClock.EXTRA_MESSAGE, "Voice Note Alarm: ${note.title}")
				putExtra(AlarmClock.EXTRA_SKIP_UI, false) // Kullanıcıya alarmı onaylatır
			}

			if (intent.resolveActivity(requireActivity().packageManager) != null) {
				startActivity(intent)
			} else {
				Toast.makeText(requireContext(), "Alarm uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
			}
		}

		TimePickerDialog(
			requireContext(),
			timeSetListener,
			calendar.get(Calendar.HOUR_OF_DAY),
			calendar.get(Calendar.MINUTE),
			true // 24 saat formatı kullan
		).show()
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