package com.kora.imui.inputbox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kora.imui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 底部输入框与多面板管理器：
 * 1. 动态自适应并持久化保存真实键盘高度，面板与软键盘等高。
 * 2. 键盘与面板严格互斥：键盘开启时面板 100% 隐藏，面板开启时键盘 100% 隐藏。
 * 3. 采用可靠的全局布局高度监听与延迟防堆叠机制，彻底杜绝面板与键盘同时存在的 550dp 挤压截断问题。
 */
public class ChatInputView extends LinearLayout {

    private static final String PREF_NAME = "im_keyboard_pref";
    private static final String KEY_KEYBOARD_HEIGHT = "key_keyboard_height";

    private ImageView ivVoice;
    private EditText etMessage;
    private TextView btnVoiceRecord;
    private ImageView ivEmoji;
    private ImageView ivMore;
    private TextView btnSend;
    private FrameLayout panelMore;
    private FrameLayout panelEmoji;

    private int keyboardHeight = 0;
    private OnInputListener listener;
    private RecyclerView rvMoreOptions;
    private RecyclerView rvEmoji;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isKeyboardShowing = false;

    private enum InputMode {
        TEXT, VOICE, EMOJI, MORE, NONE
    }
    
    private InputMode currentMode = InputMode.NONE;

    public interface OnInputListener {
        void onSendMessage(String message);
        void onVoiceClick();
        void onMoreOptionClick(String optionName);
        void onEmojiClick(String emojiTag);
        
        // Voice record callbacks
        void onVoiceRecordStart();
        void onVoiceRecordMove(boolean willCancel);
        void onVoiceRecordEnd();
        void onVoiceRecordCancel();

        default void onTyping() {}
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
        LayoutInflater.from(context).inflate(R.layout.view_chat_input, this, true);
        setOrientation(VERTICAL);

        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int defaultHeight = (int) (270 * context.getResources().getDisplayMetrics().density + 0.5f);
        keyboardHeight = sp.getInt(KEY_KEYBOARD_HEIGHT, defaultHeight);

        ivVoice = findViewById(R.id.iv_voice);
        etMessage = findViewById(R.id.et_message);
        btnVoiceRecord = findViewById(R.id.btn_voice_record);
        ivEmoji = findViewById(R.id.iv_emoji);
        ivMore = findViewById(R.id.iv_more);
        btnSend = findViewById(R.id.btn_send);
        panelMore = findViewById(R.id.panel_more);
        panelEmoji = findViewById(R.id.panel_emoji);
        rvMoreOptions = findViewById(R.id.rv_more_options);
        rvEmoji = findViewById(R.id.rv_emoji);

        // 初始化时确保两个面板高度与软键盘等高，且全部处于 GONE 隐藏状态
        preparePanel(panelMore);
        preparePanel(panelEmoji);
        hideAllPanels();

