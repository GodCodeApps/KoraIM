package com.kora.im.chat;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kora.im.R;
import com.kora.imcore.constant.SessionType;

public class ChatActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        int sessionType = getIntent().getIntExtra("session_type", SessionType.P2P);
        String sessionId = getIntent().getStringExtra("session_id");
        String peerId = getIntent().getStringExtra("peer_id");
        P2PChatFragment p2PChatFragment = new P2PChatFragment();
        Bundle bundle = new Bundle();
        bundle.putInt("session_type", sessionType);
        bundle.putString("session_id", sessionId);
        bundle.putString("peer_id", peerId);
        p2PChatFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, p2PChatFragment).commit();
    }
}
