package com.example.finalapp.room;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.baidu.mapapi.model.LatLng;
import com.example.finalapp.StatusPeriod;

import java.util.Date;
import java.util.List;

// 数据实体，轨迹数据的结构化容器
@Entity(tableName = "records")
public class Record {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public double distance; // 总距离
    public String duration; // 时长 (格式化字符串)

    @TypeConverters(DateConverter.class)
    public Date startTime;  // 开始时间
    @TypeConverters(DateConverter.class)
    public Date endTime;    // 结束时间

    public int steps;
    public int walking_steps;
    public int running_steps;

    @TypeConverters(LatLngListConverter.class)
    public List<LatLng> points; // 轨迹点列表

    // 运动周期列表<---我的计步服务
    @TypeConverters(StatusPeriodListConverter.class)
    public List<StatusPeriod> statusPeriods;
}
