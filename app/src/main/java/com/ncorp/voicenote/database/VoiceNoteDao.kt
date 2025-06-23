package com.ncorp.voicenote.database
import androidx.room.*
import com.ncorp.voicenote.model.VoiceNote
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
@Dao
interface VoiceNoteDao {

	@Insert
	fun insert(voiceNote: VoiceNote): Completable

	@Delete
	fun delete(voiceNote: VoiceNote): Completable

	@Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
	fun getAll(): Flowable<List<VoiceNote>>

	@Query("SELECT * FROM voice_notes WHERE id = :noteId")
	fun getById(noteId: Int): Flowable<VoiceNote>
}