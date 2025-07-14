package com.example.finalapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.finalapp.room.Record;

import java.util.List;
import java.util.Locale;

// 将Record对象绑定到列表项视图；格式化显示数据
public class RecordAdapter extends ArrayAdapter<Record> {
    private final LayoutInflater inflater;

    public RecordAdapter(Context context, List<Record> records) {
        super(context, 0, records);
        inflater = LayoutInflater.from(context);
    }

    // 该方法会在历史界面需要显示某个列表项时自动调用
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            // 创建item_record 布局，返回视图对象
            convertView = inflater.inflate(R.layout.item_record, parent, false);
        }

        Record record = getItem(position);

        TextView textView = convertView.findViewById(R.id.tvItem);
        // 优化时间显示：如果开始和结束在同一天，则只显示日期一次
        String timeText = MyUtil.formatTimeRange(record.startTime, record.endTime);

        String dis = String.format(Locale.CHINA, "距离: %.1f米", record.distance);
        String duration = record.duration;
        String steps = String.format(Locale.CHINA, "总步数: %d", record.steps);
        String walking_steps = String.format(Locale.CHINA, " 步行步数: %d", record.walking_steps);
        String running_steps = String.format(Locale.CHINA, " 跑步步数: %d", record.running_steps);

        // 绑定数据
        textView.setText(timeText + "\n" + dis + " 时长: " + duration + "\n" + steps + walking_steps + running_steps);

        // 返回填充好的视图
        return convertView;
    }
}
