package viz.EZiK

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    @Synchronized
    fun start(): File {
        check(recorder == null) { "Already recording" }
        val file = File.createTempFile("ezik-command-", ".m4a", context.cacheDir)
        try {
            val r = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(16_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            output = file
            recorder = r
            return file
        } catch (t: Throwable) {
            file.delete()
            recorder?.release()
            recorder = null
            output = null
            throw t
        }
    }

    fun currentAmplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    @Synchronized
    fun stop(): File? {
        val active = recorder ?: return null
        val file = output
        recorder = null
        output = null
        return try {
            active.stop()
            file
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            active.release()
        }
    }

    @Synchronized
    fun cancel() {
        val active = recorder
        recorder = null
        val file = output
        output = null
        runCatching { active?.stop() }
        active?.release()
        file?.delete()
    }
}
