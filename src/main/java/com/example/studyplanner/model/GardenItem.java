package com.example.studyplanner.model;

public class GardenItem {
    public int layoutId;
    public int flowerId;
    public double x;
    public double y;

    public GardenItem(int layoutId,int flowerId, double x, double y) {
        this.layoutId = layoutId;
        this.flowerId = flowerId;
        this.x = x;
        this.y = y;
    }
}
