package cn.imaq.realgps.xposed;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.List;

/**
 * Created by adn55 on 2017/2/9.
 */
public class HookPackage implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        XposedBridge.log("Load package: " + lpParam.packageName + " by " + lpParam.processName);
        XposedBridge.hookAllConstructors(LocationManager.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ((Context) param.args[0]).registerReceiver(
                        ZuobihiReceiver.getInstance(),
                        ZuobihiReceiver.intentFilter
                );
            }
        });
        new ZuobihiServer();

        // Providers related
        XC_MethodHook providersXC = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                List list = (List) param.getResult();
                if (!list.contains(LocationManager.GPS_PROVIDER)) {
                    list.add(LocationManager.GPS_PROVIDER);
                    param.setResult(list);
                }
            }
        };
        XposedHelpers.findAndHookMethod(LocationManager.class, "getAllProviders", providersXC);
        XposedBridge.hookAllMethods(LocationManager.class, "getProviders", providersXC);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getBestProvider", Criteria.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(LocationManager.GPS_PROVIDER);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProvider", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // TODO no ways yet
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "isProviderEnabled", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(LocationManager.GPS_PROVIDER)) {
                    param.setResult(true);
                }
            }
        });

        // GPS related
        XposedHelpers.findAndHookMethod(LocationManager.class, "addGpsStatusListener", GpsStatus.Listener.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ZuobihiReceiver.getInstance().gpsListeners.add((GpsStatus.Listener) param.args[0]);
                param.setResult(true);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "removeGpsStatusListener", GpsStatus.Listener.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ZuobihiReceiver.getInstance().gpsListeners.remove(param.args[0]);
                param.setResult(null);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "getGpsStatus", GpsStatus.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsGpsStatus());
            }
        });

        // Location related
        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastKnownLocation", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsLocation((String) param.args[0]));
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "requestLocationUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[3] instanceof LocationListener) {
                    ZuobihiReceiver.getInstance().locationListeners.add((LocationListener) param.args[3]);
                } else {
                    // TODO PendingIntent
                }
                param.setResult(null);
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "requestSingleUpdate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] instanceof LocationListener) {
                    String provider = LocationManager.GPS_PROVIDER;
                    if (param.args[0] instanceof String) {
                        provider = (String) param.args[0];
                    }
                    ((LocationListener) param.args[1]).onLocationChanged(ZuobihiReceiver.getInstance().getAsLocation(provider));
                } else {
                    // TODO PendingIntent
                }
                param.setResult(null);
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "removeUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] instanceof LocationListener) {
                    ZuobihiReceiver.getInstance().locationListeners.remove(param.args[0]);
                } else {
                    // TODO PendingIntent
                }
                param.setResult(null);
            }
        });

        // No use
        XC_MethodHook returnNull = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(null);
            }
        };
        XposedBridge.hookAllMethods(LocationManager.class, "addNmeaListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(false);
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "removeNmeaListener", returnNull);
        XposedHelpers.findAndHookMethod(LocationManager.class, "addProximityAlert", double.class, double.class, float.class, long.class, PendingIntent.class, returnNull);
        XposedHelpers.findAndHookMethod(LocationManager.class, "removeProximityAlert", PendingIntent.class, returnNull);
        XposedHelpers.findAndHookMethod(LocationManager.class, "sendExtraCommand", String.class, String.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        });

        if (Build.VERSION.SDK_INT >= 24) {
//            hookTest(lpParam, "addNmeaListener", OnNmeaMessageListener.class, Handler.class);
//            hookTest(lpParam, "registerGnssMeasurementsCallback", GnssMeasurementsEvent.Callback.class, Handler.class);
//            hookTest(lpParam, "registerGnssNavigationMessageCallback", GnssNavigationMessage.Callback.class, Handler.class);
//            hookTest(lpParam, "registerGnssStatusCallback", GnssStatus.Callback.class, Handler.class);
        }
    }

}
