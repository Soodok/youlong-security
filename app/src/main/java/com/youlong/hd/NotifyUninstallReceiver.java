package com.youlong.hd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;


public class NotifyUninstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String pkg = intent.getStringExtra("target_pkg");
        if (pkg == null || pkg.isEmpty()) return;

        
        final Context appCtx = context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        if (pkg.equals(appCtx.getPackageName())
                || "com.youlong.hd".equals(pkg)
                || "com.youlong.zoo".equals(pkg)
                || "com.youlong.tool".equals(pkg)
                || WhitelistActivity.isWhitelisted(appCtx, pkg)) {
            android.util.Log.w("NotifyUninstall", "受保护/白名单应用，拒绝卸载: " + pkg);
            try {
                Toast.makeText(appCtx, "该应用在白名单中，已跳过卸载", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
            return;
        }

        
        try {
            Intent u = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            u.setData(Uri.parse("package:" + pkg));
            u.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            u.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(u);
        } catch (Exception ignored) {}

        
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent s = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    s.setData(Uri.parse("package:" + pkg));
                    s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(s);
                } catch (Exception ignored) {}
            }
        }, 5000);
    }
}
