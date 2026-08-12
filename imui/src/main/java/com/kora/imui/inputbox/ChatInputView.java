package com.kora.imui.inputbox;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kora.imui.R;

import java.util.ArrayList;
import java.util.List;

public class ChatInputView extends LinearLayout {

    private ImageView ivVoice;
    private EditText etMessage;
    private ImageView ivEmoji;
    private ImageView ivMore;
    private TextView btnSend;
    private FrameLayout panelMore;
    private FrameLayout panelEmoji;

    private int keyboardHeight = 0;
    private OnInputListener listener;
    private RecyclerView rvMoreOptions;
    private RecyclerView rvEmoji;

    // 定义事件回调接口
    public interface OnInputListener {
        void onSendMessage(String message);

        void onVoiceClick();

        void onMoreOptionClick(String optionName);

        void onEmojiClick(String emojiTag);
    }

    public void setOnInputListener(OnInputListener listener) {
        this.listener = listener;
    }

    public ChatInputView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ChatInputView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // 加载布局
        LayoutInflater.from(context).inflate(R.layout.view_chat_input, this, true);
        setOrientation(VERTICAL);

        // 初始化视图
        ivVoice = findViewById(R.id.iv_voice);
        etMessage = findViewById(R.id.et_message);
        ivEmoji = findViewById(R.id.iv_emoji);
        ivMore = findViewById(R.id.iv_more);
        btnSend = findViewById(R.id.btn_send);
        panelMore = findViewById(R.id.panel_more);
        panelEmoji = findViewById(R.id.panel_emoji);
        rvMoreOptions = findViewById(R.id.rv_more_options);
        rvEmoji = findViewById(R.id.rv_emoji);
        // 设置监听器
        setupListeners();
        // 监听键盘高度
        setupKeyboardListener();
        setupMoreOptionsPanel();
        setupEmojiPanel();
    }

    private void setupListeners() {
        // 监听文本输入，动态切换“更多”和“发送”按钮
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    ivMore.setVisibility(GONE);
                    btnSend.setVisibility(VISIBLE);
                } else {
                    ivMore.setVisibility(VISIBLE);
                    btnSend.setVisibility(GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 点击输入框时，隐藏面板并显示键盘
//        etMessage.setOnClickListener(v -> {
//            if (isPanelVisible()) {
//                hideAllPanels();
//                showKeyboard();
//            }
//        });
        etMessage.setOnTouchListener((v, event) -> {
            // 我们只关心手指抬起的动作，这代表一次点击的完成
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // 情况一：面板是可见的，我们需要切换到键盘
                if (isPanelVisible()) {
//                    isKeyboardRequested = true;
                    // 先隐藏面板
                    hideAllPanels();
                    // 延迟显示键盘以保证平滑过渡
                    postDelayed(this::showKeyboard, 100);
                }
                // 情况二：面板是不可见的，就是一次普通的点击，直接显示键盘
                else {
                    showKeyboard();
                }
            }
            // 返回 false 让系统继续处理 EditText 的默认触摸行为（例如显示光标）
            return false;
        });

        // 发送按钮
        btnSend.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSendMessage(etMessage.getText().toString());
                etMessage.setText("");
            }
        });

        // 语音按钮
        ivVoice.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVoiceClick();
            }
        });

        // “更多”按钮
        ivMore.setOnClickListener(v -> togglePanel(panelMore));

        // 表情按钮
        ivEmoji.setOnClickListener(v -> togglePanel(panelEmoji));
    }

    private void setupKeyboardListener() {
        ViewCompat.setOnApplyWindowInsetsListener(getRootView(), (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int currentKeyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            if (isKeyboardVisible) {
                if (currentKeyboardHeight > 0) {
                    keyboardHeight = currentKeyboardHeight;
                }
                // 键盘弹出时，自动隐藏所有面板
                if (isPanelVisible()) {
                    hideAllPanels();
                }
            } else {
                if (etMessage.isFocused()) {
                    // 如果焦点还在输入框，说明是代码隐藏的键盘，此时面板应该弹出
                } else {
                    hideAllPanels();
                }
            }
            return insets;
        });
    }

    private void setupMoreOptionsPanel() {
        rvMoreOptions.setLayoutManager(new GridLayoutManager(getContext(), 4)); // 4列

        List<MoreOptionItem> options = new ArrayList<>();
        options.add(new MoreOptionItem("相册", R.drawable.ic_more_album));
        options.add(new MoreOptionItem("拍照", R.drawable.ic_more_camera));
        options.add(new MoreOptionItem("视频通话", R.drawable.ic_more_video_call));
        options.add(new MoreOptionItem("位置", R.drawable.ic_more_location));
        options.add(new MoreOptionItem("红包", R.drawable.ic_more_red_packet));

        MoreOptionsAdapter adapter = new MoreOptionsAdapter(getContext(), options);
        adapter.setOnItemClickListener(item -> {
            if (listener != null) {
                // 将点击事件传递给Activity
                listener.onMoreOptionClick(item.getName());
            }
        });

        rvMoreOptions.setAdapter(adapter);
    }

    private void setupEmojiPanel() {
        // 初始化并解析表情数据
        EmojiManager.getInstance().init(getContext());
        List<EmojiItem> emojiList = EmojiManager.getInstance().getEmojiList();

        // 设置 RecyclerView
        rvEmoji.setLayoutManager(new GridLayoutManager(getContext(), 8)); // 8列
        EmojiAdapter adapter = new EmojiAdapter(getContext(), emojiList);
        adapter.setOnEmojiClickListener(emojiTag -> {
            if (listener != null) {
                // 将点击事件传递给 Activity
                listener.onEmojiClick(emojiTag);
            }
        });
        rvEmoji.setAdapter(adapter);
    }


    public void insertText(String text) {
        int start = Math.max(etMessage.getSelectionStart(), 0);
        int end = Math.max(etMessage.getSelectionEnd(), 0);
        etMessage.getText().replace(Math.min(start, end), Math.max(start, end),
                text, 0, text.length());

        EmojiDisplayUtils.display(getContext(),etMessage,etMessage.getText());
    }

    private void togglePanel(View panel) {
        if (panel.getVisibility() == VISIBLE) {
            // 如果面板可见，则隐藏它并显示键盘
            hideAllPanels();
            showKeyboard();
            etMessage.requestFocus();

        } else {
            // 如果面板不可见
            hideKeyboard();
            // 先隐藏其他面板
            hideAllPanels(panel); // 隐藏除当前面板外的所有面板

            // 延迟显示，等待键盘完全收起
            postDelayed(() -> {

                // 设置面板高度为键盘高度
                if (keyboardHeight > 0) {
                    panel.getLayoutParams().height = keyboardHeight;
                    panel.requestLayout();
                }
                panel.setVisibility(VISIBLE);
                panel.requestFocus();

            }, 200);
        }
    }

    public void hideAllPanels() {
        hideAllPanels(null);
    }

    private void hideAllPanels(View exceptPanel) {
        if (exceptPanel != panelMore) panelMore.setVisibility(GONE);
        if (exceptPanel != panelEmoji) panelEmoji.setVisibility(GONE);
    }

    private boolean isPanelVisible() {
        return panelMore.getVisibility() == VISIBLE || panelEmoji.getVisibility() == VISIBLE;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (getContext() instanceof Activity) {
            imm.hideSoftInputFromWindow(((Activity) getContext()).getWindow().getDecorView().getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        etMessage.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
    }


    public boolean onInterceptBackPressed() {
        if (isPanelVisible()) {
            hideAllPanels();
            return true;
        }
        return false;
    }
}