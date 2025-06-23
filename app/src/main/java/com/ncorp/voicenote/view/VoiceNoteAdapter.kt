package com.ncorp.voicenote.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ncorp.voicenote.databinding.RecyclerRowBinding
import com.ncorp.voicenote.model.VoiceNote

class VoiceNoteAdapter(
	private val voiceNotes: List<VoiceNote>,
	private val onPlayClicked: (VoiceNote) -> Unit,
	private val onAlarmClicked: (VoiceNote) -> Unit,
	private val onStopClicked: (VoiceNote) -> Unit,   // Stop butonu callback'i
	private val onDeleteClicked: (VoiceNote) -> Unit,  // Silme callback'i
	private val onResumeClicked: (VoiceNote) -> Unit   // Resume callback'i
) : RecyclerView.Adapter<VoiceNoteAdapter.VoiceNoteViewHolder>() {

	inner class VoiceNoteViewHolder(val binding: RecyclerRowBinding) :
		RecyclerView.ViewHolder(binding.root)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceNoteViewHolder {
		val inflater = LayoutInflater.from(parent.context)
		val binding = RecyclerRowBinding.inflate(inflater, parent, false)
		return VoiceNoteViewHolder(binding)
	}

	override fun onBindViewHolder(holder: VoiceNoteViewHolder, position: Int) {
		val item = voiceNotes[position]

		holder.binding.audioTitleText.text = item.title

		// Toplam süreyi dakika:saniye formatında göster
		holder.binding.audioDurationText.text = formatDuration(item.duration.toInt())

		// İlerleme çubuğu maksimum değeri ve başlangıç değeri
		holder.binding.audioProgressBar.max = item.duration.toInt()
		holder.binding.audioProgressBar.progress = 0

		// Butonlara tıklama olayları
		holder.binding.playButton.setOnClickListener { onPlayClicked(item) }
		holder.binding.alarmButton.setOnClickListener { onAlarmClicked(item) }
		holder.binding.stopButton.setOnClickListener { onStopClicked(item) }
		holder.binding.deleteButton.setOnClickListener { onDeleteClicked(item) }
	}

	// Payload ile kısmi güncelleme (ilerleme çubuğu ve süre)
	override fun onBindViewHolder(holder: VoiceNoteViewHolder, position: Int, payloads: MutableList<Any>) {
		if (payloads.isNotEmpty()) {
			val (current, total) = payloads[0] as Pair<Int, Int>
			holder.binding.audioProgressBar.max = total
			holder.binding.audioProgressBar.progress = current
			holder.binding.audioDurationText.text = formatDuration(current)
		} else {
			onBindViewHolder(holder, position)
		}
	}

	// İlerleme çubuğu güncelleme çağrısı
	fun updateProgress(noteId: Int, currentPosition: Int, duration: Int) {
		val index = voiceNotes.indexOfFirst { it.id == noteId }
		if (index != -1) {
			notifyItemChanged(index, Pair(currentPosition, duration))
		}
	}

	override fun getItemCount(): Int = voiceNotes.size

	// Milisaniyeyi dakika:saniye formatına çevirir
	private fun formatDuration(ms: Int): String {
		val totalSec = ms / 1000
		val minutes = totalSec / 60
		val seconds = totalSec % 60
		return String.format("%d:%02d", minutes, seconds)
	}
}
