package com.example.finalapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.utils.DistanceUtil;
import com.example.finalapp.room.AppDatabase;
import com.example.finalapp.room.Record;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;


public class MyLocationService extends Service {
    private static final String TAG = "我的日志-MyLocationService";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final long UPDATE_INTERVAL = 1000; // 1秒更新间隔
    private Runnable durationUpdateTask; // 时间更新任务
    private Runnable monitorTask;// GPS信号监控任务

    private static final int NOTIFICATION_ID = 101;// 通知ID
    public static final String CHANNEL_ID = "MyLocationServiceChannel";
    private static final int MAX_WAIT_TIMES = 5;// 最大尝试次数5

    // 定位相关
    private LocationClient mLocationClient;
    private BDAbstractLocationListener myLocationListener;

    // 轨迹跟踪的相关数据
    private long trackingStartTimeMillis;
    private long trackingEndTimeMills;
    private boolean isTracking = false;
    private final List<LatLng> mTrajectoryPoints = new ArrayList<>();
    private double totalDistance = 0.0;

    // 绑定接口
    private final IBinder binder = new LocalBinder();

    public long lastLocationTime = 0;// 值为-1表示定位开启后还没有收到第一个位置信息
    // 保存界面对象
    private MainActivity callback;
    private int locationAttempts;
    private LatLng last = null;
    private final List<LatLng> points = new ArrayList<>();
    private long locationClientStartedTime = -1;

    public boolean isTracking() {
        return isTracking;
    }

    public void setCallback(MainActivity mainActivity) {
        callback = mainActivity;
    }

    public void clearCallback() {
        callback = null;
    }

    public int getLocationAttempts() {
        return locationAttempts;
    }

