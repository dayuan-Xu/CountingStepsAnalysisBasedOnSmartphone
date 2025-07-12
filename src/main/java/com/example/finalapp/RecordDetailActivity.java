package com.example.finalapp;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.Polyline;
import com.baidu.mapapi.map.PolylineOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.model.LatLngBounds;
import com.example.finalapp.room.AppDatabase;
import com.example.finalapp.room.Record;

import java.util.List;
import java.util.Locale;


// 单条记录详情展示界面
// 功能：
//根据ID查询完整记录
//显示详细信息（距离/时长/时间）
//在百度地图上绘制轨迹
//自动滚动到顶部突出显示
public class RecordDetailActivity extends AppCompatActivity {
    private MapView mapView;
    private BaiduMap baiduMap;
    private Polyline polyline;
    private Marker startMarker; // 起点标记
    private Marker endMarker;   // 终点标记

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_detail);
        // 添加返回按钮支持
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("记录详情"); // 设置标题
        }

        int recordId = getIntent().getIntExtra("record_id", -1);
        if (recordId == -1) {
            finish();
            return;
        }

        // 从数据库获取记录
        AppDatabase db = MyApp.getDatabase();
        Record record = db.recordDao().getRecordById(recordId);

        // 显示详细信息
        TextView distanceView = findViewById(R.id.tvDistance);
        TextView durationView = findViewById(R.id.tvDuration);
        TextView stepsView = findViewById(R.id.tvSteps);
        TextView walkingStepsView = findViewById(R.id.tvWalkingSteps);
        TextView runningStepsView = findViewById(R.id.tvRunningSteps);
        TextView timeView = findViewById(R.id.tvTime);

        distanceView.setText(String.format(Locale.CHINA, "距离: %.1f米", record.distance));
        durationView.setText("时长: " + record.duration);
        stepsView.setText("总步数: " + String.valueOf(record.steps));
        walkingStepsView.setText("步行步数: " + String.valueOf(record.walking_steps));
        runningStepsView.setText("跑步步数: " + String.valueOf(record.running_steps));

        // 优化时间显示：如果开始和结束在同一天，则只显示日期一次
        String timeText = MyUtil.formatTimeRange(record.startTime, record.endTime);
        timeView.setText(timeText);

        // 初始化地图
        mapView = findViewById(R.id.mapView);
        baiduMap = mapView.getMap();

        // 绘制轨迹
        drawTrajectory(record.points);
    }

    private void drawTrajectory(List<LatLng> points) {
        if (points == null || points.size() < 2) return;

        // 清除可能存在的旧标记
        clearMarkers();

        // 添加起点标记
        LatLng startPoint = points.get(0);
        addStartMarker(startPoint);

        // 添加终点标记
        LatLng endPoint = points.get(points.size() - 1);
        addEndMarker(endPoint);

        // 添加轨迹
        PolylineOptions options = new PolylineOptions()
                .width(10)
                .color(0xFFFF0000)
                .points(points);

        polyline = (Polyline) baiduMap.addOverlay(options);

        // 新增：计算轨迹点之间的最大距离
        double maxDistanceMeters = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            float[] results = new float[1];
            Location.distanceBetween(
                    points.get(i).latitude,
                    points.get(i).longitude,
                    points.get(i + 1).latitude,
                    points.get(i + 1).longitude,
                    results
            );
            if (results[0] > maxDistanceMeters) {
                maxDistanceMeters = results[0];
            }
        }

        // 当最大点距小于10米时，手动设置缩放级别
        if (maxDistanceMeters < 10) {
            mapView.post(() -> {
                // 使用固定高缩放级别（19级≈2米精度）
                MapStatusUpdate status = MapStatusUpdateFactory.newMapStatus(
                        new MapStatus.Builder()
                                .target(points.get(0))  // 以第一个点为中心
                                .zoom(21.0f)            // 手动设置
                                .build()
                );
                baiduMap.setMapStatus(status);
            });
        } else {
            // 原始边界框逻辑
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng point : points) {
                builder.include(point);
            }
            final LatLngBounds bounds = builder.build();

            mapView.post(() -> {
                baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngBounds(
                        bounds, 100, 100, 100, 100
                ));
            });
        }
    }

    public void addStartMarker(LatLng startPoint) {
        // 使用缩放后的图标 (36dp x 36dp)
        BitmapDescriptor startIcon = MyUtil.getScaledIcon(this, R.drawable.ic_start_point, 36, 36);

        // 创建一个类似气球形状的图层
        OverlayOptions startOptions = new MarkerOptions().position(startPoint).icon(startIcon).zIndex(2).animateType(MarkerOptions.MarkerAnimateType.grow);// 出现动画设置

        // 添加该marker图层到地图上层
        startMarker = (Marker) baiduMap.addOverlay(startOptions);
    }

    private void addEndMarker(LatLng point) {
        // 使用缩放后的图标 (36dp x 36dp)
        BitmapDescriptor endIcon = MyUtil.getScaledIcon(this, R.drawable.ic_end_point, 36, 36);

        OverlayOptions endOptions = new MarkerOptions().position(point).icon(endIcon).zIndex(3).animateType(MarkerOptions.MarkerAnimateType.grow);
        endMarker = (Marker) baiduMap.addOverlay(endOptions);
    }

    // 清除标记
    private void clearMarkers() {
        if (startMarker != null) {
            startMarker.remove();
            startMarker = null;
        }
        if (endMarker != null) {
            endMarker.remove();
            endMarker = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // 延迟滚动确保布局已加载
        new Handler().postDelayed(() -> {
            ScrollView scrollView = findViewById(R.id.scrollView);
            scrollView.smoothScrollTo(0, 0);
        }, 100);
    }

    // 添加返回按钮处理
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // 关闭当前Activity，返回到历史记录界面
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // 添加生命周期方法
    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume(); // 地图恢复
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause(); // 地图暂停
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 释放地图资源
        if (mapView != null) {
            mapView.onDestroy(); // 销毁地图视图
            mapView = null;
        }

        // 清理标记
        if (startMarker != null) {
            startMarker.remove();
            startMarker = null;
        }
        if (endMarker != null) {
            endMarker.remove();
            endMarker = null;
        }

        // 清理轨迹线
        if (polyline != null) {
            polyline.remove();
            polyline = null;
        }

        // 清理地图控制器
        if (baiduMap != null) {
            baiduMap.setMyLocationEnabled(false); // 禁用定位图层
            baiduMap.clear(); // 清除所有覆盖物
            baiduMap = null;
        }
    }

}
