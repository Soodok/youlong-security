// ==========================================================================

//   manager/src/main/kotlin/roro/stellar/manager/shortcut/CommandShortcutManager.kt

//

















// ==========================================================================

package roro.stellar.manager.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.widget.Toast
import roro.stellar.manager.R
import roro.stellar.manager.compat.BuildUtils.atLeast26

object CommandShortcutManager {
    
    fun requestPin(context: Context, commandId: String, title: String) {
        if (!atLeast26) {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (!manager.isRequestPinShortcutSupported) {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, CommandShortcutActivity::class.java)
            .setAction(CommandShortcutActivity.ACTION_EXECUTE_COMMAND)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            .putExtra(CommandShortcutActivity.EXTRA_COMMAND_ID, commandId)
        val shortcut = ShortcutInfo.Builder(context, "command:$commandId")
            .setShortLabel(title)
            .setLongLabel(title)
            
            .setIcon(Icon.createWithResource(context, R.drawable.ic_notification_shield))
            .setIntent(intent)
            .build()
        manager.requestPinShortcut(shortcut, null)
    }
}
