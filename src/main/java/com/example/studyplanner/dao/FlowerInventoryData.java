package com.example.studyplanner.dao;

public class FlowerInventoryData {
    public int flowerId;
    public int seedQty;
    public int flowerQty;
    public boolean seedUnlocked;
    public boolean cardUnlocked;

    public FlowerInventoryData(int id, int s_qty, int f_qty, boolean s, boolean c) {
        flowerId = id;
        seedQty = s_qty;
        flowerQty = f_qty;
        seedUnlocked = s;
        cardUnlocked = c;
    }
}
