package ting.demo.tsensorcalibrationactivity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.DhcpInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

import com.msi.android.SysMgr;
import com.msi.android.system.SystemManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;


public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String LOGTAG = MainActivity.class.getName();

    private SystemManager systemManager;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private WifiManager wifiManager;

    private SensorManager tSensorManager;
    private Sensor tSensor;

    private TextView uiMessageCalibration;
    private TextView uiMessageSensorEvent;

    private CalibrationTask calibrationTask;
    private final Object DELAY = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOGTAG, "onCreate");

        setContentView(R.layout.activity_main);
        uiMessageCalibration = (TextView) findViewById(R.id.id_message);
        uiMessageSensorEvent = (TextView) findViewById(R.id.id_message2);

        systemManager = new SystemManager();
//        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        tSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        tSensor = tSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        tSensorManager.registerListener(this, tSensor,SensorManager.SENSOR_DELAY_NORMAL);

        Log.i(LOGTAG, "setPsensorAutoWakeUpScreenEnabled: false");
        systemManager.setPsensorAutoWakeUpScreenEnabled(this, false);

        connectWifi();

        getWifiApIpAddress();
        getApIpAddr();

        //turnOffScreen();

        calibrationTask = new CalibrationTask(DELAY, uiMessageCalibration);
        calibrationTask.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(LOGTAG, "onDestroy");

        tSensorManager.unregisterListener(this);

        Log.i(LOGTAG, "setPsensorAutoWakeUpScreenEnabled: true");
        systemManager.setPsensorAutoWakeUpScreenEnabled(this, true);

        //turnOnScreen();

//        if (wakeLock != null) {
//            wakeLock.release();
//            wakeLock = null;
//        }

        if (calibrationTask != null) {
            calibrationTask.cancel(true);
        }

        synchronized (DELAY) {
            DELAY.notifyAll();
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.i(LOGTAG, "onKeyUp: BACK");
            new AlertDialog.Builder(this)
                    .setTitle("Exit Application")
                    .setMessage("Do you want to exit this application?")
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(LOGTAG, "exit application dialog: exit pressed");
                            MainActivity.this.finish();
                        }
                    })
                    .setNegativeButton("Stay Here", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.i(LOGTAG, "exit application dialog: stay here pressed");
                        }
                    })
                    .show();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    //    private void turnOffScreen() {
