package cn.imaq.realgps.xposed;

import android.content.Context;
import android.content.Intent;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adn55 on 2017/2/10.
 */
public class ZuobihiServer {

    private Socket csocket;

    private int[] prn = new int[32];
    private float[] snr = new float[32];
    private float[] elv = new float[32];
    private float[] azm = new float[32];

    private Pattern locPattern = Pattern.compile("\\[LOC:(.*?),(.*?),(.*?),(.*?),(.*?),(.*?),(.*?);");
    private Pattern satPattern = Pattern.compile("SAT:(.*?),(.*?),(.*?),(.*?);");
    private Intent intent = new Intent(ZuobihiReceiver.action);
    private Context context;

    public ZuobihiServer(Context context, final ServerSocket ssocket) {
        this.context = context;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        Socket socket = ssocket.accept();
                        if (csocket != null) {
                            csocket.close();
                            // XposedBridge.log("ZuobihiServer: closed old connection");
                        }
                        csocket = socket;
                        new Thread(new ServerThread()).start();
                    }
                } catch (Throwable ignored) {
                }
            }
        }).start();
    }

    private synchronized boolean parseData(String s) {
        // [LOC:$lat,$lng,$alt,$speed,$bearing,$accr,$time;
        // SAT:${sat.PRN},${sat.snr},${sat.elv},${sat.azm};]
        try {
            s = s.substring(0, s.indexOf(']'));
            Matcher locMatcher = locPattern.matcher(s);
            if (locMatcher.find() && locMatcher.groupCount() == 7) {
                intent.putExtra("lat", Double.parseDouble(locMatcher.group(1)));
                intent.putExtra("lng", Double.parseDouble(locMatcher.group(2)));
                intent.putExtra("alt", Double.parseDouble(locMatcher.group(3)));
                intent.putExtra("speed", Float.parseFloat(locMatcher.group(4)));
                intent.putExtra("bearing", Float.parseFloat(locMatcher.group(5)));
                intent.putExtra("accr", Float.parseFloat(locMatcher.group(6)));
                intent.putExtra("time", Long.parseLong(locMatcher.group(7)));

                int svCount = 0, mask = 0;
                Matcher satMatcher = satPattern.matcher(s);
                while (satMatcher.find()) {
                    if (satMatcher.groupCount() == 4) {
                        prn[svCount] = Integer.parseInt(satMatcher.group(1));
                        snr[svCount] = Float.parseFloat(satMatcher.group(2));
                        elv[svCount] = Float.parseFloat(satMatcher.group(3));
                        azm[svCount] = Float.parseFloat(satMatcher.group(4));
                        mask |= (1 << (prn[svCount] - 1));
                        svCount++;
                        if (svCount >= 32) {
                            break;
                        }
                    }
                }

                // XposedBridge.log("ZuobihiServer: Data parsed, containing " + svCount + " satellites");
                if (svCount > 0) {
                    intent.putExtra("svCount", svCount);
                    intent.putExtra("prn", prn);
                    intent.putExtra("snr", snr);
                    intent.putExtra("elv", elv);
                    intent.putExtra("azm", azm);
                    intent.putExtra("mask", mask);
                    context.sendBroadcast(intent);
                    // XposedBridge.log("ZuobihiServer: broadcast sent");
                }
            }
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private class ServerThread implements Runnable {
        @Override
        public void run() {
            // XposedBridge.log("ZuobihiServer: opened new connection");
            try {
                InputStream input = csocket.getInputStream();
                byte[] buf = new byte[4096];
                String data = "";
                while (true) {
                    int len = input.read(buf);
                    if (len < 0) {
                        break;
                    }
                    String s = new String(buf, 0, len);
                    if (!data.isEmpty() || s.startsWith("[")) {
                        data += s;
                        if (parseData(data) || data.length() > 8192) {
                            data = "";
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            // XposedBridge.log("ZuobihiServer: connection closed");
        }
    }

}
