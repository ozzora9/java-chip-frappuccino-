package com.example.studyplanner.model;

public class Flower {

    private int id;
    private String name;
    private String meaning;
    private String imagePath;
    private boolean unlocked;

    // 🌱 성장 단계 이미지 (카드 표시용)
    private String seedIcon;
    private String sproutIcon;
    private String growIcon;
    private String bloomIcon;

    private int quantity;

    public Flower() {}
    
    public Flower(int id, String name, String meaning, String imagePath,
                  boolean unlocked,
                  String seedIcon, String sproutIcon,
                  String growIcon, String bloomIcon) {
        this.id = id;
        this.name = name;
        this.meaning = meaning;
        this.imagePath = imagePath;
        this.unlocked = unlocked;

        this.seedIcon = seedIcon;
        this.sproutIcon = sproutIcon;
        this.growIcon = growIcon;
        this.bloomIcon = bloomIcon;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getMeaning() { return meaning; }
    public String getImagePath() { return imagePath; }

    public boolean isUnlocked() { return unlocked; }

    public String getSeedIcon() { return seedIcon; }
    public String getSproutIcon() { return sproutIcon; }
    public String getGrowIcon() { return growIcon; }
    public String getBloomIcon() { return bloomIcon; }
    public int getQuantity() { return quantity; }

    public void setSeedIcon(String seedIcon) { this.seedIcon = seedIcon; }
    public void setSproutIcon(String sproutIcon) { this.sproutIcon = sproutIcon; }
    public void setGrowIcon(String growIcon) { this.growIcon = growIcon; }
    public void setBloomIcon(String bloomIcon) { this.bloomIcon = bloomIcon; }

    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
