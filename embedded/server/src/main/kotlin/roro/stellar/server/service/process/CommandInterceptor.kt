// ==========================================================================

// --------------------------------------------------------------------------

//




//



//
// ==========================================================================

// --------------------------------------------------------------------------





//






//


// ==========================================================================

package roro.stellar.server.service.process

import roro.stellar.server.util.Logger


sealed class InterceptDecision {
    
    object Allow : InterceptDecision()

    
    data class Block(val reason: String) : InterceptDecision()

    
    data class NeedConfirm(val reason: String, val kind: String = "normal") : InterceptDecision()
}


object CommandInterceptor {

    private val LOGGER = Logger("CommandInterceptor")

    
    private val SELF_PACKAGES = listOf(
        "com.youlong.hd",    
        "com.youlong.tool"   
    )

    
    private val STOP_ALL_PATTERNS = listOf(
        Regex("""\bam\s+kill-all\b"""),
        Regex("""\bpm\s+disable-user\s+--user\s+\d+\s+-a\b"""),
        Regex("""\bpkill\b"""),
        Regex("""\bkillall\b"""),
        
        Regex("""\bam\s+force-stop\s+["']?\*"""),
        
        Regex("""\bpm\s+(uninstall|disable-user|disable|force-stop|suspend)\b[^\n|;&]*\s(-a|--all)\b""")
    )

    
    private val ENUMERATE_PACKAGES = Regex("""\bpm\s+list\s+packages\b""")
    private val LOOP_STOP = Regex("""\b(am\s+force-stop|pm\s+disable-user|pm\s+disable|pm\s+suspend|pm\s+hide)\b""")

    
    private val CRITICAL_SYSTEM_PACKAGES = listOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.providers.settings",
        "com.android.shell",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.android.mms",
        "com.android.providers.contacts",
        "com.android.providers.media",
        "com.android.documentsui",
        "com.android.vending",
        "com.android.launcher3",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.wifi",
        "com.android.se",
        "com.android.networkstack",
        "com.android.inputmethod.latin",
        "com.android.keychain"
    )

    
    private val SYSTEM_PACKAGE_PATTERN = Regex(
        "^(android|com\\.android|com\\.google\\.android|com\\.qualcomm|" +
            "com\\.mediatek|com\\.miui|com\\.xiaomi|com\\.huawei|com\\.honor|" +
            "com\\.oppo|com\\.coloros|com\\.vivo|com\\.bbk|com\\.oneplus|" +
            "com\\.oplus|com\\.realme|com\\.meizu|com\\.smartisan|com\\.sony|" +
            "com\\.samsung|com\\.sec|com\\.lg|com\\.htc|com\\.motorola|" +
            "com\\.lenovo|com\\.asus|com\\.nokia|com\\.zte)\\."
    )

    
    private val ADMIN_PATTERNS = listOf(
        Regex("""\bdpm\b"""),
        Regex("""\bpm\s+set-user-restriction\b"""),
        Regex("""\bpm\s+remove-user\b"""),
        Regex("""\bpm\s+create-user\b"""),
        Regex("""\bam\s+set-user-restriction\b"""),
        Regex("""\bcmd\s+device_policy\b"""),
        Regex("""\bcpm\s+(install|remove)\b""")
    )

    
    private val NEED_CONFIRM_PATTERNS = listOf(
        Regex("""\bpm\s+uninstall\b"""),
        Regex("""\bpm\s+disable-user\b"""),
        Regex("""\bpm\s+disable\b"""),
        Regex("""\bpm\s+suspend\b"""),
        Regex("""\bpm\s+hide\b"""),
        Regex("""\bpm\s+clear\b"""),
        Regex("""\bpm\s+set-user-restriction\b"""),
        Regex("""\bcmd\s+package\s+(uninstall|suspend|disable)\b""")
    )

    
    fun inspect(cmd: Array<String?>, callerPackage: String?, isManager: Boolean): InterceptDecision {
        
        if (isManager) return InterceptDecision.Allow

        val flat = flatten(cmd)
        if (flat.isBlank()) return InterceptDecision.Allow

        val who = callerPackage ?: "未知应用"

        
        
        
        if (SELF_PACKAGES.any { flat.contains(it) }) {
            if (!isReadOnly(flat)) {
                LOGGER.w("拦截针对自家应用的命令: caller=%s, cmd=%s", who, flat)
                return InterceptDecision.Block("尝试操作本应用或配套应用")
            }
        }

        
        if (STOP_ALL_PATTERNS.any { it.containsMatchIn(flat) }
            || (ENUMERATE_PACKAGES.containsMatchIn(flat) && LOOP_STOP.containsMatchIn(flat))
        ) {
            LOGGER.w("拦截批量停止命令: caller=%s, cmd=%s", who, flat)
            return InterceptDecision.Block("尝试停止所有应用")
        }

        
        if (NEED_CONFIRM_PATTERNS.any { it.containsMatchIn(flat) }) {
            
            
            val kind = analyzeRiskKind(flat)
            LOGGER.w("卸载/冻结命令需用户确认: caller=%s, kind=%s, cmd=%s", who, kind, flat)
            return InterceptDecision.NeedConfirm(describeDanger(flat), kind)
        }

        
        
        if (ADMIN_PATTERNS.any { it.containsMatchIn(flat) }) {
            LOGGER.w("设备管理员/策略类命令需用户确认: caller=%s, cmd=%s", who, flat)
            return InterceptDecision.NeedConfirm(describeDanger(flat), "admin")
        }

        return InterceptDecision.Allow
    }

    // ------------------------------------------------------------------
    
    // ------------------------------------------------------------------

    
    private fun analyzeRiskKind(flat: String): String {
        if (ADMIN_PATTERNS.any { it.containsMatchIn(flat) }) return "admin"
        if (mentionsSystemPackage(flat)) return "system"
        if (affectedPackageCount(flat) >= 3) return "bulk"
        return "normal"
    }

    
    private fun mentionsSystemPackage(flat: String): Boolean {
        if (CRITICAL_SYSTEM_PACKAGES.any { flat.contains(it) }) return true
        return extractPackages(flat).any { SYSTEM_PACKAGE_PATTERN.containsMatchIn(it) }
    }

    
    private fun affectedPackageCount(flat: String): Int = extractPackages(flat).size

    // ------------------------------------------------------------------
    
    // ------------------------------------------------------------------

    
    private fun flatten(cmd: Array<String?>): String {
        val joined = cmd.filterNotNull().joinToString(" ")
        return joined.replace(Regex("""\s+"""), " ").trim()
    }

    
    private fun isReadOnly(flat: String): Boolean {
        val writeOps = Regex(
            """\b(uninstall|disable-user|disable|suspend|hide|clear|force-stop|kill|kill-all|pkill|killall|rm|mv|chmod|chown|setprop|svc|reboot|grant|revoke)\b"""
        )
        if (writeOps.containsMatchIn(flat)) return false
        val readOps = Regex("""\b(pm\s+list|pm\s+path|dumpsys|ps|pidof|getprop|ls|cat|which|id|echo|grep)\b""")
        return readOps.containsMatchIn(flat)
    }

    
    private fun describeDanger(flat: String): String {
        val action = when {
            Regex("""\bpm\s+uninstall\b""").containsMatchIn(flat) -> "卸载应用"
            Regex("""\bpm\s+suspend\b""").containsMatchIn(flat) -> "冻结应用"
            Regex("""\bpm\s+hide\b""").containsMatchIn(flat) -> "隐藏应用"
            Regex("""\bpm\s+clear\b""").containsMatchIn(flat) -> "清除应用数据"
            Regex("""\bpm\s+disable""").containsMatchIn(flat) -> "停用应用"
            Regex("""\bdpm\b""").containsMatchIn(flat) -> "修改设备管理员"
            Regex("""\bpm\s+set-user-restriction\b""").containsMatchIn(flat) -> "修改用户限制策略"
            else -> "执行危险操作"
        }
        val targets = extractPackages(flat)
        return if (targets.isEmpty()) action else "$action（${targets.joinToString("、")}）"
    }

    
    private fun extractPackages(flat: String): List<String> {
        val pkgRegex = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+){2,})\b""")
        return pkgRegex.findAll(flat)
            .map { it.groupValues[1] }
            .filter {
                !it.startsWith("android.") &&
                    !it.startsWith("java.") &&
                    !it.startsWith("com.android.internal")
            }
            .distinct()
            .take(8)
            .toList()
    }
}
