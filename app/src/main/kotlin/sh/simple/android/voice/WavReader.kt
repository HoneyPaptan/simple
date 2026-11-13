package sh.simple.android.voice

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavReader {
    fun loadMono16k(path: String): FloatArray {
        val file = File(path)
        require(file.isFile) { "missing" }
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") {
                error("bad_wav")
            }
            var channels = 1
            var sampleRate = 16_000
            var bits = 16
            var dataOffset = 0L
            var dataSize = 0
            while (raf.filePointer + 8 <= raf.length()) {
                val chunkId = readAscii(raf, 4)
                val chunkSize = readLeInt(raf).toLong() and 0xffffffffL
                when (chunkId) {
                    "fmt " -> {
                        val fmt = ByteArray(chunkSize.toInt().coerceAtMost(40))
                        raf.readFully(fmt)
                        val buf = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        channels = buf.getShort(2).toInt() and 0xffff
                        sampleRate = buf.getInt(4)
                        bits = buf.getShort(14).toInt() and 0xffff
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = chunkSize.toInt()
                        raf.seek(raf.filePointer + chunkSize)
                    }
                    else -> raf.seek(raf.filePointer + chunkSize)
                }
                if (chunkSize and 1L == 1L) raf.skipBytes(1)
            }
            if (dataSize <= 0 || bits != 16) error("bad_wav")
            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize)
            raf.readFully(bytes)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val frameCount = dataSize / (2 * channels)
            val pcm = FloatArray(frameCount)
            for (i in 0 until frameCount) {
                var sum = 0
                for (c in 0 until channels) {
                    sum += buf.short.toInt()
                }
                pcm[i] = (sum / channels) / 32768f
            }
            return resample(pcm, sampleRate, DictationAudio.SAMPLE_RATE)
        }
    }

    private fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input
        if (input.isEmpty()) return input
        val outLen = ((input.size.toLong() * toRate) / fromRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcPos = i.toDouble() * fromRate / toRate
            val j = srcPos.toInt().coerceIn(0, input.lastIndex)
            val frac = (srcPos - j).toFloat()
            val a = input[j]
            val b = input[(j + 1).coerceAtMost(input.lastIndex)]
            out[i] = a + (b - a) * frac
        }
        return out
    }

    private fun readAscii(raf: RandomAccessFile, n: Int): String {
        val b = ByteArray(n)
        raf.readFully(b)
        return String(b, Charsets.US_ASCII)
    }

    private fun readLeInt(raf: RandomAccessFile): Int {
        val b = ByteArray(4)
        raf.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
