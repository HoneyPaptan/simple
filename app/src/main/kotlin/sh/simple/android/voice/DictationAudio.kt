package sh.simple.android.voice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.min

object DictationAudio {
    const val SAMPLE_RATE = 16_000
    const val MAX_SECONDS = 120

    fun createRecord(context: Context): AudioRecord {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO")
        }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
    }

    fun pcm16ToFloat(samples: ShortArray, count: Int): FloatArray {
        val out = FloatArray(count)
        for (i in 0 until count) {
            out[i] = samples[i] / 32768f
        }
        return out
    }

    fun levelFromPcm(samples: ShortArray, count: Int): Float {
        var peak = 0
        for (i in 0 until count) {
            peak = maxOf(peak, abs(samples[i].toInt()))
        }
        return (peak / 32768f).coerceIn(0f, 1f)
    }

    fun maxSamples(): Int = SAMPLE_RATE * MAX_SECONDS
}
