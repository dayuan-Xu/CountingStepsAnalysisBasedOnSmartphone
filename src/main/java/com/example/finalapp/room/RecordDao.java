package com.example.finalapp.room;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

// 数据访问接口
@Dao
public interface RecordDao {
    @Insert
    void insert(Record record);

    @Query("SELECT * FROM records ORDER BY startTime DESC")
    List<Record> getAllRecords();

    @Query("SELECT * FROM records WHERE id = :id")
    Record getRecordById(int id);

    //删除方法
    @Delete
    void delete(Record record);

    // 新增：删除所有记录
    @Query("DELETE FROM records")
    void deleteAllRecords();
}
