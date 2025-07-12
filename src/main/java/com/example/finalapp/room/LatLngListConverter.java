package com.example.finalapp.room;

import androidx.room.TypeConverter;

import com.baidu.mapapi.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

// 轨迹点列表转换器
public class LatLngListConverter {
    private static Gson gson = new Gson();

    @TypeConverter
    public static String fromLatLngList(List<LatLng> points) {
        return gson.toJson(points);
    }

    @TypeConverter
    public static List<LatLng> toLatLngList(String json) {
        Type type = new TypeToken<List<LatLng>>() {
        }.getType();
        return gson.fromJson(json, type);
    }
}
