package com.kora.imui.inputbox;

public class EmojiItem {
    private String id;
    private String tag; // 例如 "[可爱]"
    private String fileName; // 例如 "emoji_01.png"

    public EmojiItem(String id, String tag, String fileName) {
        this.id = id;
        this.tag = tag;
        this.fileName = fileName;
    }

    public String getId() { return id; }
    public String getTag() { return tag; }
    public String getFileName() { return fileName; }
}