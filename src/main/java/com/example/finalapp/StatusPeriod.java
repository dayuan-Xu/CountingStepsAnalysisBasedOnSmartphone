package com.example.finalapp;

public class StatusPeriod {
    public long startTime;
    public long endTime;
    public Status status;
    public int steps;

    public StatusPeriod() {
        this.startTime = 0;
        this.endTime = 0;
        this.status = Status.STILL;
        this.steps = 0;
    }

    public StatusPeriod(long startTime, long endTime, Status status, int steps) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.steps = steps;
    }

}
