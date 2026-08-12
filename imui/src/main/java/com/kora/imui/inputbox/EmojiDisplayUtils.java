package com.kora.imui.inputbox;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.widget.EditText;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表情显示工具类
 * 用于将文本中的表情标签（如 [可爱]）转换成图片进行显示
 */
public class EmojiDisplayUtils {

    public static void display(Context context, TextView textView, CharSequence text) {
        EmojiManager.getInstance().init(context);
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        Pattern emojiPattern = EmojiManager.getInstance().getEmojiPattern();
        if (emojiPattern == null) {
            textView.setText(text); // 如果模式为空，直接设置文本
            return;
        }
        
        Matcher matcher = emojiPattern.matcher(text);

        while (matcher.find()) {
            String tag = matcher.group(); // 获取匹配到的标签，例如 "[可爱]"
            Drawable drawable = EmojiManager.getInstance().getDrawable(context, tag);

            if (drawable != null) {
                int size = (int) (textView.getTextSize() * 1.2f);
                drawable.setBounds(0, 0, size, size);
                ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
                // SPAN_INCLUSIVE_EXCLUSIVE 表示在 span 前面插入字符时应用 span，在后面插入时不应用
                spannable.setSpan(imageSpan, matcher.start(), matcher.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            }
        }
        
        textView.setText(spannable);
    }
    public static void display(Context context, EditText textView, CharSequence text) {
        EmojiManager.getInstance().init(context);
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        Pattern emojiPattern = EmojiManager.getInstance().getEmojiPattern();
        if (emojiPattern == null) {
            textView.setText(text); // 如果模式为空，直接设置文本
            return;
        }

        Matcher matcher = emojiPattern.matcher(text);

        while (matcher.find()) {
            String tag = matcher.group(); // 获取匹配到的标签，例如 "[可爱]"
            Drawable drawable = EmojiManager.getInstance().getDrawable(context, tag);

            if (drawable != null) {
                int size = (int) (textView.getTextSize() * 1.2f);
                drawable.setBounds(0, 0, size, size);
                ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM);
                // SPAN_INCLUSIVE_EXCLUSIVE 表示在 span 前面插入字符时应用 span，在后面插入时不应用
                spannable.setSpan(imageSpan, matcher.start(), matcher.end(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            }
        }

        textView.setText(spannable);
        textView.setSelection(spannable.length());
    }
}