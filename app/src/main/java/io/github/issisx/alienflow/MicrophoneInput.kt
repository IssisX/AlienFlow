package io.github.issisx.alienflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** A physical microphone boundary; the transport owns its lifetime and every accepted frame. */
internal interface MicrophoneInput {
    fun start()
    fun read(samples: ShortArray): Int
    fun stop()
    fun release()
}

internal class AndroidMicrophoneInput(private val context: Context) : MicrophoneInput {
    @Volatile private var record: AudioRecord? = null
    @Volatile private var stopped = false

    override fun start() {
        if (stopped) return
        val minimum = AudioRecord.getMinBufferSize(RealtimeProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported microphone format" }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission was revoked before capture started")
        }
        val current = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RealtimeProtocol.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, RealtimeProtocol.CHUNK_BYTES * 4))
            .build()
        record = current
        check(current.state == AudioRecord.STATE_INITIALIZED) { "Microphone did not initialize" }
        if (stopped) return
        current.startRecording()
        // A Stop concurrent with initialization must win even if startRecording just returned.
        if (stopped) { stop(); return }
        check(current.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
    }
    override fun read(samples: ShortArray): Int {
        if (stopped) return 0
        return record?.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING) ?: 0
    }
    override fun stop() {
        stopped = true
        record?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
    }
    override fun release() { record?.release(); record = null }
}
