package com.kora.imui.inputbox;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class EmojiManager {
    private static volatile EmojiManager instance;
    private List<EmojiItem> emojiList;
    private Pattern emojiPattern;
    private Map<String, String> tagToFileNameMap;
    private EmojiManager() {
        emojiList = new ArrayList<>();
        tagToFileNameMap = new HashMap<>();
        emojiPattern = Pattern.compile("\\[[\\u4e00-\\u9fa5a-zA-Z0-9]+\\]");
    }

    public static EmojiManager getInstance() {
        if (instance == null) {
            synchronized (EmojiManager.class) {
                if (instance == null) {
                    instance = new EmojiManager();
                }
            }
        }
        return instance;
    }
    public Pattern getEmojiPattern() {
        return emojiPattern;
    }
    public void init(Context context) {
        // 如果已经解析过，就不要重复解析
        if (!emojiList.isEmpty()) {
            return;
        }

        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open("emoji/emoji.xml");

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("Emoticon".equals(parser.getName())) {
                        String id = parser.getAttributeValue(null, "ID");
                        String tag = parser.getAttributeValue(null, "Tag");
                        String file = parser.getAttributeValue(null, "File");
                        emojiList.add(new EmojiItem(id, tag, file));
                        tagToFileNameMap.put(tag, file);
                    }
                }
                eventType = parser.next();
            }
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<EmojiItem> getEmojiList() {
        return emojiList;
    }
    // 新增：根据 Tag 获取 Drawable
    public Drawable getDrawable(Context context, String tag) {
        String fileName = tagToFileNameMap.get(tag);
        if (fileName == null) {
            return null;
        }

        AssetManager assetManager = context.getAssets();
        InputStream inputStream = null;
        try {
            String filePath = "emoji/default/" + fileName;
            inputStream = assetManager.open(filePath);
            return Drawable.createFromStream(inputStream, null);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}