    public int getMaxWaitTime() {
        return MAX_WAIT_TIMES;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public List<LatLng> getTrajectoryPoints() {
        return mTrajectoryPoints;
    }

    public void pauseLocationUpdates() {
        // 仅允许在主界面失去焦点并且不处于轨迹跟踪中时暂停位置更新
        if (isTracking == false) {
            mLocationClient.stop();
            Log.d(TAG, "定位服务已暂停");
        } else {
            Log.e(TAG, "当前处于轨迹跟踪，不允许暂停定位客户端");
        }
    }

    public void resumeLocationUpdates() {
        if (mLocationClient != null && !mLocationClient.isStarted()) {
            mLocationClient.start();
            lastLocationTime = -1;
            locationClientStartedTime = System.currentTimeMillis();
            Log.d(TAG, "定位服务已恢复");
            if (callback != null) {
                mainHandler.post(() -> callback.updateLogDisplay("已重启定位，正在等待第一个位置信息..."));
            }
        }
    }


    public class LocalBinder extends Binder {
        MyLocationService getService() {
            return MyLocationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // 创建通知渠道
        createNotificationChannel();

        Notification notification = createNotification();

        if (notification == null) {
            Log.e(TAG, "通知构建失败！");
        } else {
            try {
                // 启动前台服务
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception e) {
                Log.e(TAG, "启动前台服务失败", e);
            }
        }

        // 开启子线程执行耗时操作，防止阻塞主线程（服务的onCreate()方法是在主线程中执行的）
        Executors.newSingleThreadExecutor().execute(() -> {
            // 定位初始化（在后台线程）
            initLocationAndStart();
            Log.d(TAG, "百度地图定位SDK子线程中----已经调用百度地图定位客户端的启动方法");
        });

        // GPS监控（在后台线程）
        startGpsMonitoring();
        Log.d(TAG, "MyLocationService实例已创建");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "本应用通知渠道",
                NotificationManager.IMPORTANCE_HIGH // 提高重要性
        );
        // 设置通知颜色
        channel.setLightColor(getResources().getColor(R.color.notification_color));
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        // 创建通知渠道
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        // 创建点击通知
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("定位服务已开启")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .build();
    }


    // 自定义定位监听器
    class MyLocationListener extends BDAbstractLocationListener {
        // 此方法由启动的定位客户端的线程池中的线程调用
        // 直接使用callback存在线程风险，因为UI只能由主线程操作
        // 所有需要使用Handler往主线程负责处理的消息队列中添加UI更新任务
        @Override
        public void onReceiveLocation(BDLocation location) {
            // 检查位置信息是否有效
            if (location == null) return;

            if (location.getLocType() == BDLocation.TypeGpsLocation) {
                String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());

                if (lastLocationTime == -1) {
                    Log.d(TAG, timestamp + "-本次开启定位后第一次收到GPS定位");
                } else {
                    Log.d(TAG, "😀😀😀 Received new location！本次定位结果类型为" + location.getLocType());
                }

                // 更新上次收到新位置的时间
                lastLocationTime = System.currentTimeMillis();

                LatLng currentPoint = new LatLng(location.getLatitude(), location.getLongitude());
                float accuracy = location.getRadius();
                float speed = location.getSpeed();

                // 切换到主线程（在主线程中处理UI更新消息）
                if (callback != null) {
                    mainHandler.post(() -> {
                        // 更新UI: 显示日志，在地图上显示最新位置点
                        callback.updateLocationOnMap(currentPoint, speed, accuracy);
                        callback.updateLogDisplay(timestamp + "-收到GPS定位，" + location.getLocTypeDescription());
                    });
                }

                // 根据速度大小动态调整扫描间隔
                adjustScanSpanBasedOnSpeed(speed);

                // 如果正在追踪轨迹
                if (isTracking) {
                    if (callback != null) {
                        callback.updateSpeedDisplay(speed);
                    }

                    // 先尝试找到最精确的轨迹起点
                    if (mTrajectoryPoints.isEmpty()) {
                        LatLng startPoint = getMostAccuracyLocation(location);
                        if (startPoint != null) {
                            // 添加轨迹起点
                            mTrajectoryPoints.add(currentPoint);
                            // 切换到主线程（在主线程中处理UI更新消息）
                            if (callback != null) {
                                mainHandler.post(() -> {
                                    // 停止进度提示
                                    callback.stopLocationProgress();
                                    // 添加起点的点标记
                                    callback.addStartMarker();
                                });
                            }
                        } else {
                            locationAttempts++;
                            // 检查是否超时
                            if (locationAttempts >= MAX_WAIT_TIMES) {
                                // 使用最新位置信息作为起点
                                mTrajectoryPoints.add(currentPoint);
                                if (callback != null) {
                                    // 切换到主线程（在主线程中处理UI更新消息）
                                    mainHandler.post(() -> {
                                        callback.stopLocationProgress();
                                        callback.onReachedMaxWaitTime();
                                        // 添加轨迹起点的点标记
                                        callback.addStartMarker();
                                    });
                                }
                            } else {
                                if (callback != null) {
                                    // 切换到主线程（在主线程中处理UI更新消息）
                                    mainHandler.post(() -> callback.updateLocationProgress());
                                }
                            }
                        }
                    }
                    // 已经确定了轨迹的起点，则开始记录轨迹点,更新UI
                    else {
                        LatLng lastPoint = mTrajectoryPoints.get(mTrajectoryPoints.size() - 1);
                        double segmentDistance = DistanceUtil.getDistance(lastPoint, currentPoint);
                        if (segmentDistance > 0.01) {
                            totalDistance += segmentDistance;
                        }
                        // 添加点
                        mTrajectoryPoints.add(currentPoint);
                        if (callback != null) {
                            // 切换到主线程（在主线程中处理UI更新消息）
                            mainHandler.post(() -> {
                                callback.updateDistanceDisplay();
                                callback.updateMapTrajectory();
                            });
                        }
                    }
                }
            } else {
                // 检查定位结果是否有效
                if (location.getLocType() == BDLocation.TypeServerError) {
                    Log.e(TAG, "百度地图的定位服务异常");
                }
            }
        }

        @Override
        public void onConnectHotSpotMessage(String s, int i) {

        }
    }

