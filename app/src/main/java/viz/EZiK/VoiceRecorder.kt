package viz.EZiK

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): File {
        check(recorder == null) { "Already recording" }
        val file = File.createTempFile("ezik-command-", ".m4a", context.cacheDir)
        output = file
        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(16_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        return file
    }

    fun stop(): File? {
        val active = recorder ?: return null
        return try {
            active.stop()
            output
        } finally {
            active.release()
            recorder = null
        }
    }

    fun cancel() {
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        output?.delete()
        output = null
    }
}
