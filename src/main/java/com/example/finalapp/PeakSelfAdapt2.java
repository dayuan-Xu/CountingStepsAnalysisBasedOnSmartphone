package com.example.finalapp;

import java.util.ArrayList;
import java.util.List;

public class PeakSelfAdapt2 {
    private String status = "静止";
    private boolean statusChanged = false;

    private List<Float> Accs = new ArrayList<>(); // 存储三轴合加速度模值
    private long baseTimestamp = -1; // 第一个数据点的时间戳
    private List<Long> AccsTimestamp = new ArrayList<>(); // 存储时间戳
    private int windowStart = 0; // 滑动窗口起点

    private int kickingPeakNum = 0; // 蹬脚波峰数量（即步数）
    private long lastPeakTimestamp = -1; // 上一次波峰时间戳，-1 表示静止
    private int potentialPeakIndex = 0; // 潜在波峰索引
    private float potentialPeakValue = 0.0f; // 潜在波峰大小
    private long potentialPeakTimestamp = 0; // 潜在波峰时间戳

    private boolean reachedKicking = false; // 是否达到蹬脚阈值
    private final float MIN_ACC = 11.15f; // 蹬脚波峰大小左阈值
    private int MIN_TIME_GAP = 300; // 最小时间间隔（ms）
    private int MAX_TIME_GAP = 830; // 最大时间间隔（ms）
    private int VICINITY_SIZE = 10; // 蹬脚波峰邻域大小

    // 下面检测波谷
    private int treadingValleyNum = 0;
    private long lastValleyTimestamp = 0;
    private int potentialValleyIndex = 0;
    private float potentialValleyValue = 0.0f;
    private long potentialValleyTimestamp = 0;

    private boolean newPeakDetected = false;
    public boolean newValleyDetected = false;
    private int noNewPeakDetectedTimes = 0; // 连续未检测到新步次数
    private int currentSteps = 0;
    private int walking_steps = 0;
    private int running_steps = 0;

    // 终止算法，重置算法状态。
    public void stop() {
        status = "静止";
        statusChanged = false;
        Accs.clear();
        baseTimestamp = -1;
        AccsTimestamp.clear();
        windowStart = 0;

        kickingPeakNum = 0;
        lastPeakTimestamp = -1;

        treadingValleyNum = 0;
        lastValleyTimestamp = 0;

        noNewPeakDetectedTimes = 0;
        currentSteps = 0;
        walking_steps = 0;
        running_steps = 0;
    }

    public void processSensorData(long timestamp, float[] accs) {
        // 初始化本次检测结果
        newPeakDetected = false;
        newValleyDetected = false;

        // 计算三轴合加速度模值
        float acc = (float) Math.sqrt(accs[0] * accs[0] + accs[1] * accs[1] + accs[2] * accs[2]);
        Accs.add(acc);

        if (baseTimestamp == -1) {
            baseTimestamp = timestamp;
        }

        AccsTimestamp.add(timestamp - baseTimestamp);

        if (Accs.size() >= 20) {
            setPotentialPeakInWindow();
            setPotentialValleyInWindow();

            // 如果最近0.4s潜在波峰达到蹬脚大小的加速度，则检测是否是真的蹬脚波峰
            if (reachedKicking) {
                if ("静止".equals(status)) {
                    VICINITY_SIZE = 10;
                } else {
                    if (potentialPeakValue > 2 * 9.8) {
                        MIN_TIME_GAP = 200;
                        VICINITY_SIZE = 5;
                    } else {
                        MIN_TIME_GAP = 300;
                        VICINITY_SIZE = 10;
                    }
                }

                if (timeGapOk()) {
                    if (vicinityOk()) {
                        System.out.printf("于%.2fs探测到新的Peak，波峰时间戳%.2fs\n",
                                (timestamp - baseTimestamp) / 1000.0,
                                potentialPeakTimestamp / 1000.0);
                        newPeakDetected = true;
                        kickingPeakNum++;
                        if (lastPeakTimestamp == -1) {
                            if (potentialPeakValue > 2 * 9.8) {
                                status = "奔跑中";
                            } else {
                                status = "步行中";
                            }
                            statusChanged = true;
                            System.out.printf("于%.2fs检测到运动开始\n", (timestamp - baseTimestamp) / 1000.0);
                        }
                        lastPeakTimestamp = potentialPeakTimestamp;
                        noNewPeakDetectedTimes = 0;

                        if (potentialPeakValue > 2 * 9.8 && "步行中".equals(status)) {
                            status = "奔跑中";
                            statusChanged = true;
                            System.out.printf("于%.2fs探测到从跑步变为行走\n", (timestamp - baseTimestamp) / 1000.0);
                        } else if (potentialPeakValue <= 2 * 9.8 && "奔跑中".equals(status)) {
                            status = "步行中";
                            statusChanged = true;
                            System.out.printf("于%.2fs探测到从行走变为跑步\n", (timestamp - baseTimestamp) / 1000.0);
                        }
                    }
                }
            }

            if (lastPeakTimestamp != -1) {
                long valleyDiff = potentialValleyTimestamp - lastPeakTimestamp;
                if (valleyDiff >= 80 && valleyDiff <= 520) {
                    if (vicinityOkForValley()) {
                        if (potentialValleyTimestamp - lastValleyTimestamp > 0 && kickingPeakNum > treadingValleyNum) {
                            newValleyDetected = true;
                            treadingValleyNum++;
                            lastValleyTimestamp = potentialValleyTimestamp;
                            currentSteps++;
                            if ("奔跑中".equals(status)) {
                                running_steps++;
                            } else if ("步行中".equals(status)) {
                                walking_steps++;
                            }
                            System.out.printf("于%.2fs探测到新的Valley，波谷时间戳%.2fs，认为探测到新的一步\n",
                                    (timestamp - baseTimestamp) / 1000.0,
                                    potentialValleyTimestamp / 1000.0);
                        }
                    }
                }
            }
            windowStart++;
        }

        if (lastPeakTimestamp != -1 && !newPeakDetected) {
            noNewPeakDetectedTimes++;
        }

        if (noNewPeakDetectedTimes > MAX_TIME_GAP / 20) {
            reset();
            statusChanged = true;
            System.out.printf("于%.2f探测到太久没有新波峰，重置运动状态为静止,同时重置算法状态\n",
                    (timestamp - baseTimestamp) / 1000.0);
        }
    }

