package sh.simple.android.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavReaderTest {
    @Test
    fun readsMono16k() {
        val dir = File.createTempFile("wavtest", null).parentFile!!
        val file = File(dir, "sample.wav")
        writeMonoWav(file, sampleRate = 16_000, samples = shortArrayOf(0, 16_384, 0, -16_384))
        val pcm = WavReader.loadMono16k(file.absolutePath)
        assertEquals(4, pcm.size)
        assertEquals(0.5f, pcm[1], 0.02f)
        file.delete()
    }

    private fun writeMonoWav(out: File, sampleRate: Int, samples: ShortArray) {
        val dataSize = samples.size * 2
        RandomAccessFile(out, "rw").use { raf ->
            raf.writeBytes("RIFF")
            writeLeInt(raf, 36 + dataSize)
            raf.writeBytes("WAVEfmt ")
            writeLeInt(raf, 16)
            writeLeShort(raf, 1)
            writeLeShort(raf, 1)
            writeLeInt(raf, sampleRate)
            writeLeInt(raf, sampleRate * 2)
            writeLeShort(raf, 2)
            writeLeShort(raf, 16)
            raf.writeBytes("data")
            writeLeInt(raf, dataSize)
            val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) buf.putShort(s)
            raf.write(buf.array())
        }
    }

    private fun writeLeInt(raf: RandomAccessFile, v: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        raf.write(b)
    }

    private fun writeLeShort(raf: RandomAccessFile, v: Int) {
        val b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
        raf.write(b)
    }
}
