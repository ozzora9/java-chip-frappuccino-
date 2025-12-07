package com.example.studyplanner.model;

import javafx.scene.paint.Color;
import java.util.Objects;

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

    @Override
    public String toString() {
        return name;
    }

    // ★ [추가됨] 이름이 같으면 같은 객체로 인식하게 함
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject subject = (Subject) o;
        return Objects.equals(name, subject.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}