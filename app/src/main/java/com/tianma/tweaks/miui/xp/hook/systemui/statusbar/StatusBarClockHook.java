package com.tianma.tweaks.miui.xp.hook.systemui.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.widget.TextView;

import com.tianma.tweaks.miui.utils.XLog;
import com.tianma.tweaks.miui.utils.XSPUtils;
import com.tianma.tweaks.miui.xp.hook.BaseSubHook;
import com.tianma.tweaks.miui.xp.hook.systemui.SystemUIHook;
import com.tianma.tweaks.miui.xp.hook.systemui.tick.TickObserver;
import com.tianma.tweaks.miui.xp.hook.systemui.tick.TimeTicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 状态栏时钟 Hook
 * 适用版本 9.4.x+
 */
public class StatusBarClockHook extends BaseSubHook implements TickObserver {

    private static final String PKG_STATUS_BAR = "com.android.systemui.statusbar";
    private static final String CLASS_CLOCK = PKG_STATUS_BAR + ".policy.Clock";
    private static final String CLASS_RECEIVER_INFO = CLASS_CLOCK + "$ReceiverInfo";
    private static final String CLASS_STATUS_BAR = PKG_STATUS_BAR + ".phone.StatusBar";
    private static final String CLASS_DEPENDENCY = "com.android.systemui.Dependency";

    private Class<?> mClockCls;

    private Set<Object> mClockViewSet = new HashSet<>();

    /**
     * 状态栏是否显示秒数
     */
    private boolean mShowSecInStatusBar;
    /**
     * 下拉状态栏是否显示秒数
     */
    private boolean mShowSecInDropdownStatusBar;
    /**
     * 是否自定义状态栏时间格式
     */
    private boolean mStatusBarClockFormatEnabled;
    private SimpleDateFormat mStatusBarClockFormat;

    /**
     * 是否自定义状态栏时间颜色
     */
    private boolean mStatusBarClockColorEnabled;
    /**
     * 状态栏时间颜色
     */
    private int mStatusBarClockColor;
    /**
     * 是否自定义下拉状态栏时钟颜色
     */
    private boolean mDropdownStatusBarClockColorEnabled;
    /**
     * 下拉状态栏时间颜色
     */
    private int mDropdownStatusBarClockColor;
    /**
     * 下拉状态栏日期颜色
     */
    private int mDropdownStatusBarDateColor;

    private boolean mBlockSystemTimeTick;

    private ArrayMap<String, Integer> mNameIdMap = new ArrayMap<>();

    public StatusBarClockHook(ClassLoader classLoader, XSharedPreferences xsp) {
        super(classLoader, xsp);

        mShowSecInStatusBar = XSPUtils.showSecInStatusBar(xsp);
        mStatusBarClockFormatEnabled = XSPUtils.isStatusBarClockFormatEnabled(xsp);
        mShowSecInDropdownStatusBar = XSPUtils.showSecInDropdownStatusBar(xsp);

        if (mStatusBarClockFormatEnabled) {
            String timeFormat = XSPUtils.getStatusBarClockFormat(xsp);
            mStatusBarClockFormat = new SimpleDateFormat(timeFormat, Locale.getDefault());
        }

        mStatusBarClockColorEnabled = XSPUtils.isStatusBarClockColorEnabled(xsp);
        if (mStatusBarClockColorEnabled) {
            mStatusBarClockColor = XSPUtils.getStatusBarClockColor(xsp);
        }

        mDropdownStatusBarClockColorEnabled = XSPUtils.isDropdownStatusBarClockColorEnabled(xsp);
        if (mDropdownStatusBarClockColorEnabled) {
            mDropdownStatusBarClockColor = XSPUtils.getDropdownStatusBarClockColor(xsp);
            mDropdownStatusBarDateColor = XSPUtils.getDropdownStatusBarDateColor(xsp);
        }

        mBlockSystemTimeTick = mShowSecInStatusBar || mShowSecInDropdownStatusBar;
    }

    @Override
    public void startHook() {
        try {
            XLog.d("Hooking StatusBar Clock...");
            mClockCls = XposedHelpers.findClass(CLASS_CLOCK, mClassLoader);

            hookClockConstructor();

            if (mShowSecInStatusBar || mStatusBarClockFormatEnabled || mShowSecInDropdownStatusBar) {
                if (mShowSecInStatusBar || mShowSecInDropdownStatusBar) {
                    hookStatusBar();
                    hookReceiverInfo();
                }
                hookClockUpdateClock();
            }

            if (mStatusBarClockColorEnabled) {
                hookOnDarkChanged();
            }
        } catch (Throwable t) {
            XLog.e("Error occurs when hook StatusBar Clock", t);
        }
    }

