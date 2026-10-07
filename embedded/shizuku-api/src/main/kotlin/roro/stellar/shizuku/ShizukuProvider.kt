package roro.stellar.shizuku

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log


open class ShizukuProvider : ContentProvider() {

    override fun attachInfo(context: Context?, info: ProviderInfo) {
        super.attachInfo(context, info)
        check(!info.multiprocess) { "android:multiprocess must be false" }
        check(info.exported) { "android:exported must be true" }
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        extras.classLoader = BinderContainer::class.java.classLoader

        return when (method) {
            METHOD_SEND_BINDER -> {
                handleSendBinder(extras)
                Bundle()
            }
            METHOD_GET_BINDER -> {
                handleGetBinder()
            }
            else -> null
        }
    }

    private fun handleSendBinder(extras: Bundle) {
        // ==================================================================
        
        // ------------------------------------------------------------------
        
        
        
        //
        
        
        
        // ==================================================================
        if (!isTrustedBinderSender()) {
            val badUid = android.os.Binder.getCallingUid()
            recordRejectedInjection(badUid)
            android.util.Log.e(
                TAG,
                "拒绝来自不可信来源的 Binder 注入：callingUid=" + badUid
            )
            return
        }

        val container = extras.getParcelable<BinderContainer>(EXTRA_BINDER)
        if (container?.binder != null) {
            Log.i(TAG, "收到 Shizuku Binder")
            ShizukuCompat.onBinderReceived(container.binder, context!!.packageName)
        }
    }

    private fun handleGetBinder(): Bundle? {
        val binder = ShizukuCompat.binder
        if (binder == null || !binder.pingBinder()) return null

        val reply = Bundle()
        reply.putParcelable(EXTRA_BINDER, BinderContainer(binder))
        return reply
    }

    override fun query(
        uri: Uri, projection: Array<String?>?,
        selection: String?, selectionArgs: Array<String?>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String?>?): Int = 0

    
    
    private fun isOwnServiceBinder(binder: android.os.IBinder?): Boolean {
        if (binder == null) return false
        return try {
            if (!binder.pingBinder()) return false
            binder.interfaceDescriptor == "com.stellar.server.IStellarService"
        } catch (tr: Throwable) {
            false
        }
    }

    private fun isTrustedBinderSender(): Boolean {
        return try {
            val uid = android.os.Binder.getCallingUid()
            uid == 0 || uid == 1000 || uid == 2000
        } catch (tr: Throwable) {
            false
        }
    }

    companion object {
        private const val TAG = "ShizukuProvider"

        
        @JvmStatic
        val rejectedInjectionInfo: String
            get() = rejectedInfo

        private var rejectedCount = 0
        private var rejectedInfo = "无"

        private fun recordRejectedInjection(uid: Int) {
            rejectedCount++
            rejectedInfo = "已拒绝 " + rejectedCount + " 次，最近一次来源 uid=" + uid
        }
        private const val METHOD_SEND_BINDER = "sendBinder"
        private const val METHOD_GET_BINDER = "getBinder"
        private const val EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER"
    }
}
