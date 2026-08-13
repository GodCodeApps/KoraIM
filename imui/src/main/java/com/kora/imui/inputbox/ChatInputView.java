package com.kora.imui.inputbox;

import android.annotation.SuppressLint;
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
        void onVoiceRecordEnd();
        void onVoiceRecordCancel();
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

        setupListeners();
        setupKeyboardListener();
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
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etMessage.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                switchMode(InputMode.TEXT);
            }
            return false;
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
                    // Simple cancel detection (e.g. swipe up)
                    if (event.getY() < -50) {
                        btnVoiceRecord.setText("松开 取消");
                    } else {
                        btnVoiceRecord.setText("松开 结束");
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
        
        // Reset icons
        ivVoice.setImageResource(R.drawable.ic_voice);
        ivEmoji.setImageResource(R.drawable.ic_emoji);
        
        switch (newMode) {
            case TEXT:
                btnVoiceRecord.setVisibility(GONE);
                etMessage.setVisibility(VISIBLE);
                hideAllPanels();
                showKeyboard();
                updateSendButtonState();
                break;
                
            case VOICE:
                ivVoice.setImageResource(R.drawable.ic_keyboard); // Replace with keyboard icon
                etMessage.setVisibility(GONE);
                btnVoiceRecord.setVisibility(VISIBLE);
                hideKeyboard();
                hideAllPanels();
                ivMore.setVisibility(VISIBLE);
                btnSend.setVisibility(GONE);
                break;
                
            case EMOJI:
                ivEmoji.setImageResource(R.drawable.ic_keyboard); // Replace with keyboard icon
                btnVoiceRecord.setVisibility(GONE);
                etMessage.setVisibility(VISIBLE);
                updateSendButtonState();
                
                if (oldMode == InputMode.TEXT) {
                    hideKeyboard();
                    postDelayed(() -> showPanel(panelEmoji), 100);
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
                
                if (oldMode == InputMode.TEXT) {
                    hideKeyboard();
                    postDelayed(() -> showPanel(panelMore), 100);
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
        if (keyboardHeight > 0) {
            panel.getLayoutParams().height = keyboardHeight;
            panel.requestLayout();
        }
        panel.setVisibility(VISIBLE);
    }

    private void setupKeyboardListener() {
        ViewCompat.setOnApplyWindowInsetsListener(getRootView(), (v, insets) -> {
            boolean isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int currentKeyboardHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            if (isKeyboardVisible) {
                if (currentKeyboardHeight > 0) {
                    keyboardHeight = currentKeyboardHeight;
                }
                if (currentMode == InputMode.EMOJI || currentMode == InputMode.MORE || currentMode == InputMode.NONE) {
                    currentMode = InputMode.TEXT;
                    ivVoice.setImageResource(R.drawable.ic_voice);
                    ivEmoji.setImageResource(R.drawable.ic_emoji);
                    hideAllPanels();
                }
            } else {
                if (currentMode == InputMode.TEXT && !etMessage.isFocused()) {
                    currentMode = InputMode.NONE;
                }
            }
            return insets;
        });
    }

    private void setupMoreOptionsPanel() {
        rvMoreOptions.setLayoutManager(new GridLayoutManager(getContext(), 4));
        List<MoreOptionItem> options = new ArrayList<>();
        options.add(new MoreOptionItem("图片", R.drawable.ic_more_album));
        options.add(new MoreOptionItem("视频", R.drawable.ic_more_camera));
        options.add(new MoreOptionItem("视频通话", R.drawable.ic_more_video_call));
        options.add(new MoreOptionItem("位置", R.drawable.ic_more_location));
        options.add(new MoreOptionItem("红包", R.drawable.ic_more_red_packet));

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