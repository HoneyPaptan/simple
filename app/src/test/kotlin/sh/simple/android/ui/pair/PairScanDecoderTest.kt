package sh.simple.android.ui.pair

import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class PairScanDecoderTest {
    private val uri = "simple://pair?v=1&n=box&a=100.64.0.1:47420,[fd7a:115c:a1e0::1]:47420&f=" + "ab".repeat(32) + "&c=123456"

    private fun render(dark: Boolean): RGBLuminanceSource {
        val m = QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 0, 0)
        val scale = 6
        val pad = 4 * scale
        val w = m.width * scale + 2 * pad
        val h = m.height * scale + 2 * pad
        val module = if (dark) 0xDDDDDD else 0x111111
        val background = if (dark) 0x1E1E1E else 0xFFFFFF
        val px = IntArray(w * h) { i ->
            val x = (i % w - pad) / scale
            val y = (i / w - pad) / scale
            val inside = i % w >= pad && i / w >= pad && x < m.width && y < m.height
            if (inside && m.get(x, y)) module else background
        }
        return RGBLuminanceSource(w, h, px)
    }

    private fun scan(dark: Boolean): String? {
        val decoder = pairScanDecoderFactory().createDecoder(emptyMap<DecodeHintType, Any>())
        val frame = render(dark)
        return (1..2).firstNotNullOfOrNull { decoder.decode(frame)?.text }
    }

    @Test
    fun readsCodeFromLightTerminal() = assertEquals(uri, scan(dark = false))

    @Test
    fun readsInvertedCodeFromDarkTerminal() = assertEquals(uri, scan(dark = true))
}
