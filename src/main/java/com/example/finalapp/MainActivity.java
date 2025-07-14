package com.example.finalapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.example.finalapp.room.AppDatabase;
import com.example.finalapp.room.Record;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "我的日志-MainActivity";
    private static final int PERMISSION_REQUEST_CODE_1 = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.POST_NOTIFICATIONS
    };

    // 地图相关UI
    private MapView mMapView;
    private BaiduMap mBaiduMap;// 地图控制器对象

    // 轨迹相关UI
    private ImageButton btnStartOrStop;
    private CircularProgressButton circularProgress;

    private ValueAnimator longPressProgressAnimator;

    // 数据相关UI
    private TextView tvDistance, tvTime, tvSteps, tvSpeed, tvLocationLog;

    private Polyline mTrajectoryLine;// 轨迹线
    private Marker mStartMarker; // 轨迹起点的点标记
    private Marker mEndMarker;   // 轨迹终点的点标记


    // 起点获取进度相关UI
    private Toast locationToast;

    // 定位服务相关变量
    private MyLocationService myLocationService;
    private boolean isLocationBound = false;
    private final ServiceConnection locationConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            MyLocationService.LocalBinder binder = (MyLocationService.LocalBinder) service;
            myLocationService = binder.getService();
            isLocationBound = true;

            // 设置回调接口（关键连接点）
            myLocationService.setCallback(MainActivity.this);

            // 当onCreate后重新绑定时，从服务中恢复UI状态
            if (myLocationService.isTracking()) {
                btnStartOrStop.setImageResource(R.drawable.ic_btn_stop);
                updateDistanceDisplay();
                updateMapTrajectory();
                Log.i(TAG, "已经成功绑定到我的定位服务，回调接口已设置，UI状态已经恢复");
            } else {
                Log.i(TAG, "已经成功绑定到我的定位服务，回调接口已设置");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isLocationBound = false;
            myLocationService = null;
        }
    };
    private Runnable stopTrackingRunnable;
    private boolean ignore_next_action_up = false;
    private final long LONG_PRESS_TIME_MILLIS = 1500;

    // 计步服务相关变量
    public MyCountingStepsService myStepService;
    private boolean isStepBound = false;
    private final ServiceConnection stepConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MyCountingStepsService.LocalBinder binder = (MyCountingStepsService.LocalBinder) service;
            myStepService = binder.getService();
            isStepBound = true;

            // 设置回调接口（关键连接点）
            myStepService.setCallback(MainActivity.this);

            // 当onCreate后重新绑定时，从服务中恢复UI状态
            if (myStepService.isCounting()) {
                updateStepsDisplay();
                Log.i(TAG, "已经成功绑定到我的计步服务，回调接口已设置，UI状态已经恢复");
            } else {
                Log.i(TAG, "已经成功绑定到我的计步服务，回调接口已设置");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isStepBound = false;
            myStepService = null;
        }
    };
    LocationManager locationManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 0 初始化百度地图SDK
        if (!com.baidu.mapapi.SDKInitializer.isInitialized()) {
            Log.e(TAG, "百度地图SDK未初始化！");
            Toast.makeText(this, "地图服务初始化失败", Toast.LENGTH_LONG).show();
        }
        setContentView(R.layout.activity_main);

        // 1 检查并请求所有必要权限
        checkAndRequestPermissions();

        // 2 初始化UI
        initUI();

        // 3. 先启动前台服务（声明重要性），会触发服务的onCreate()方法和onStartCommand()方法
        Intent locationServiceIntent = new Intent(this, MyLocationService.class);
        startForegroundService(locationServiceIntent);

        // 4. 再绑定服务（建立通信）
        bindService(locationServiceIntent, locationConnection, Context.BIND_AUTO_CREATE);
    }


    // 1 检查并请求权限
    private void checkAndRequestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE_1
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE_1) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                handlePermissionDenied();
            } else {
                Log.d(TAG, "所有必要权限已授予");
            }
        }
    }

    // * 处理权限被拒绝的情况
    private void handlePermissionDenied() {
        Toast.makeText(this, "部分必要权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show();

        // 对于通知权限，显示专门的引导对话框
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            showNotificationPermissionDeniedDialog();
        }
    }

    private void showNotificationPermissionDeniedDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("通知权限被拒绝")
                .setMessage("为了正常使用应用，请前往设置手动开启通知权限。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent();
                    intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                    intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
                    startActivity(intent);
                })
                .setNegativeButton("取消", null)
                .show();
    }


    // 2 初始化UI
    @SuppressLint("ClickableViewAccessibility")
    private void initUI() {
        tvLocationLog = findViewById(R.id.tvLocationLog);
        tvDistance = findViewById(R.id.tvDistance);
        tvTime = findViewById(R.id.tvTime);
        tvSteps = findViewById(R.id.tvSteps);
        tvSpeed = findViewById(R.id.tvSpeed);

        btnStartOrStop = findViewById(R.id.btnStartOrStop);
        circularProgress = findViewById(R.id.circularProgress); // 初始化自定义进度按钮
        btnStartOrStop.setOnClickListener(v -> {
        });

        btnStartOrStop.setOnTouchListener((v, event) -> {
            if (!myLocationService.isTracking()) {
                // 非跟踪状态：单击开始
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    if (!ignore_next_action_up) {
                        Log.d(TAG, "当前为非跟踪状态，检测到抬起动作，并且不用忽略，则尝试开始跟踪和计步");
                        v.performClick();
                        if (myLocationService.lastLocationTime != -1) {
                            handleStart();
                        } else {
                            Toast.makeText(this, "定位已开启，请等待收到第一个定位信息", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "当前为非跟踪状态，检测到抬起动作，但是应该忽略（因为是stopTracking执行完毕后的抬起动作），则不开始跟踪");
                        ignore_next_action_up = false;
                    }
                }
                return false;
            } else {
                // 跟踪状态：仅响应长按
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        startCircularProgressAnimation(); // 显示进度条
                        // 创建延时任务
                        stopTrackingRunnable = () -> {
                            resetCircularProgress(); // 取消动画
                            handleStop();
                            ignore_next_action_up = true;// 在isTracking转变true后，忽略下次的Action_UP，因为它是结束轨迹跟踪后的抬起，不是单击开始轨迹跟踪的抬起。
                            v.performClick();
                        };
                        // 启动延时任务
                        v.postDelayed(stopTrackingRunnable, LONG_PRESS_TIME_MILLIS);
                        Log.d(TAG, "当前为跟踪状态，检测到按下动作，启动一个任务，它在2s后停止跟踪");
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        // 判断是否是按下后的2s内的抬起动作
                        if (stopTrackingRunnable != null) {
                            v.removeCallbacks(stopTrackingRunnable);
                            resetCircularProgress(); // 取消动画
                            v.performClick();
                            Log.d(TAG, "当前为跟踪状态，在按压后的2s内检测到抬起动作，取消动画，取消原来的那个任务");
                        }
                        return true;

                    default:
                        return true;
                }
            }
        });

        // 初始化地图
        initMap();
    }

    private void initMap() {
        mMapView = findViewById(R.id.mapView);
        mBaiduMap = mMapView.getMap();// 获取地图控制器对象

        // 开启定位图层
        mBaiduMap.setMyLocationEnabled(true);

        //打开室内图，默认为关闭状态
        mBaiduMap.setIndoorEnable(true);

        // 设置初始缩放级别
        MapStatus.Builder builder = new MapStatus.Builder();
        builder.zoom(18.0f); // 设置初始缩放级别为18
        mBaiduMap.setMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));

        // 配置定位图标
        mBaiduMap.setMyLocationConfiguration(new MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, false,  // 不显示方向箭头
                null));
    }

    // 2 启停控制
    private void handleStart() {
        // 1 更新UI
        clearMap();
        tvDistance.setText("距离: 0.0");
        tvSteps.setText("0");
        btnStartOrStop.setImageResource(R.drawable.ic_btn_stop);
        btnStartOrStop.setContentDescription("长按停止");

        // 2 启动持续轨迹跟踪和速度实时识别
        startTracking();

        // 3 启动持续计步
        Intent stepServiceIntent = new Intent(this, MyCountingStepsService.class);
        startForegroundService(stepServiceIntent);

        // 4 绑定计步服务（建立通信渠道)
        bindService(stepServiceIntent, stepConnection, Context.BIND_AUTO_CREATE);
    }

    private void handleStop() {
        // 1 更新UI
        btnStartOrStop.setImageResource(R.drawable.ic_btn_start);
        btnStartOrStop.setContentDescription("单击开始");

        // 2 停止持续轨迹跟踪和速度实时识别
        stopTracking();

        // 3 停止持续计步
        stopCounting();

        // 5 保存记录，此时还不能解绑计步服务，因为要获取其中状态数据
        saveRecord();

        // 6 解绑、停止计步服务
        Intent stepServiceIntent = new Intent(this, MyCountingStepsService.class);
        if (isStepBound) {
            unbindService(stepConnection);
            isStepBound = false;
        }
        stopService(stepServiceIntent);
    }

    private void startCircularProgressAnimation() {
        circularProgress.setVisibility(View.VISIBLE);
        circularProgress.setProgress(0);

        if (longPressProgressAnimator != null && longPressProgressAnimator.isRunning()) {
            longPressProgressAnimator.cancel();
        }

        longPressProgressAnimator = ValueAnimator.ofFloat(0, 360);
        longPressProgressAnimator.setDuration(LONG_PRESS_TIME_MILLIS);
        longPressProgressAnimator.setInterpolator(new LinearInterpolator()); // 确保线性变化
        longPressProgressAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            circularProgress.setProgress(value);
        });
        longPressProgressAnimator.start();
    }

    private void resetCircularProgress() {
        if (longPressProgressAnimator != null && longPressProgressAnimator.isRunning()) {
            longPressProgressAnimator.removeAllUpdateListeners();
            longPressProgressAnimator.cancel();
            longPressProgressAnimator = null;
        }
        circularProgress.setProgress(0);
        circularProgress.setVisibility(View.GONE);
    }

    // 启动轨迹追踪
    private void startTracking() {
        if (isLocationBound) {
            myLocationService.startTracking();
            Log.d(TAG, "已经启动轨迹跟踪");
        } else {
            Toast.makeText(this, "定位服务未绑定！", Toast.LENGTH_SHORT).show();
        }
    }

    // 停止持续轨迹跟踪和速度实时识别
    public void stopTracking() {
        if (isLocationBound) {
            updateDistanceDisplay();
            // 至少有两个轨迹点时，才添加终点标记
            List<LatLng> points = myLocationService.getTrajectoryPoints();
            if (points.size() >= 2) {
                LatLng endPoint = points.get(points.size() - 1);
                addEndMarker(endPoint);
            }
            // 停止轨迹追踪，但是不停止整个定位服务（因为用户还可能实时显示，只有在不处于跟踪状态下退出应用，才停止我的定位服务）
            myLocationService.stopTracking();
            Log.d(TAG, "已经关闭轨迹跟踪和速度实时识别");
        } else {
            Toast.makeText(this, "定位服务未绑定！", Toast.LENGTH_SHORT).show();
        }
    }

    // 停止持续计步
    private void stopCounting() {
        Log.d(TAG, "stopCounting开始执行");
        if (isStepBound) {
            myStepService.stopCounting();
        }
        Log.d(TAG, "stopCounting执行完毕");
    }

    // 保存本次运动记录: 总距离，时间，轨迹
    private void saveRecord() {
        if (myLocationService != null && myStepService != null) {
            // 创建记录对象
            Record record = new Record();
            record.distance = myLocationService.getTotalDistance();
            record.duration = MyUtil.formatDuration(myLocationService.geTrackingPeriod());
            record.startTime = new Date(myLocationService.getTrackingStartTimeMillis());
            record.endTime = new Date(myLocationService.geTrackingEndTimeMills());
            record.points = new ArrayList<>(myLocationService.getTrajectoryPoints()); // 复制轨迹点
            record.steps = myStepService.getCurrentSteps();
            record.walking_steps = myStepService.getWalkingSteps();
            record.running_steps = myStepService.getRunningSteps();
            record.statusPeriods = myStepService.getStatusPeriods();

            // 保存到数据库
            new Thread(() -> {
                AppDatabase db = MyApp.getDatabase();
                db.recordDao().insert(record);
                Log.d(TAG, "轨迹跟踪记录已保存，ID: " + record.id);
            }).start();
        }

    }

    // 下面是UI更新操作，供服务回调。

    // 更新速度显示
    public void updateSpeedDisplay(float speed) {
        tvSpeed.setText(String.format("%.1f", speed / 3.6F));
    }

    // 显示GPS接收日志
    public void updateLocationLog(String logMessage) {
        tvLocationLog.setText(logMessage);
    }

    // 更新地图上的当前坐标
    public void updateLocationOnMap(LatLng point, float speed, float accuracy) {
        if (mBaiduMap == null) return;

        // 创建位置数据（方向需要自己实现）
        MyLocationData locData = new MyLocationData.Builder().accuracy(accuracy)// 精度圈大小
                .latitude(point.latitude).longitude(point.longitude).speed(speed).build();

        // 更新地图，显示传入的坐标
        mBaiduMap.setMyLocationData(locData);

        // 以指定点为地图中心
        moveMapToCenterOf(point);
    }

    // 以指定点为地图中心
    private void moveMapToCenterOf(LatLng ll) {
        if (mBaiduMap == null || mBaiduMap.getMapStatus() == null) return;

        runOnUiThread(() -> {
            try {
                // 获取当前地图状态（包含用户设置的缩放级别）
                MapStatus currentStatus = mBaiduMap.getMapStatus();

                // 仅移动中心点，保留用户设置的缩放级别
                MapStatus.Builder builder = new MapStatus.Builder(currentStatus);
                builder.target(ll); // 只更新中心点，不改变缩放级别

                mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
            } catch (Exception e) {
                Log.e(TAG, "Map refresh error", e);
            }
        });
    }

    // 显示轨迹起点获取进度
    public void updateLocationProgress() {
        if (isLocationBound) {
            if (locationToast != null) {
                locationToast.cancel();
            }
            int locationAttempts = myLocationService.getLocationAttempts();
            int MAX_WAIT_TIME = myLocationService.getMaxWaitTime();
            locationToast = Toast.makeText(this, "第" + locationAttempts + "/" + MAX_WAIT_TIME + "次尝试获取起点位置", Toast.LENGTH_SHORT);
            locationToast.show();
        }
    }

    // 显示无法获取精确起点位置，使用当前位置
    public void onReachedMaxWaitTime() {
        if (isLocationBound) {
            Toast.makeText(this, "无法获取精确起点位置，使用当前位置", Toast.LENGTH_LONG).show();
        }
    }

    // 停止进度提示
    public void stopLocationProgress() {
        if (locationToast != null) {
            locationToast.cancel();
            locationToast = null;
        }
    }

    // 显示GPS信号弱提示
    public void showGpsWeakAlert() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "长时间未获取到卫星信号，请前往室外", Toast.LENGTH_SHORT).show());
    }

    // 显示轨迹总距离
    public void updateDistanceDisplay() {
        if (isLocationBound) {
            double distance = myLocationService.getTotalDistance();
            String distanceText = String.format(Locale.CHINA, "距离: %.1f", distance);
            tvDistance.setText(distanceText);
        }
    }

    // 显示时长
    public void updateTimeDisplay(String timeText) {
        if (isLocationBound) {
            tvTime.setText(timeText);
        }
    }

    // 显示步数
    public void updateStepsDisplay() {
        if (isStepBound) {
            tvSteps.setText(String.format(Locale.getDefault(), "%d", myStepService.getCurrentSteps()));
        }
    }

    // 更新地图上的轨迹
    public void updateMapTrajectory() {
        if (isLocationBound) {
            List<LatLng> points = myLocationService.getTrajectoryPoints();
            if (points.size() >= 2) {
                if (mTrajectoryLine == null) {
                    OverlayOptions options = new PolylineOptions().zIndex(1).width(10).color(0xFFFF0000).points(points);
                    mTrajectoryLine = (Polyline) mBaiduMap.addOverlay(options);
                } else {
                    mTrajectoryLine.setPoints(points);
                }
            }
        }
    }

    // 清除地图上的已有轨迹
    private void clearMap() {
        // 清理点标记
        if (mStartMarker != null) {
            mStartMarker.remove();
            mStartMarker = null;
        }
        if (mEndMarker != null) {
            mEndMarker.remove();
            mEndMarker = null;
        }
        if (mTrajectoryLine != null) {
            mTrajectoryLine.remove();
            mTrajectoryLine = null;
        }
    }

    // 添加起点标记、终点标记
    public void addStartMarker() {
        if (isLocationBound) {
            if (mStartMarker != null) {
                mStartMarker.remove();
            }
            // 获取轨迹起点
            LatLng startPoint = myLocationService.getTrajectoryPoints().get(0);

            // 使用缩放后的图标 (36dp x 36dp)
            BitmapDescriptor startIcon = MyUtil.getScaledIcon(this, R.drawable.ic_start_point, 36, 36);

            // 创建一个类似气球形状的图层
            OverlayOptions startOptions = new MarkerOptions().position(startPoint).icon(startIcon).zIndex(2).animateType(MarkerOptions.MarkerAnimateType.grow);// 出现动画设置

            // 添加该marker图层到地图上层
            mStartMarker = (Marker) mBaiduMap.addOverlay(startOptions);
        }
    }

    private void addEndMarker(LatLng point) {
        if (mEndMarker != null) {
            mEndMarker.remove();
        }

        // 使用缩放后的图标 (36dp x 36dp)
        BitmapDescriptor endIcon = MyUtil.getScaledIcon(this, R.drawable.ic_end_point, 36, 36);

        OverlayOptions endOptions = new MarkerOptions().position(point).icon(endIcon).zIndex(3).animateType(MarkerOptions.MarkerAnimateType.grow);
        mEndMarker = (Marker) mBaiduMap.addOverlay(endOptions);
    }

    // 加载主界面的菜单
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    // 处理菜单点击事件
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 跳转到历史记录页面
        if (item.getItemId() == R.id.menu_history) {
            Log.d(TAG, "用户点击了历史记录选项");
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 生命周期管理
    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.onResume();
        }
        // 绑定成功->pause->resume，则恢复回调
        if (myLocationService != null) {
            myLocationService.setCallback(this);
        }
        if (myStepService != null) {
            myStepService.setCallback(this);
        }

        if (myLocationService != null) {
            if (myLocationService.isTracking()) {
                // 如果处于轨迹跟踪中，则切回到本界面时更新地图轨迹
                updateMapTrajectory();
            } else {
                // 否则只是恢复位置更新
                myLocationService.resumeLocationUpdates();
            }
        }
        Log.d(TAG, "MainActivity onResume执行完毕");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMapView != null) {
            mMapView.onPause();
        }

        if (myLocationService != null) {
            // 清除回调
            myLocationService.clearCallback();
            // 如果该界面失去焦点并且不处于轨迹跟踪中时，暂停位置更新
            if (myLocationService.isTracking() == false) {
                myLocationService.pauseLocationUpdates();
            }
        }
        if (myStepService != null) {
            // 清除回调
            myStepService.clearCallback();
        }
        Log.d(TAG, "MainActivity onPause执行完毕");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 完全释放动画资源
        if (longPressProgressAnimator != null) {
            longPressProgressAnimator.removeAllUpdateListeners();
            longPressProgressAnimator.cancel();
            longPressProgressAnimator = null;
        }

        // 清理点标记
        if (mStartMarker != null) {
            mStartMarker.remove();
            mStartMarker = null;
        }
        if (mEndMarker != null) {
            mEndMarker.remove();
            mEndMarker = null;
        }

        // 释放地图资源
        if (mBaiduMap != null) {
            mBaiduMap.setMyLocationEnabled(false);
            mBaiduMap.clear();//清空地图所有的 Overlay 覆盖物以及 InfoWindow
        }

        if (mMapView != null) {
            mMapView.onDestroy();
            mMapView = null;
        }

        boolean isTracking = false;
        if (myLocationService != null) {
            isTracking = myLocationService.isTracking();
        }

        // 如果当前还和服务绑定，则消除服务中对MainActivity的回调，解绑服务
        if (isLocationBound) {
            if (myLocationService != null) {
                myLocationService.clearCallback();
            }
            try {
                unbindService(locationConnection);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "解绑定位服务异常", e);
            }
            isLocationBound = false;
        }

        if (isStepBound) {
            if (myStepService != null) {
                myStepService.clearCallback();
            }
            unbindService(stepConnection);
            isStepBound = false;
        }

        // 在非跟踪状态下销毁该Activity，专门杀死定位服务
        if (!isTracking) {
            Intent locationServiceIntent = new Intent(this, MyLocationService.class);
            stopService(locationServiceIntent);
            Log.d(TAG, "当前没有处于轨迹跟踪状态，则杀死定位服务");
        }
    }
}
