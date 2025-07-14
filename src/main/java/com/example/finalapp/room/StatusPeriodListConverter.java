package com.example.finalapp.room;

import androidx.room.TypeConverter;

import com.example.finalapp.StatusPeriod;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class StatusPeriodListConverter {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static String fromStatusPeriodList(List<StatusPeriod> periods) {
        return gson.toJson(periods);
    }

    @TypeConverter
    public static List<StatusPeriod> toStatusPeriodList(String json) {
        Type listType = new TypeToken<List<StatusPeriod>>() {
        }.getType();
        return gson.fromJson(json, listType);
    }
}