//        try {
//            Log.i(LOGTAG, "turnOffScreen");
//            powerManager.getClass().getMethod("goToSleep", new Class[]{long.class}).invoke(powerManager, SystemClock.uptimeMillis());
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        }
//    }
//
//    private void turnOnScreen() {
//        Log.i(LOGTAG, "turnOnScreen");
//        wakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
//                | PowerManager.SCREEN_DIM_WAKE_LOCK, LOGTAG);
//        wakeLock.acquire();
//    }

    /*
    *    Get Wifi self-local IP
    **/
    private String getWifiApIpAddress() {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements();) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                if (intf.getName().contains("wlan")) {
                    for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr
                            .hasMoreElements();) {
                        InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()
                                && (inetAddress.getAddress().length == 4)) {
                            Log.d(LOGTAG, "get SSID's IP: " + inetAddress.getHostAddress());
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(LOGTAG, ex.toString());
        }
        return null;
    }

    /*
    *   Get Wifi AP IP
    * */
    private byte[] convert2Bytes(int hostAddress) {
        byte[] addressBytes = { (byte)(0xff & hostAddress),
                (byte)(0xff & (hostAddress >> 8)),
                (byte)(0xff & (hostAddress >> 16)),
                (byte)(0xff & (hostAddress >> 24)) };
        return addressBytes;
    }

    private String getApIpAddr() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        byte[] ipAddress = convert2Bytes(dhcpInfo.serverAddress);
        try {
            String apIpAddr = InetAddress.getByAddress(ipAddress).getHostAddress();
            Log.d(LOGTAG, "get SSID's IP (2): " + apIpAddr);
            return apIpAddr;
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
    *   Auto connect to the specific wifi SSID/password
    **/
    private static final int WIFICIPHER_NOPASS = 0;
    private static final int WIFICIPHER_WEP = 1;
    private static final int WIFICIPHER_WPA = 2;

    private WifiConfiguration createWifiConfig(String ssid, String password, int type) {
        WifiConfiguration config = new WifiConfiguration();
//        config.allowedAuthAlgorithms.clear();
//        config.allowedGroupCiphers.clear();
//        config.allowedKeyManagement.clear();
//        config.allowedPairwiseCiphers.clear();
//        config.allowedProtocols.clear();

        config.SSID = "\"" + ssid + "\"";

        WifiConfiguration tempConfig = isExist(ssid);
        if(tempConfig != null) {
            wifiManager.removeNetwork(tempConfig.networkId);
        }

        if (type == WIFICIPHER_NOPASS) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        } else if(type == WIFICIPHER_WEP) {
            config.hiddenSSID = true;
            config.wepKeys[0]= "\""+password+"\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        } else if(type == WIFICIPHER_WPA) {
            config.preSharedKey = "\""+password+"\"";
            config.hiddenSSID = true;
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
        }

        return config;
    }

    private WifiConfiguration isExist(String ssid) {
        List<WifiConfiguration> configs = wifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {
            if (config.SSID.equals("\""+ssid+"\"")) {
                return config;
            }
        }
        return null;
    }

    private void connectWifi() {
        wifiManager.setWifiEnabled(true);

        int wifiId = wifiManager.addNetwork(createWifiConfig("sw2_ipv6_2.4G", "21178113", WIFICIPHER_WPA));
        Log.i(LOGTAG, "connectWifi: netId: " + wifiId);
        if (wifiId != -1) {
            wifiManager.saveConfiguration();
            boolean enable = wifiManager.enableNetwork(wifiId, true);
            Log.i(LOGTAG, "connectWifi: enable: " + enable);
        }

    }

    /*
    *   SensorEventListener callbacks
    * */

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(tSensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            float tSensorEventValue = event.values[0];
            Log.d(LOGTAG, "SensorEventListener.onSensorChanged: " + tSensorEventValue);
            uiMessageSensorEvent.setText("sensor event: "+tSensorEventValue);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /*
    *  Task of calibration
    * */

    private static class CalibrationTask extends AsyncTask<Object, Object, Object> {
        private final static String LOGTAG = CalibrationTask.class.getName();

        private final Object DELAYOBJ;

        private TextView uiMessage;

        public CalibrationTask(Object delayObj, TextView uiMessageCalibration) {
            DELAYOBJ = delayObj;
            uiMessage = uiMessageCalibration;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(LOGTAG, "onPreExecute");
            uiMessage.setText("calibration start");
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            super.onProgressUpdate(values);
            String message = (String)values[0];
            Log.i(LOGTAG, "onProgressUpdate: " + message);
            uiMessage.setText(message);
        }

        @Override
        protected void onCancelled(Object o) {
            super.onCancelled(o);
            Log.i(LOGTAG, "onCancelled");
            uiMessage.setText("calibration cancelled");
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            Log.i(LOGTAG, "onCancelled");
            uiMessage.setText("calibration cancelled");
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            String result = (String)o;
            Log.i(LOGTAG, "onPostExecute: " + result);
            uiMessage.setText("calibration end, " + result);
        }

        private final static int MAX_RETRY_NUMBER_OF_CALIBRATION = 3;

        @Override
        protected Object doInBackground(Object... objects) {
            Log.i(LOGTAG, "doInBackground");

            String result = null;
            for (int i = 0 ; i < MAX_RETRY_NUMBER_OF_CALIBRATION ; i++) {
                if (isCancelled()) {
                    Log.i(LOGTAG, "doInBackground: task canceled!");
                    break;
                }

                result = doCalibration();
                if (result.contains(CALIBRATION_RESULT_OK)) {
                    break;
                } else {
                    publishProgress(result);
                }
            }
            return result;
        }

        private final static String Calibration_file ="/device/TSensor.txt";/* calibration parameter */
        private final static String echoPath = "/sys/bus/i2c/drivers/tsm1a103/1-0050/tsm1a103_calibration";
        private final static String catPath = "cat /sys/bus/i2c/drivers/tsm1a103/1-0050/t_value";

        private final static String CALIBRATION_RESULT_OK = "calibration ok";
        private final static String CALIBRATION_RESULT_FAIL_CASE_1 = "calibration fail: can't reach heat balance!";
        private final static String CALIBRATION_RESULT_FAIL_CASE_2 = "calibration fail: server IP cannot be parsed!";
        private final static String CALIBRATION_RESULT_FAIL_CASE_3 = "calibration fail: socket IO error!";
        private final static String CALIBRATION_RESULT_FAIL_CASE_4 = "calibration fail: user cancel";

        private final static double TOLERABLE_DIFF_VALUE = 0.3f*10f; // device temperature is format as 201.0 which means 20.1 degrees C.

        private String doCalibration() {
            //
            //  total 30 mins, each 5 min, record t-sensor raw data.
            //
            final int COUNTS_6 = 6;
//            double[] rawDataValues = new double[COUNTS_6];
            for (int count = 0 ; count < COUNTS_6 ; count++) {
                if (isCancelled()) {
                    Log.i(LOGTAG, "doCalibration: user cancel");
                    return CALIBRATION_RESULT_FAIL_CASE_4;
                }
                try { synchronized (DELAYOBJ) { DELAYOBJ.wait(1000*60*5); /*5 min*/ } } catch (InterruptedException e) { e.printStackTrace(); }
                double rawDataValue = Double.parseDouble(SysMgr.nativeSetProp("excuteSystemCmdWithResult", catPath));
//                rawDataValues[count++] = rawDataValue;
                Log.i(LOGTAG, "doCalibration: get device raw data temperature = " + rawDataValue);
            }

            //
            //  check if heat balance: 1 second per one time, total 60 times
            //
            final int COUNTS_60 = 60;
            double previousValue = Double.parseDouble(SysMgr.nativeSetProp("excuteSystemCmdWithResult", catPath));
            double [] diffValues = new double [COUNTS_60];
            for (int count = 0 ; count < COUNTS_60 ; count++) {
                try { synchronized (DELAYOBJ) { DELAYOBJ.wait(1000); /*1 sec*/ } } catch (InterruptedException e) { e.printStackTrace(); }
                double value = Double.parseDouble(SysMgr.nativeSetProp("excuteSystemCmdWithResult", catPath));
                diffValues[count] = Math.abs(value-previousValue);
                Log.i(LOGTAG, "doCalibration: get device raw data temperature = " + value + " / " + diffValues[count] + " (check heat balance)");
                previousValue = value;
            }
            double diff = Arrays.stream(diffValues).average().orElse(Double.NaN);
            Log.i(LOGTAG, "doCalibration: check heat balance, diff = " + diff);
            if (diff > TOLERABLE_DIFF_VALUE) {
                Log.i(LOGTAG, "doCalibration: check heat balance fail! " + CALIBRATION_RESULT_FAIL_CASE_1);
                return CALIBRATION_RESULT_FAIL_CASE_1;
            }
//            double deviceTemperature = Arrays.stream(rawDataValues).average().orElse(Double.NaN);

            //
            // fetch server temperature
            //
            InetAddress serverIp = null;
            try { serverIp = InetAddress.getByName("192.168.0.1");
            } catch (UnknownHostException e) {
                e.printStackTrace();
                return CALIBRATION_RESULT_FAIL_CASE_2;
            }
            int serverPort = 5050;
            Socket clientSocket = null;
            BufferedWriter bw = null;
            BufferedReader br = null;
            double serverTemperature = 0;
            try {
                clientSocket = new Socket(serverIp, serverPort);
                bw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                boolean temperatureObtained = false;
                int retryCount = 0;
                while (clientSocket.isConnected()) {
                    // todo: write to socket
                    Log.i(LOGTAG, "doCalibration: fetch server temperature: write GET_TEMP");
                    bw.write("GET_TEMP");
                    bw.flush();
                    // todo: read from socket
                    String line;
                    while ((line = br.readLine()) != null) {
                        Log.i(LOGTAG, "doCalibration: fetch server temperature: read " + line);
                        // parse line, its format must be TEMP: xxx
                        String[] subStrings = line.split("TEMP: ");
                        serverTemperature = Double.parseDouble(subStrings[1]);
                        Log.i(LOGTAG, "doCalibration: fetch server temperature: obtain temperature = " + serverTemperature);
                        temperatureObtained = true;
                        break;
                    }
                    if (temperatureObtained || retryCount >= 3) {
                        break;
                    }
                    retryCount++;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return CALIBRATION_RESULT_FAIL_CASE_3;
            } finally {
                try { bw.close(); } catch (NullPointerException e) { e.printStackTrace(); } catch (IOException e) { e.printStackTrace(); }
                try { br.close(); } catch (NullPointerException e) { e.printStackTrace(); } catch (IOException e) { e.printStackTrace(); }
                try { clientSocket.close(); } catch (NullPointerException e) { e.printStackTrace(); } catch (IOException e) { e.printStackTrace(); }
            }

            //
            // do calibration between device raw data and server temperature
            //
            double deviceTemperature = Double.parseDouble(SysMgr.nativeSetProp("excuteSystemCmdWithResult", catPath));
            Log.i(LOGTAG, "doCalibration: get device raw data temperature = " + deviceTemperature);

            // TODO: need double check double to int may lose data?
            int nor_average = (int)serverTemperature;
            int dev_average = (int)deviceTemperature;

            String write_data = "" + nor_average + "," + dev_average + "," + (nor_average - dev_average) + ",";
            Log.d(LOGTAG,"doCalibration: write_data = " + write_data);

            SysMgr.nativeSetProp("excuteSystemCmdWithResult", "echo " + write_data + " > " + echoPath).trim();
            SysMgr.nativeSetProp("W_DEVICE:" + Calibration_file, write_data);

            Log.d(LOGTAG,"doCalibration: success");
            return CALIBRATION_RESULT_OK;
        }

    }
}

// NOTICE: both device(t-sensor) & server must be make sure it temperature format is correct!
//              client side(app itself) do not check it!

// TODO: sync "connect to wifi" code with Diag.
// TODO: turn LED on/off, not device sleep/resume, only screen on/off