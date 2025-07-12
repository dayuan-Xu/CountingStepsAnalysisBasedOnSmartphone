package com.example.finalapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class CircularProgressButton extends View {
    private Paint backgroundPaint;
    private Paint progressPaint;
    private RectF rect = new RectF();
    private float progress = 0; // 0 ~ 360
    private int strokeWidth = 10; // 进度条宽度

    public CircularProgressButton(Context context) {
        super(context);
        init();
    }

    public CircularProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        backgroundPaint = new Paint();
        backgroundPaint.setAntiAlias(true);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidth);
        backgroundPaint.setColor(Color.parseColor("#FFFFFF"));

        progressPaint = new Paint();
        progressPaint.setAntiAlias(true);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        // 使用蓝色进度条
        progressPaint.setColor(Color.BLUE);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float inset = strokeWidth / 2f;
        rect.set(inset, inset, w - inset, h - inset);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 绘制背景圆环
        canvas.drawOval(rect, backgroundPaint);

        // 绘制进度圆弧
        canvas.drawArc(rect, -90, progress, false, progressPaint);
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseResources();
    }

    private void releaseResources() {
        if (backgroundPaint != null) {
            backgroundPaint = null;
        }
        if (progressPaint != null) {
            progressPaint = null;
        }
        rect.setEmpty();
    }
}
