package com.example.studyplanner.model;

public class Flower {

    private int id;
    private String name;
    private String meaning;
    private String imagePath;
    private boolean seedUnlocked;
    private boolean cardUnlocked;

    // 🌱 성장 단계 이미지 (카드 표시용)
    private String seedIcon;
    private String sproutIcon;
    private String growIcon;
    private String bloomIcon;

    private int seedQty;
    private int flowerQty;

    public Flower() {}
    
    public Flower(int id, String name, String meaning, String imagePath,
                  boolean seedUnlocked, boolean cardUnlocked,
                  String seedIcon, String sproutIcon,
                  String growIcon, String bloomIcon) {
        this.id = id;
        this.name = name;
        this.meaning = meaning;
        this.imagePath = imagePath;

        this.seedUnlocked = seedUnlocked;
        this.cardUnlocked = cardUnlocked;

        this.seedIcon = seedIcon;
        this.sproutIcon = sproutIcon;
        this.growIcon = growIcon;
        this.bloomIcon = bloomIcon;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getMeaning() { return meaning; }
    public String getImagePath() { return imagePath; }

    public String getSeedIcon() { return seedIcon; }
    public String getSproutIcon() { return sproutIcon; }
    public String getGrowIcon() { return growIcon; }
    public String getBloomIcon() { return bloomIcon; }
    public int getSeedQty() { return seedQty; }
    public int getFlowerQty() {
        return flowerQty;
    }

    public boolean isSeedUnlocked() { return seedUnlocked; }
    public boolean isCardUnlocked() { return cardUnlocked; }



    public void setSeedIcon(String seedIcon) { this.seedIcon = seedIcon; }
    public void setSproutIcon(String sproutIcon) { this.sproutIcon = sproutIcon; }
    public void setGrowIcon(String growIcon) { this.growIcon = growIcon; }
    public void setBloomIcon(String bloomIcon) { this.bloomIcon = bloomIcon; }
    public void setSeedQty(int seedQty) { this.seedQty = seedQty; }
    public void setFlowerQty(int flowerQty) {
        this.flowerQty = flowerQty;
    }

    public void setSeedUnlocked(boolean seedUnlocked) { this.seedUnlocked = seedUnlocked; }
    public void setCardUnlocked(boolean cardUnlocked) { this.cardUnlocked = cardUnlocked; }



}
