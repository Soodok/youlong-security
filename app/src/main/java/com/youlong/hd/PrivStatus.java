package com.youlong.hd;


final class PrivStatus {

    private PrivStatus() {}

    
    static final class Snapshot {
        final boolean available;
        final boolean granted;
        final String uidOutput;
        final String idOutput;
        
        final String source;
        
        final boolean shizukuAlive;
        
        final boolean shizukuGranted;

        Snapshot(boolean available, boolean granted, String uidOutput, String idOutput,
                 String source, boolean shizukuAlive, boolean shizukuGranted) {
            this.available = available;
            this.granted = granted;
            this.uidOutput = uidOutput;
            this.idOutput = idOutput;
            this.source = source;
            this.shizukuAlive = shizukuAlive;
            this.shizukuGranted = shizukuGranted;
        }

        
        String summary() {
            return "连接=" + available + " 授权=" + granted
                    + " 来源=" + source
                    + " uid=" + uidOutput.replace('\n', ' ').trim()
                    + " id=" + idOutput.replace('\n', ' ').trim()
                    + " | Shizuku在线=" + shizukuAlive + " Shizuku授权=" + shizukuGranted;
        }
    }

    
    static String rawExec(String cmd) {
        try {
            java.lang.Process p = roro.stellar.Stellar.INSTANCE.newProcess(
                    new String[]{"sh", "-c", cmd}, null, null);
            if (p == null) return "(newProcess 返回 null)";
            return readAll(p);
        } catch (Throwable t) {
            return "EX:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    
    static String rawExecShizuku(String cmd) {
        try {
            if (!ShizukuBackend.isBinderAlive()) return "(Shizuku 未连接)";
            java.lang.Process p = ShizukuBackend.newProcess(
                    new String[]{"sh", "-c", cmd}, null, null);
            if (p == null) return "(newProcess 返回 null)";
            return readAll(p);
        } catch (Throwable t) {
            return "EX:" + t.getClass().getSimpleName() + ":" + t.getMessage();
        }
    }

    private static String readAll(java.lang.Process p) throws Exception {
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
        StringBuilder out = new StringBuilder();
        String line;
        int n = 0;
        while ((line = r.readLine()) != null && n < 20) {
            out.append(line).append(' ');
            n++;
        }
        r.close();
        p.waitFor();
        String s = out.toString().trim();
        return s.isEmpty() ? "(无输出)" : s;
    }

    
    static boolean requestReconnect(android.content.Context ctx) {
        try {
            PrivRouter.requestReconnect(ctx);
            CrashLogger.event("[特权面板] 已请求服务端重新投递 Binder");
            return true;
        } catch (Throwable t) {
            CrashLogger.event("[特权面板] 请求重投 Binder 失败", t);
            return false;
        }
    }

    
    static Snapshot collect() {
        boolean available = false;
        boolean granted = false;
        boolean shizukuAlive = false;
        boolean shizukuGranted = false;
        String source = "未知";
        String uid = "(未取到)";
        String id = "(未取到)";
        try {
            
            
            available = StellarUtils.isPrivilegeBinderAlive();
            shizukuAlive = PrivRouter.shizukuAlive();
            source = PrivRouter.activeName(YouLongApp.instance());
            granted = available && StellarUtils.hasStellarPermission();
            if (shizukuAlive) shizukuGranted = ShizukuBackend.hasPermission();
            if (granted) {
                uid = StellarUtils.runCommand("id -u", 8000);
                id = StellarUtils.runCommand("id", 12000);
            } else {
                uid = "(未授权)";
                id = "(未授权)";
            }
        } catch (Throwable t) {
            id = "ERR:" + t;
        }
        return new Snapshot(available, granted, uid, id, source, shizukuAlive, shizukuGranted);
    }
}
