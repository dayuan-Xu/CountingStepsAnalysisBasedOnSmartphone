package com.example.finalapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatActivity;

import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MyUtil {
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.CHINA);
    public static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    public static SimpleDateFormat fullFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.CHINA);

    public static BitmapDescriptor getScaledIcon(AppCompatActivity activity, int resId, int widthDp, int heightDp) {
        // 获取原始位图
        Bitmap bitmap = BitmapFactory.decodeResource(activity.getResources(), resId);

        // 转换DP为像素
        int widthPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, widthDp, activity.getResources().getDisplayMetrics());
        int heightPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, heightDp, activity.getResources().getDisplayMetrics());

        // 缩放位图
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, widthPx, heightPx, false);

        return BitmapDescriptorFactory.fromBitmap(scaledBitmap);
    }

    public static String getTimeStamp(String pattern) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
        return dateTimeFormatter.format(LocalDateTime.now());
    }

    public static String formatTimeRange(Date start, Date end) {
        if (isSameDay(start, end)) {
            return dateFormat.format(start) + " "
                    + timeFormat.format(start) + "-"
                    + timeFormat.format(end);
        } else {
            return fullFormat.format(start) + " - " + fullFormat.format(end);
        }
    }

    // 检查两个日期是否在同一天
    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
                cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH);
    }

    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs);
        }
    }

    // 格式化周期信息为字符串
    public static String formatPeriodString(StatusPeriod period) {
        String statusText;
        switch (period.status) {
            case STILL:
                statusText = "静止";
                break;
            case WALKING:
                statusText = "行走";
                break;
            case RUNNING:
                statusText = "跑步";
                break;
            default:
                statusText = "未知";
        }

        String duration = formatDuration(period.endTime - period.startTime);

        return String.format(Locale.CHINA, "%s: %d步 %s",
                statusText,
                period.steps,
                duration);
    }
}