        setupListeners();
        setupGlobalLayoutListener();
        setupMoreOptionsPanel();
        setupEmojiPanel();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupListeners() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
                if (s != null && s.length() > 0 && listener != null) {
                    listener.onTyping();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 点击输入框切回文本键盘输入
        etMessage.setOnClickListener(v -> {
            switchMode(InputMode.TEXT);
        });
        etMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                switchMode(InputMode.TEXT);
            }
        });

        btnSend.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSendMessage(etMessage.getText().toString());
                etMessage.setText("");
            }
        });

        ivVoice.setOnClickListener(v -> {
            if (currentMode == InputMode.VOICE) {
                switchMode(InputMode.TEXT);
            } else {
                switchMode(InputMode.VOICE);
            }
        });

        ivMore.setOnClickListener(v -> {
            if (currentMode == InputMode.MORE) {
                switchMode(InputMode.TEXT);
            } else {
                switchMode(InputMode.MORE);
            }
        });

        ivEmoji.setOnClickListener(v -> {
            if (currentMode == InputMode.EMOJI) {
                switchMode(InputMode.TEXT);
            } else {
                switchMode(InputMode.EMOJI);
            }
        });
        
        setupVoiceRecordButton();
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupVoiceRecordButton() {
        btnVoiceRecord.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    btnVoiceRecord.setText("松开 结束");
                    btnVoiceRecord.setPressed(true);
                    if (listener != null) listener.onVoiceRecordStart();
                    break;
                case MotionEvent.ACTION_MOVE:
                    boolean willCancel = event.getY() < -50;
                    if (willCancel) {
                        btnVoiceRecord.setText("松开 取消");
                    } else {
                        btnVoiceRecord.setText("松开 结束");
                    }
                    if (listener != null) {
                        listener.onVoiceRecordMove(willCancel);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    btnVoiceRecord.setPressed(false);
                    btnVoiceRecord.setText("按住 说话");
                    if (listener != null) {
                        if (event.getY() < -50) {
                            listener.onVoiceRecordCancel();
                        } else {
                            listener.onVoiceRecordEnd();
                        }
                    }
                    break;
            }
            return true;
        });
    }

    private void updateSendButtonState() {
        if (etMessage.getText().length() > 0 && currentMode != InputMode.VOICE) {
            ivMore.setVisibility(GONE);
            btnSend.setVisibility(VISIBLE);
        } else {
            ivMore.setVisibility(VISIBLE);
            btnSend.setVisibility(GONE);
        }
    }

    private void switchMode(InputMode newMode) {
        if (currentMode == newMode) return;
        
        InputMode oldMode = currentMode;
        currentMode = newMode;
        mainHandler.removeCallbacksAndMessages(null);

        // 重置按钮图标
        ivVoice.setImageResource(R.drawable.ic_voice);
        ivEmoji.setImageResource(R.drawable.ic_emoji);
        
        switch (newMode) {
            case TEXT:
                btnVoiceRecord.setVisibility(GONE);
                etMessage.setVisibility(VISIBLE);
                updateSendButtonState();
                hideAllPanels();
                showKeyboard();
                break;
                
            case VOICE:
                ivVoice.setImageResource(R.drawable.ic_keyboard);
                etMessage.setVisibility(GONE);
                btnVoiceRecord.setVisibility(VISIBLE);
                hideKeyboard();
                hideAllPanels();
                ivMore.setVisibility(VISIBLE);
                btnSend.setVisibility(GONE);
                break;
                
            case EMOJI:
                ivEmoji.setImageResource(R.drawable.ic_keyboard);
                btnVoiceRecord.setVisibility(GONE);
                etMessage.setVisibility(VISIBLE);
                updateSendButtonState();
                
                if (oldMode == InputMode.TEXT && isKeyboardShowing) {
                    hideKeyboard();
                    // 等待软键盘开始收起后再显示面板，绝不同步占位
                    mainHandler.postDelayed(() -> {
                        hideAllPanels();
                        showPanel(panelEmoji);
                    }, 120);
                } else {
                    hideKeyboard();
                    hideAllPanels();
                    showPanel(panelEmoji);
                }
                break;
                
            case MORE:
                btnVoiceRecord.setVisibility(GONE);
                etMessage.setVisibility(VISIBLE);
                updateSendButtonState();
                
                if (oldMode == InputMode.TEXT && isKeyboardShowing) {
                    hideKeyboard();
                    // 等待软键盘开始收起后再显示面板，绝不同步占位
                    mainHandler.postDelayed(() -> {
                        hideAllPanels();
                        showPanel(panelMore);
                    }, 120);
                } else {
                    hideKeyboard();
                    hideAllPanels();
                    showPanel(panelMore);
                }
                break;
                
            case NONE:
                hideKeyboard();
                hideAllPanels();
                break;
        }
    }
    
    private void showPanel(View panel) {
        preparePanel(panel);
        panel.setVisibility(VISIBLE);
    }

    private void preparePanel(View panel) {
        if (panel == null) return;
        int targetHeight = keyboardHeight > 0 ? keyboardHeight : (int) (270 * getResources().getDisplayMetrics().density);
        if (panel.getLayoutParams() != null && panel.getLayoutParams().height != targetHeight) {
            panel.getLayoutParams().height = targetHeight;
            panel.requestLayout();
        }
    }

    private void setupGlobalLayoutListener() {
        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!(getContext() instanceof Activity)) return;
            Activity activity = (Activity) getContext();
            View decorView = activity.getWindow().getDecorView();
            Rect r = new Rect();
            decorView.getWindowVisibleDisplayFrame(r);
            int screenHeight = decorView.getHeight();
            int heightDiff = screenHeight - r.bottom;

            int minKeyboardHeight = (int) (120 * getResources().getDisplayMetrics().density);
            boolean isKeyboardNowVisible = heightDiff > minKeyboardHeight;
            isKeyboardShowing = isKeyboardNowVisible;

            if (isKeyboardNowVisible) {
                if (keyboardHeight != heightDiff) {
                    keyboardHeight = heightDiff;
                    getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_KEYBOARD_HEIGHT, heightDiff)
                            .apply();
                    preparePanel(panelMore);
                    preparePanel(panelEmoji);
                }

                // 键盘在屏幕上可见时，强制隐藏所有面板，防止任何情况下产生 550dp 双重堆叠
                if (panelMore.getVisibility() == VISIBLE || panelEmoji.getVisibility() == VISIBLE) {
                    hideAllPanels();
                }
                if (currentMode != InputMode.TEXT && currentMode != InputMode.NONE) {
                    currentMode = InputMode.TEXT;
                    ivVoice.setImageResource(R.drawable.ic_voice);
                    ivEmoji.setImageResource(R.drawable.ic_emoji);
                }
            } else {
                if (currentMode == InputMode.TEXT && !etMessage.isFocused()) {
                    currentMode = InputMode.NONE;
                }
            }
        });
    }

    private void setupMoreOptionsPanel() {
        rvMoreOptions.setLayoutManager(new GridLayoutManager(getContext(), 4));
        List<MoreOptionItem> options = new ArrayList<>();
        options.add(new MoreOptionItem("图片", R.drawable.ic_more_album));
        options.add(new MoreOptionItem("视频", R.drawable.ic_more_camera));
        options.add(new MoreOptionItem("文件", R.drawable.ic_more_file));
        options.add(new MoreOptionItem("视频通话", R.drawable.ic_more_video_call));
        options.add(new MoreOptionItem("语音通话", R.drawable.ic_more_voice_call));
        options.add(new MoreOptionItem("位置", R.drawable.ic_more_location));
        options.add(new MoreOptionItem("红包", R.drawable.ic_more_red_packet));
        options.add(new MoreOptionItem("个人名片", R.drawable.ic_more_card));

        MoreOptionsAdapter adapter = new MoreOptionsAdapter(getContext(), options);
        adapter.setOnItemClickListener(item -> {
            if (listener != null) listener.onMoreOptionClick(item.getName());
        });
        rvMoreOptions.setAdapter(adapter);
    }

    private void setupEmojiPanel() {
        EmojiManager.getInstance().init(getContext());
        List<EmojiItem> emojiList = EmojiManager.getInstance().getEmojiList();

        rvEmoji.setLayoutManager(new GridLayoutManager(getContext(), 8));
        EmojiAdapter adapter = new EmojiAdapter(getContext(), emojiList);
        adapter.setOnEmojiClickListener(emojiTag -> {
            if (listener != null) listener.onEmojiClick(emojiTag);
        });
        rvEmoji.setAdapter(adapter);
    }

    public void insertText(String text) {
        int start = Math.max(etMessage.getSelectionStart(), 0);
        int end = Math.max(etMessage.getSelectionEnd(), 0);
        etMessage.getText().replace(Math.min(start, end), Math.max(start, end), text, 0, text.length());
        EmojiDisplayUtils.display(getContext(), etMessage, etMessage.getText());
    }

    public void hideAllPanels() {
        panelMore.setVisibility(GONE);
        panelEmoji.setVisibility(GONE);
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
        if (currentMode != InputMode.NONE && currentMode != InputMode.TEXT) {
            switchMode(InputMode.NONE);
            return true;
        }
        return false;
    }
}