    private boolean vicinityOk() {
        int index = potentialPeakIndex;
        if (index < VICINITY_SIZE) {
            return false;
        }

        for (int i = index - VICINITY_SIZE; i < index; i++) {
            if (Accs.get(i) > potentialPeakValue) {
                return false;
            }
        }

        if (index + VICINITY_SIZE >= Accs.size()) {
            return false;
        }

        for (int i = index + 1; i <= index + VICINITY_SIZE; i++) {
            if (Accs.get(i) > potentialPeakValue) {
                return false;
            }
        }

        return true;
    }

    private boolean timeGapOk() {
        if (lastPeakTimestamp == -1) {
            return true;
        }
        long diff = potentialPeakTimestamp - lastPeakTimestamp;
        return MIN_TIME_GAP <= diff && diff <= MAX_TIME_GAP;
    }

    private void setPotentialPeakInWindow() {
        if (windowStart + 20 > Accs.size()) {
            return;
        }

        float maxValue = Accs.get(windowStart);
        int maxIndex = windowStart;
        long maxTime = AccsTimestamp.get(windowStart);

        for (int i = windowStart + 1; i < windowStart + 20; i++) {
            if (i < Accs.size()) {
                float val = Accs.get(i);
                if (val > maxValue) {
                    maxValue = val;
                    maxIndex = i;
                    maxTime = AccsTimestamp.get(i);
                }
            }
        }

        potentialPeakValue = maxValue;
        potentialPeakIndex = maxIndex;
        potentialPeakTimestamp = maxTime;

        reachedKicking = potentialPeakValue >= MIN_ACC;
    }

    private void setPotentialValleyInWindow() {
        if (windowStart + 20 > Accs.size()) {
            return;
        }

        float minValue = Accs.get(windowStart);
        int minIndex = windowStart;
        long minTime = AccsTimestamp.get(windowStart);

        for (int i = windowStart + 1; i < windowStart + 20; i++) {
            if (i < Accs.size()) {
                float val = Accs.get(i);
                if (val < minValue) {
                    minValue = val;
                    minIndex = i;
                    minTime = AccsTimestamp.get(i);
                }
            }
        }

        potentialValleyValue = minValue;
        potentialValleyIndex = minIndex;
        potentialValleyTimestamp = minTime;
    }

    // 重置为静止状态
    private void reset() {
        status = "静止";
        statusChanged = false;
        lastPeakTimestamp = -1;
        noNewPeakDetectedTimes = 0;
    }

    private boolean vicinityOkForValley() {
        int index = potentialValleyIndex;
        int vicinitySize = 9;
        if (index < vicinitySize) {
            return false;
        }

        for (int i = index - vicinitySize; i < index; i++) {
            if (Accs.get(i) < potentialValleyValue) {
                return false;
            }
        }

        if (index + vicinitySize >= Accs.size()) {
            return false;
        }

        for (int i = index + 1; i <= index + vicinitySize; i++) {
            if (Accs.get(i) < potentialValleyValue) {
                return false;
            }
        }

        return true;
    }

    public int getCurrentSteps() {
        return currentSteps;
    }

    public int getWalkingSteps() {
        return walking_steps;
    }

    public int getRunningSteps() {
        return running_steps;
    }

}
