import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.joaomagdaleno.music_hub.R

object AppShortcuts {
    fun configureAppShortcuts(context: Context) {
        val searchShortcut = ShortcutInfoCompat.Builder(context, "search")
            .setShortLabel(context.getString(R.string.search))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_search))
            .setIntent(Intent(Intent.ACTION_VIEW, "echo://search".toUri()))
            .build()

        val libraryShortcut = ShortcutInfoCompat.Builder(context, "library")
            .setShortLabel(context.getString(R.string.library))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_library))
            .setIntent(Intent(Intent.ACTION_VIEW, "echo://library".toUri()))
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(searchShortcut, libraryShortcut))
    }
}