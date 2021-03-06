package com.tianma.tweaks.miui.xp.hook;


import com.tianma.tweaks.miui.BuildConfig;
import com.tianma.tweaks.miui.utils.ModuleUtils;
import com.tianma.tweaks.miui.utils.XLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook class com.github.tianma8023.xposed.smscode.utils.ModuleUtils
 */
public class ModuleUtilsHook extends BaseHook {

    private static final String SMSCODE_PACKAGE = BuildConfig.APPLICATION_ID;

    @Override
    public void onLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (SMSCODE_PACKAGE.equals(lpparam.packageName)) {
            try {
                XLog.i("Hooking current Xposed module status...");
                hookModuleUtils(lpparam);
            } catch (Throwable e) {
                XLog.e("Failed to hook current Xposed module status.");
            }
        }

    }

    private void hookModuleUtils(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String className = ModuleUtils.class.getName();

        XposedHelpers.findAndHookMethod(className, lpparam.classLoader,
                "isModuleActive",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.setResult(true);
                    }
                });
    }

}
