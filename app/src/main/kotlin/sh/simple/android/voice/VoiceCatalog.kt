package sh.simple.android.voice

data class VoiceModelSpec(
    val id: String,
    val label: String,
    val fileName: String,
    val bytes: Long,
    val sha256: String,
)

object VoiceCatalog {
    const val DEFAULT_ID = "tiny.en"

    val models: List<VoiceModelSpec> = listOf(
        VoiceModelSpec(
            "tiny.en",
            "Tiny English",
            "ggml-tiny.en.bin",
            77_704_715L,
            "921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f",
        ),
        VoiceModelSpec(
            "base.en",
            "Base English",
            "ggml-base.en.bin",
            147_964_211L,
            "a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002",
        ),
        VoiceModelSpec(
            "small.en",
            "Small English",
            "ggml-small.en.bin",
            487_614_201L,
            "c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d",
        ),
        VoiceModelSpec(
            "tiny",
            "Tiny multilingual",
            "ggml-tiny.bin",
            77_691_713L,
            "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21",
        ),
        VoiceModelSpec(
            "base",
            "Base multilingual",
            "ggml-base.bin",
            147_951_465L,
            "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
        ),
    )

    fun find(id: String): VoiceModelSpec? = models.firstOrNull { it.id == id }

    fun downloadUrl(spec: VoiceModelSpec): String =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/${spec.fileName}"
}
