package com.kora.imui.inputbox;

public class MoreOptionItem {
    private String name;
    private int iconResId;

    public MoreOptionItem(String name, int iconResId) {
        this.name = name;
        this.iconResId = iconResId;
    }

    public String getName() {
        return name;
    }

    public int getIconResId() {
        return iconResId;
    }
}