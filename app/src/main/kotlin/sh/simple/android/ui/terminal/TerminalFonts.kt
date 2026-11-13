package sh.simple.android.ui.terminal

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import sh.simple.android.R
import sh.simple.android.store.CodeFontStore

fun terminalTypeface(context: Context, store: CodeFontStore): Typeface {
    if (store.choice.mode == "host" && store.localFile.isFile) {
        Typeface.createFromFile(store.localFile)?.let { return it }
    }
    return ResourcesCompat.getFont(context, R.font.jetbrains_mono_nerd_regular) ?: Typeface.MONOSPACE
}
