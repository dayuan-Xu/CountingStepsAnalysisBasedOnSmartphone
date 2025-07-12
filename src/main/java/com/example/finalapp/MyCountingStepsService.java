package com.example.finalapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyCountingStepsService extends Service implements SensorEventListener {
    private static final String TAG = "我的日志-CountingStepsService";
    private static final int NOTIFICATION_ID = 102;// 通知ID

    private Handler handler = new Handler();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private final List<String> sensorData = new ArrayList<>();
    private boolean isCounting = false;
    private long lastTimestamp = 0;
    private String startTime;
    private static final long SAMPLE_INTERVAL = 20; // 50Hz = 20ms
    private static final int SAMPLING_PERIOD_MICRO_SECONDS = 5 * 1000; // 200Hz
    private PeakSelfAdapt2 peakDetector = new PeakSelfAdapt2();
    private MainActivity callback;

    @Override
    public void onCreate() {
        super.onCreate();
        // 创建点击通知时打开主界面的Intent
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP); // 确保不会创建新的 Activity 实例
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, MyLocationService.CHANNEL_ID)
                .setContentTitle("计步，轨迹跟踪中...")
                .setContentText("点击回到主界面")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        // 成为前台服务（防止被系统杀死）
        startForeground(NOTIFICATION_ID, notification);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // 开始监听传感器并计步
        startCounting();
    }


    public void startCounting() {
        if (accelerometer == null) {
            Toast.makeText(this, "设备不支持加速度传感器!", Toast.LENGTH_SHORT).show();
            return;
        }
        startTime = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(new Date());
        sensorData.clear();
        sensorData.add("x,y,z,timestamp");
        // 注册传感器监听器
        sensorManager.registerListener(this, accelerometer, SAMPLING_PERIOD_MICRO_SECONDS);

        // 设置计步标志,允许计步算法处理传感器数据
        isCounting = true;
        lastTimestamp = System.currentTimeMillis();
    }

    public void stopCounting() {
        if (isCounting) {
            sensorManager.unregisterListener(this);
            isCounting = false;
            saveToCSV();
            peakDetector.stop();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 在处于计步状态时，才处理传感器数据
        if (!isCounting || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER)
            return;

        long currentTime = System.currentTimeMillis();
        // 检查是否达到50Hz采样间隔
        if (currentTime - lastTimestamp >= SAMPLE_INTERVAL) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            sensorData.add(String.format(Locale.US, "%.6f,%.6f,%.6f,%d", x, y, z, currentTime));
            lastTimestamp = currentTime;

            // 处理传感器数据
            peakDetector.processSensorData(currentTime, event.values);

            // 如果检测到新步数，通知订阅者更新步数显示
            if (callback != null && peakDetector.newValleyDetected) {
                // 如果检测到新的一步，通知订阅者更新步数显示
                handler.post(() -> callback.updateStepsDisplay());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void saveToCSV() {
        if (sensorData.size() <= 1) return;

        String endTime = new SimpleDateFormat("MM-dd-HH-mm-ss", Locale.getDefault()).format(new Date());
        String fileName = "AccData_" + startTime + "_To_" + endTime + ".csv";

        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(downloadsDir, fileName);

        try (FileWriter writer = new FileWriter(file)) {
            for (String line : sensorData) {
                writer.append(line).append("\n");
            }
            Toast.makeText(this, "数据已保存到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public void setCallback(MainActivity mainActivity) {
        this.callback = mainActivity;
    }

    public void clearCallback() {
        this.callback = null;
    }

    public boolean isCounting() {
        return isCounting;
    }


    public class LocalBinder extends Binder {
        MyCountingStepsService getService() {
            return MyCountingStepsService.this;
        }
    }

    public int getCurrentSteps() {
        return peakDetector.getCurrentSteps();
    }

    public int getWalkingSteps() {
        return peakDetector.getWalkingSteps();
    }

    public int getRunningSteps() {
        return peakDetector.getRunningSteps();
    }
}
