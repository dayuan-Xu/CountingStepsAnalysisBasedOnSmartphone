package com.example.finalapp.room;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;


// 数据库入口
@Database(entities = {Record.class}, version = 3, exportSchema = false)
@TypeConverters({LatLngListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract RecordDao recordDao();
}
