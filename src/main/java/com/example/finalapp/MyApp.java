package com.example.finalapp;

import android.app.Application;
import android.util.Log;

import androidx.room.Room;

import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.example.finalapp.room.AppDatabase;

public class MyApp extends Application {
    private static final String TAG = "我的日志-App";
    private static AppDatabase database;

    @Override
    public void onCreate() {
        super.onCreate();
        // 创建数据库实例
        database = Room.databaseBuilder(this, AppDatabase.class, "record-db")
                .allowMainThreadQueries() // 仅用于示例，实际开发中应避免
                .fallbackToDestructiveMigration() // 添加破坏性迁移以解决模式变更
                .build();

        // 添加隐私协议同意
        SDKInitializer.setAgreePrivacy(getApplicationContext(), true);

        // 设置百度地图API密钥
        SDKInitializer.setApiKey(Constant.API_KEY);

        // 初始化百度地图SDK
        SDKInitializer.initialize(this);
        SDKInitializer.setCoordType(CoordType.BD09LL); // 使用百度坐标
        Log.i(TAG, "百度地图SDK初始化成功");
    }

    public static AppDatabase getDatabase() {
        return database;
    }
}