    // 根据运动状态动态调整间隔
    public void adjustScanSpanBasedOnSpeed(float speed) {
        /*
        1. **低速运动（如步行、跑步）**：速度变化较快，方向可能频繁改变。需要更高的定位频率来捕捉细节变化，保证轨迹的平滑和准确。
        2. **高速运动（如行车）**：速度变化相对平缓，方向变化较慢。较低的定位频率即可满足需求，同时减少设备功耗和网络流量。
         */
        LocationClientOption option = mLocationClient.getLocOption();
        if (speed > 36.0) { // 高速状态(>36km/h==10m/s)
            option.setScanSpan(3000);
        } else if (speed > 18) { // 中速--跑步速度(18-36km/h==5m/s)
            option.setScanSpan(2000);
        } else { // 低速--行走速度
            option.setScanSpan(1000);
        }
        mLocationClient.setLocOption(option);
    }

    private void initLocationAndStart() {
        LocationClient.setKey(Constant.API_KEY);
        LocationClient.setAgreePrivacy(true);
        try {
            mLocationClient = new LocationClient(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "❗❗❗定位客户端初始化失败", e);
        }
        // 设置定位模式
        LocationClientOption option = getLocationClientOption();
        mLocationClient.setLocOption(option);
        if (myLocationListener == null) {
            myLocationListener = new MyLocationListener();
            // 注册定位监听器（让SDK中的线程可以调用监听器的方法）
            mLocationClient.registerLocationListener(myLocationListener);
        }

        // 启动定位SDK
        mLocationClient.start(); // 启动定位 → 触发定位SDK内部线程
        lastLocationTime = -1;

        locationClientStartedTime = System.currentTimeMillis();

        // 显示日志
        if (callback != null) {
            mainHandler.post(() -> callback.updateLogDisplay("已开启定位，正在等待第一个位置信息..."));
        }

    }

