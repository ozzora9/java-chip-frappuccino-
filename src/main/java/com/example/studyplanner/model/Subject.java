package com.example.studyplanner.model;

import javafx.scene.paint.Color;

public class Subject {
    private String name;
    private Color color;

    public Subject(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }

    // 콤보박스에 글자만 뜨게 하기 위해 toString 오버라이드
    @Override
    public String toString() {
        return name;
    }
}