    // com.android.systemui.statusbar.policy.Clock#updateClock
    private void hookClockUpdateClock() {
        XposedHelpers.findAndHookMethod(mClockCls,
                "updateClock",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            TextView clockView = (TextView) param.thisObject;
                            Resources res = clockView.getResources();
                            int id = clockView.getId();
                            String timeStr = clockView.getText().toString();
                            if (id == getId(res, "clock")) {
                                if (mStatusBarClockFormatEnabled) {
                                    timeStr = getCustomFormatTime();
                                } else if (mShowSecInStatusBar) {
                                    timeStr = addInSecond(timeStr);
                                } else {
                                    return;
                                }
                            } else if (id == getId(res, "big_time")
                                    || id == getId(res, "date_time")) {
                                if (mShowSecInDropdownStatusBar) {
                                    timeStr = addInSecond(timeStr);
                                } else {
                                    return;
                                }
                            } else {
                                return;
                            }
                            clockView.setText(timeStr);
                        } catch (Throwable t) {
                            XLog.e("", t);
                        }
                    }
                });
    }

    private String addInSecond(String originalTimeStr) {
        int sec = Calendar.getInstance().get(Calendar.SECOND);
        String secStr = String.format(Locale.getDefault(), "%02d", sec);
        return originalTimeStr.replaceAll("(\\d+:\\d+)(:\\d+)?", "$1:" + secStr);
    }

    private String getCustomFormatTime() {
        return mStatusBarClockFormat.format(new Date());
    }

    private Integer getId(Resources res, String name) {
        if (!mNameIdMap.containsKey(name)) {
            int id = res.getIdentifier(name, "id", SystemUIHook.PACKAGE_NAME);
            mNameIdMap.put(name, id);
        }
        return mNameIdMap.get(name);
    }

    // com.android.systemui.statusbar.policy.Clock#access()
    private void hookClockConstructor() {
        XposedBridge.hookAllConstructors(mClockCls,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            TextView clock = (TextView) param.thisObject;
                            Resources res = clock.getResources();
                            int id = clock.getId();
                            if (id == getId(res, "clock")) {
                                if (mBlockSystemTimeTick) {
                                    addClock(clock);
                                }
                                if (mStatusBarClockColorEnabled) {
                                    clock.setTextColor(mStatusBarClockColor);
                                }
                            } else if (id == getId(res, "big_time")) {
                                if (mBlockSystemTimeTick) {
                                    addClock(clock);
                                }
                                if (mDropdownStatusBarClockColorEnabled) {
                                    clock.setTextColor(mDropdownStatusBarClockColor);
                                }
                            } else if (id == getId(res, "date_time")) {
                                if (mBlockSystemTimeTick) {
                                    addClock(clock);
                                }
                                if (mDropdownStatusBarClockColorEnabled) {
                                    clock.setTextColor(mDropdownStatusBarDateColor);
                                }
                            }
                        } catch (Throwable e) {
                            XLog.e("", e);
                        }
                    }
                });
    }

    private void hookOnDarkChanged() {
        if (mStatusBarClockColorEnabled) {
            XposedHelpers.findAndHookMethod(mClockCls,
                    "onDarkChanged",
                    Rect.class,
                    float.class,
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            return null;
                        }
                    });
        }
    }

    private void hookStatusBar() {
        hookMakeStatusBarView();
    }

    // com.android.systemui.statusbar.phone.StatusBar#makeStatusBarView()
    private void hookMakeStatusBarView() {
        XposedHelpers.findAndHookMethod(CLASS_STATUS_BAR,
                mClassLoader,
                "makeStatusBarView",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object statusBar = param.thisObject;
                            Context context = (Context) XposedHelpers.getObjectField(statusBar, "mContext");

                            // register receiver
                            IntentFilter filter = new IntentFilter();
                            filter.addAction(Intent.ACTION_SCREEN_ON);
                            filter.addAction(Intent.ACTION_SCREEN_OFF);
                            context.registerReceiver(mScreenReceiver, filter);
                        } catch (Throwable t) {
                            XLog.e("", t);
                        }
                    }
                });
    }

    private synchronized void addClock(Object clock) {
        if (mClockViewSet.isEmpty()) {
            TimeTicker.get().registerObserver(StatusBarClockHook.this);
        }

        mClockViewSet.add(clock);
    }

    @Override
    public void onTimeTick() {
        for (Object clockView : mClockViewSet) {
            if (clockView != null) {
                XposedHelpers.callMethod(clockView, "updateClock");
            }
        }
    }

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                TimeTicker.get().registerObserver(StatusBarClockHook.this);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                TimeTicker.get().unregisterObserver(StatusBarClockHook.this);
            }
        }
    };

    // com.android.systemui.statusbar.policy.Clock$ReceiverInfo
    private void hookReceiverInfo() {
        hookRegister();
    }

    // com.android.systemui.statusbar.policy.Clock$ReceiverInfo#register()
    private void hookRegister() {
        XposedHelpers.findAndHookMethod(CLASS_RECEIVER_INFO,
                mClassLoader,
                "register",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object thisObject = param.thisObject;
                            Context context = (Context) param.args[0];
                            IntentFilter filter = new IntentFilter();
                            // 不再接收 TIME_TICK 广播
                            // filter.addAction("android.intent.action.TIME_TICK");
                            filter.addAction("android.intent.action.TIME_SET");
                            filter.addAction("android.intent.action.TIMEZONE_CHANGED");
                            filter.addAction("android.intent.action.CONFIGURATION_CHANGED");
                            filter.addAction("android.intent.action.USER_SWITCHED");
                            Object receiver = XposedHelpers.getObjectField(thisObject, "mReceiver");
                            Object USER_HANDLE_ALL = XposedHelpers.getStaticObjectField(UserHandle.class, "ALL");
                            Class<?> dependencyClass = XposedHelpers.findClass(CLASS_DEPENDENCY, mClassLoader);
                            Object TIME_TICK_HANDLER = XposedHelpers.getStaticObjectField(dependencyClass, "TIME_TICK_HANDLER");
                            Object handler = XposedHelpers.callStaticMethod(dependencyClass, "get", TIME_TICK_HANDLER);
                            // context.registerReceiverAsUser(this.mReceiver, UserHandle.ALL, filter, null, (Handler) Dependency.get(Dependency.TIME_TICK_HANDLER));
                            XposedHelpers.callMethod(context,
                                    "registerReceiverAsUser",
                                    receiver,
                                    USER_HANDLE_ALL,
                                    filter,
                                    null,
                                    handler);
                            param.setResult(null);
                        } catch (Throwable t) {
                            XLog.e("", t);
                        }
                    }
                });
    }
}
