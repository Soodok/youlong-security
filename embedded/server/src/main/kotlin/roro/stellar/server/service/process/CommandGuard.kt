// ==========================================================================


//




// ==========================================================================

// ==========================================================================

// --------------------------------------------------------------------------


//





//



//
// ==========================================================================

package roro.stellar.server.service.process

import android.content.Intent
import rikka.hidden.compat.ActivityManagerApis
import roro.stellar.server.ServerConstants
import roro.stellar.server.util.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicLong

object CommandGuard {

    private val LOGGER = Logger("CommandGuard")

    
    private val DIR: File = File("/data/local/tmp/stellar_guard")

    
    private const val CONFIRM_TIMEOUT_MS = 30_000L

    
    private const val POLL_INTERVAL_MS = 150L

    
    private const val MAX_CMD_LEN = 4000

    private val SEQ = AtomicLong(0)

    const val KIND_ALERT = "block"
    const val KIND_CONFIRM = "confirm"

    const val REPLY_ALLOW = "ALLOW"
    const val REPLY_DENY = "DENY"
    const val REPLY_ACK = "ACKNOWLEDGED"

    
    fun showBlockedAlert(
        packageName: String?,
        command: String,
        reason: String,
        userId: Int,
        
        riskKind: String = "normal"
    ) {
        val id = nextId()
        val request = writeRequest(id, KIND_ALERT, packageName, command, reason, riskKind)
        if (request == null) {
            LOGGER.w("告警框请求文件写入失败，跳过弹窗")
            return
        }
        if (!launch(id, KIND_ALERT, packageName, command, reason, request, userId)) {
            LOGGER.w("告警框拉起失败")
        }
    }

    
    fun askForConfirmation(
        packageName: String?,
        command: String,
        reason: String,
        userId: Int,
        
        riskKind: String = "normal"
    ): Boolean {
        val id = nextId()
        val request = writeRequest(id, KIND_CONFIRM, packageName, command, reason, riskKind)
        if (request == null) {
            LOGGER.w("确认框请求文件写入失败，按拒绝处理")
            return false
        }
        if (!launch(id, KIND_CONFIRM, packageName, command, reason, request, userId, riskKind)) {
            LOGGER.w("确认框拉起失败，按拒绝处理")
            cleanup(id)
            return false
        }

        val deadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS
        val response = File(DIR, "$id.resp")
        while (System.currentTimeMillis() < deadline) {
            try {
                if (response.isFile) {
                    val text = runCatching { response.readText() }.getOrDefault("")
                    val allow = text.trim().uppercase().startsWith(REPLY_ALLOW)
                    LOGGER.i("用户%s了对 %s 的命令", if (allow) "允许" else "拒绝", packageName)
                    return allow
                }
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                LOGGER.w("等待用户确认被中断，按拒绝处理")
                return false
            }
        }
        LOGGER.w("等待用户确认超时（%d ms），按拒绝处理", CONFIRM_TIMEOUT_MS)
        cleanup(id)
        return false
    }

    // ------------------------------------------------------------------
    
    // ------------------------------------------------------------------

    private fun nextId(): Long {
        
        
        val seq = SEQ.incrementAndGet()
        return "${System.currentTimeMillis()}$seq".toLong()
    }

    
    private fun writeRequest(
        id: Long,
        kind: String,
        packageName: String?,
        command: String,
        reason: String,
        
        riskKind: String = "normal"
    ): String? {
        return try {
            if (!DIR.exists() && !DIR.mkdirs()) {
                LOGGER.w("无法创建握手目录 %s", DIR.absolutePath)
                return null
            }
            
            
            
            runCatching {
                Runtime.getRuntime().exec(arrayOf("chmod", "0711", DIR.absolutePath)).waitFor()
            }.onFailure { LOGGER.w(it, "设置握手目录权限失败（不影响功能）") }
            val req = File(DIR, "$id.req")
            val body = buildString {
                append("id=").append(id).append('\n')
                append("kind=").append(kind).append('\n')
                append("package=").append(packageName ?: "").append('\n')
                append("reason=").append(reason.replace('\n', ' ')).append('\n')
                
                append("riskKind=").append(riskKind).append('\n')
                append("command=")
                    .append(
                        command.take(MAX_CMD_LEN).replace('\n', ' ').replace('\r', ' ')
                    )
                    .append('\n')
            }
            req.writeText(body)
            
            File(DIR, "$id.resp").delete()
            req.absolutePath
        } catch (t: Throwable) {
            LOGGER.w(t, "写请求文件失败")
            null
        }
    }

    private fun launch(
        id: Long,
        kind: String,
        packageName: String?,
        command: String,
        reason: String,
        requestPath: String,
        userId: Int,
        
        riskKind: String = "normal"
    ): Boolean {
        return try {
            val intent = Intent(ServerConstants.COMMAND_GUARD_ACTION)
                .setPackage(ServerConstants.MANAGER_APPLICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .putExtra("id", id)
                .putExtra("kind", kind)
                .putExtra("packageName", packageName)
                .putExtra("command", command.take(MAX_CMD_LEN))
                .putExtra("reason", reason)
                .putExtra("requestPath", requestPath)
                
                .putExtra("riskKind", riskKind)
            ActivityManagerApis.startActivityNoThrow(intent, null, userId)
            true
        } catch (t: Throwable) {
            LOGGER.w(t, "拉起命令拦截对话框失败")
            false
        }
    }

    private fun cleanup(id: Long) {
        runCatching { File(DIR, "$id.req").delete() }
        runCatching { File(DIR, "$id.resp").delete() }
    }

    
    fun handshakeDirPath(): String = DIR.absolutePath
}
