package com.kora.im.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kora.im.R;
import com.kora.imui.fragment.IMessageFragment;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import com.kora.im.DemoUsers;
import com.kora.im.DemoUserInfo;
import com.kora.imui.MessageBuilder;
import com.kora.imcore.impl.IMMessage;
import java.util.List;
import java.util.ArrayList;

public class P2PChatFragment extends IMessageFragment {
    @Override
    public void onViewCreated(@NotNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onMoreOptionClick(@NotNull String optionName) {
        super.onMoreOptionClick(optionName);
        if ("个人名片".equals(optionName)) {
            showCardSelectorDialog();
        } else if ("位置".equals(optionName)) {
            simulateLocationSelection();
        }
    }

    private void simulateLocationSelection() {
        // Here we simulate opening a map activity and getting a result back
        new AlertDialog.Builder(requireContext())
                .setTitle("模拟地图选点")
                .setMessage("假设你打开了高德/百度地图，并选择了一个位置：\n成都市武侯区天府软件园")
                .setPositiveButton("发送该位置", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        IMMessage msg = MessageBuilder.INSTANCE.createLocationMessage(
                                getSessionId(),
                                getSessionType(),
                                getPeerId() != null && !getPeerId().isEmpty() ? getPeerId() : getSessionId(),
                                30.5432, 104.0623, "成都市武侯区天府软件园"
                        );
                        sendMessage(msg);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCardSelectorDialog() {
        List<String> accounts = DemoUsers.INSTANCE.getAccounts();
        List<String> displayNames = new ArrayList<>();
        for (String account : accounts) {
            DemoUserInfo info = DemoUsers.INSTANCE.demoUser(account);
            if (info != null) {
                displayNames.add(info.getNickname() + " (" + account + ")");
            } else {
                displayNames.add(account);
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("选择要发送的名片")
                .setItems(displayNames.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedAccount = accounts.get(which);
                        DemoUserInfo info = DemoUsers.INSTANCE.demoUser(selectedAccount);
                        if (info != null) {
                            IMMessage msg = MessageBuilder.INSTANCE.createCardMessage(
                                    getSessionId(),
                                    getSessionType(),
                                    getPeerId() != null && !getPeerId().isEmpty() ? getPeerId() : getSessionId(),
                                    info.getAccount(),
                                    info.getNickname(),
                                    info.getAvatarUrl()
                            );
                            sendMessage(msg);
                        }
                    }
                })
                .show();
    }
}
