// ==========================================================================

//   server/src/main/kotlin/roro/stellar/server/service/process/ProcessManager.kt

//














// ==========================================================================

package roro.stellar.server.service.process

import com.stellar.server.IRemoteProcess
import com.stellar.server.IRemotePtyProcess
import rikka.hidden.compat.PackageManagerApis
import rikka.rish.RishConfig
import rikka.rish.RishConstants
import rikka.rish.RishHost
import roro.stellar.server.ServerConstants.MANAGER_APPLICATION_ID
import roro.stellar.server.bootstrap.ServerBootstrap
import roro.stellar.server.ClientManager
import roro.stellar.server.api.RemoteProcessHolder
import roro.stellar.server.api.RemotePtyProcessHolder
import roro.stellar.server.shizuku.ShizukuApiConstants
import roro.stellar.server.util.Logger
import java.io.File
import java.io.IOException

class ProcessManager(
    private val clientManager: ClientManager
) {
    companion object {
        private val LOGGER = Logger("ProcessManager")
    }

    fun newProcess(
        uid: Int,
        pid: Int,
        cmd: Array<String?>,
        env: Array<String?>?,
        dir: String?
    ): IRemoteProcess {
        LOGGER.d(
            "newProcess: uid=$uid, cmd=${cmd.contentToString()}, env=${env.contentToString()}, dir=$dir"
        )

        
        guard(uid, pid, cmd)

        val process: Process = try {
            Runtime.getRuntime().exec(cmd, env, if (dir != null) File(dir) else null)
        } catch (e: IOException) {
            throw IllegalStateException(e.message)
        }

        val clientRecord = clientManager.findClient(uid, pid)
        val token = clientRecord?.client?.asBinder()

        return RemoteProcessHolder(process, token)
    }

    fun newPtyProcess(
        uid: Int,
        pid: Int,
        cmd: Array<String?>,
        env: Array<String?>?,
        dir: String?
    ): IRemotePtyProcess {
        
        
        guard(uid, pid, cmd)

        ServerBootstrap.managerApplicationInfo?.nativeLibraryDir?.let {
            RishConfig.setLibraryPath(it)
        }
        RishConfig.init(ShizukuApiConstants.BINDER_DESCRIPTOR, 30000)
        val tty = (RishConstants.ATTY_IN or RishConstants.ATTY_OUT or RishConstants.ATTY_ERR).toByte()
        val host = RishHost(cmd.filterNotNull().toTypedArray(), env?.filterNotNull()?.toTypedArray(), dir ?: "", tty, null, null, null)
        host.start()
        val token = clientManager.findClient(uid, pid)?.client?.asBinder()
        return RemotePtyProcessHolder(host, token)
    }

    // ======================================================================
    
    // ======================================================================

    
    private fun guard(uid: Int, pid: Int, cmd: Array<String?>) {
        val callerPackage = resolvePackageName(uid)
        val isManager = callerPackage == MANAGER_APPLICATION_ID

        val decision = CommandInterceptor.inspect(cmd, callerPackage, isManager)
        if (decision is InterceptDecision.Allow) return

        val flat = cmd.filterNotNull().joinToString(" ")
        val userId = userIdOf(uid)

        when (decision) {
            is InterceptDecision.Block -> {
                CommandGuard.showBlockedAlert(callerPackage, flat, decision.reason, userId)
                throw SecurityException(
                    "命令已被「游龙安全护盾」拦截：${decision.reason}"
                )
            }

            is InterceptDecision.NeedConfirm -> {
                val allowed = CommandGuard.askForConfirmation(
                    callerPackage, flat, decision.reason, userId, decision.kind
                )
                if (!allowed) {
                    throw SecurityException(
                        "命令已被用户拒绝：${decision.reason}"
                    )
                }
                LOGGER.i("用户已允许 %s 执行：%s", callerPackage, decision.reason)
            }

            is InterceptDecision.Allow -> Unit
        }
    }

    
    private fun resolvePackageName(uid: Int): String? {
        clientManager.findClients(uid).firstOrNull()?.packageName?.let { return it }
        return runCatching {
            PackageManagerApis.getPackagesForUidNoThrow(uid)?.firstOrNull()
        }.getOrNull()
    }

    
    private fun userIdOf(uid: Int): Int = uid / 100000
}
