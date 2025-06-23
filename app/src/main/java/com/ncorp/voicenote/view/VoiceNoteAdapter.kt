package com.ncorp.voicenote.view



import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ncorp.voicenote.databinding.RecyclerRowBinding
import com.ncorp.voicenote.model.VoiceNote

class VoiceNoteAdapter(
	private val voiceNotes: List<VoiceNote>,
	private val onPlayClicked: (VoiceNote) -> Unit,
	private val onAlarmClicked: (VoiceNote) -> Unit
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

		// Süreyi saniye cinsinden göster
		val seconds = item.duration / 1000
		holder.binding.audioDurationText.text = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

		holder.binding.playButton.setOnClickListener { onPlayClicked(item) }
		holder.binding.alarmButton.setOnClickListener { onAlarmClicked(item) }
	}

	override fun getItemCount(): Int = voiceNotes.size
}
