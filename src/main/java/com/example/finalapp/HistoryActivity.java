package com.example.finalapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalapp.room.AppDatabase;
import com.example.finalapp.room.Record;

import java.util.List;

// 历史界面，展示记录列表
//功能：
//查询数据库获取所有记录
//使用RecordAdapter显示列表
//处理记录点击事件
public class HistoryActivity extends AppCompatActivity {
    private static final String TAG = "我的日志-HistoryActivity";
    private ListView listView;
    private RecordAdapter adapter;
    private List<Record> records;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 添加返回按钮支持
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        listView = findViewById(R.id.history_list);
        // 从数据库获取记录
        AppDatabase db = MyApp.getDatabase();
        records = db.recordDao().getAllRecords();
        // 创建RecordAdapter
        adapter = new RecordAdapter(this, records);
        listView.setAdapter(adapter);

        // 添加点击事件监听器
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Record record = adapter.getItem(position);
            if (record != null) {
                Intent intent = new Intent(HistoryActivity.this, RecordDetailActivity.class);
                intent.putExtra("record_id", record.id);
                startActivity(intent);
            }
        });

        // 添加长按删除功能
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Record record = adapter.getItem(position);
            showDeleteConfirmationDialog(record, position);
            return true; // 消费长按事件
        });
    }

    private void showDeleteConfirmationDialog(Record record, int position) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定要删除这条记录吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteRecord(record, position))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteRecord(Record record, int position) {
        // 在后台线程执行数据库删除
        new Thread(() -> {
            AppDatabase db = MyApp.getDatabase();
            db.recordDao().delete(record);

            // 在主线程更新UI
            runOnUiThread(() -> {
                records.remove(position);
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "记录已删除", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }


    private void showDeleteAllConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("清空所有记录")
                .setMessage("确定要删除所有记录吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> deleteAllRecords())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteAllRecords() {
        new Thread(() -> {
            AppDatabase db = MyApp.getDatabase();
            db.recordDao().deleteAllRecords(); // 假设 RecordDao 提供了 deleteAllRecords 方法

            runOnUiThread(() -> {
                records.clear();
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "所有记录已删除", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.history_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (item.getItemId() == android.R.id.home) {
            finish(); // 关闭当前Activity
            Log.d(TAG, "用户点击了返回按钮");
            return true;
        } else if (id == R.id.menu_clear_all) {
            showDeleteAllConfirmationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