    @NonNull
    private static LocationClientOption getLocationClientOption() {
        LocationClientOption option = new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Device_Sensors);
        option.setCoorType("bd09ll");// 设置坐标类型
        option.setScanSpan(1000);// 设置定位间隔，单位毫秒
        option.setOpenGnss(true);// 高精度定位和仅仅使用设备时必须打开
        option.setLocationNotify(true);//当卫星定位有效时按照1S/1次频率输出卫星定位结果
        option.SetIgnoreCacheException(true);//不缓存定位数据
        option.setIgnoreKillProcess(true);//stop时不杀死定位SDK所处进程
        option.setFirstLocType(LocationClientOption.FirstLocType.SPEED_IN_FIRST_LOC);// 设置首次定位类型为速度优先
        return option;
    }

    private void startGpsMonitoring() {
        // 使用独立计时器（避免递归积累）
        monitorTask = new Runnable() {
            @Override
            public void run() {
                checkGpsSignal();
                mainHandler.postDelayed(this, 8000); // 主线程每8s检查一次GPS信号
            }
        };
        // 向主线程(也即自身，因为服务的onCreate方法由主线程执行）发送该任务
        mainHandler.post(monitorTask);
    }

    // 提取检查逻辑
    private void checkGpsSignal() {
        long currentTime = System.currentTimeMillis();
        // 如果定位服务开启后一直没有收到GPS信号 或者 GPS信号中途丢失，则显示GPS信号弱提示
        if (mLocationClient != null && mLocationClient.isStarted() && lastLocationTime == -1 && (currentTime - locationClientStartedTime) > 5000
                || mLocationClient != null && mLocationClient.isStarted() && lastLocationTime != -1 && (currentTime - lastLocationTime) > 5000) {
            // 添加日志
            if (mLocationClient != null && mLocationClient.isStarted() && lastLocationTime == -1 && (currentTime - locationClientStartedTime) > 5000)
                Log.e(TAG, "一直检测不到卫星信号！！！");
            else
                Log.e(TAG, "卫星信号中断！！！");

            // 添加callback有效性检查
            if (callback != null) {
                mainHandler.post(() -> {
                    if (isTracking) {
                        callback.stopTracking();
                    }
                    callback.showGpsWeakAlert();
                });
            }
        }
    }

    public void startTracking() {
        if (!isTracking) {
            isTracking = true;
            trackingStartTimeMillis = System.currentTimeMillis();
            // 重置轨迹跟踪的状态数据
            resetTracking();
            // 启动时长更新任务
            startTimeLengthUpdating();
        }
    }

    private void resetTracking() {
        locationAttempts = 0;// 重置尝试次数
        last = null;    // 重置上次的位置点
        points.clear(); // 重置精度检测点列表
        mTrajectoryPoints.clear();// 清空轨迹点
        totalDistance = 0.0;// 重置总距离
    }

    private void startTimeLengthUpdating() {
        // 初始化时间更新任务
        durationUpdateTask = new Runnable() {
            @Override
            public void run() {
                updateTimeLength();
                // 继续调度下一次更新
                mainHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        mainHandler.post(durationUpdateTask);
    }

    private void updateTimeLength() {
        long currentTime = System.currentTimeMillis();
        long duration = currentTime - trackingStartTimeMillis;
        String formattedDuration = formatDuration(duration);

        // 直接调用MainActivity的更新方法
        if (callback != null) {
            callback.updateTimeDisplay(formattedDuration);
        }
    }

    public void stopTracking() {
        if (isTracking) {
            isTracking = false;
            trackingEndTimeMills = System.currentTimeMillis();
            // 停止时长更新任务
            if (durationUpdateTask != null) {
                mainHandler.removeCallbacks(durationUpdateTask);
                durationUpdateTask = null;
            }
            saveRecord();
        }
    }

    // 添加格式化方法
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    // 保存本次运动记录: 总距离，时间，轨迹
    private void saveRecord() {
        // 创建记录对象
        Record record = new Record();
        record.distance = totalDistance;
        record.duration = formatDuration(trackingEndTimeMills - trackingStartTimeMillis);
        record.startTime = new Date(trackingStartTimeMillis);
        record.endTime = new Date(trackingEndTimeMills);
        record.points = new ArrayList<>(mTrajectoryPoints); // 复制轨迹点
        record.steps = callback.myStepService.getCurrentSteps();
        record.walking_steps = callback.myStepService.getWalkingSteps();
        record.running_steps = callback.myStepService.getRunningSteps();

        // 保存到数据库
        new Thread(() -> {
            AppDatabase db = MyApp.getDatabase();
            db.recordDao().insert(record);
            Log.d(TAG, "轨迹跟踪记录已保存，ID: " + record.id);
        }).start();
    }

    /**
     * 首次定位很重要，选一个精度相对较高的起始点
     * 注意：如果一直显示gps信号弱，说明过滤的标准过高了，
     * 你可以将location.getRadius()>25中的过滤半径调大，比如>50，
     * 并且将连续5个点之间的距离DistanceUtil.getDistance(last, ll ) > 5也调大一点，比如>10，
     */
    private LatLng getMostAccuracyLocation(final BDLocation location) {
        if (location.getRadius() > 25) {
            //最新点的的gps位置精度大于25米，直接弃用，
            return null;
        }

        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        // 处理首次调用时last为null的情况
        if (last == null) {
            last = ll;
            points.add(ll);
            return null;
        }

        if (DistanceUtil.getDistance(last, ll) > 5) {
            points.clear();//有两点位置大于5，重新来过
            last = ll;
            return null;
        }
        points.add(ll);
        last = ll;
        //有3个连续的点之间的距离小于5，认为gps已稳定，以最新的点为起始点
        if (points.size() >= 3) {
            points.clear();
            return ll;
        }
        return null;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocationClient != null) {
            mLocationClient.stop();
            mLocationClient.unRegisterLocationListener(myLocationListener);
        }

        // 释放GPS监控资源
        if (mainHandler != null) {
            mainHandler.removeCallbacks(monitorTask); // 精确移除
        }
        Log.d(TAG, "MyLocation服务已经杀死");
    }
}

