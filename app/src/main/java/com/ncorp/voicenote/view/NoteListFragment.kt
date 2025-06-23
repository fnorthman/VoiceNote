package com.ncorp.voicenote.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.ncorp.voicenote.databinding.FragmentNoteListBinding
import com.ncorp.voicenote.model.VoiceNote
import com.ncorp.voicenote.database.AppDatabase
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers

class NoteListFragment : Fragment() {

	private  var _binding: FragmentNoteListBinding? = null
	private val binding get() = _binding!!
	private val disposable = CompositeDisposable()

	private lateinit var adapter: VoiceNoteAdapter
	private lateinit var db: AppDatabase

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentNoteListBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// Room DB oluşturuluyor
		db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "VoiceNotes.db")
			.build()

		// RecyclerView ayarları
		binding.recordRecyclerView.layoutManager = LinearLayoutManager(requireContext())

		// Sesli notları veritabanından al
		getVoiceNotes()

		// FAB mikrofona tıklama (kayıt için)
		binding.micButton.setOnClickListener {
			// Yeni kayıt işlemi burada başlatılacak
		}
	}

	private fun getVoiceNotes() {
		disposable.add(
			db.voiceNoteDao().getAll()
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe { notes ->
					adapter = VoiceNoteAdapter(notes,
						onPlayClicked = { note -> playAudio(note) },
						onAlarmClicked = { note -> setAlarm(note) }
					)
					binding.recordRecyclerView.adapter = adapter
				}
		)
	}

	private fun playAudio(note: VoiceNote) {
		// Oynatma işlemi burada olacak (MediaPlayer ile)
	}

	private fun setAlarm(note: VoiceNote) {
		// Alarm kurma işlemi burada olacak (AlarmManager ile)
	}

	override fun onDestroyView() {
		super.onDestroyView()
		disposable.clear()
		_binding = null
	}
}
