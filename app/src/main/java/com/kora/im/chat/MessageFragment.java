package com.kora.im.chat;

import android.content.Intent;

import com.kora.imcore.db.Conversation;
import com.kora.imui.fragment.IConversationListFragment;

import org.jetbrains.annotations.NotNull;

public class MessageFragment extends IConversationListFragment {


    @Override
    protected void onConversationClick(@NotNull Conversation conversation) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra("session_type",conversation.getSessionType());
        intent.putExtra("session_id",conversation.getSessionId());
        intent.putExtra("peer_id",conversation.getPeerId());
        startActivity(intent);
    }
}
