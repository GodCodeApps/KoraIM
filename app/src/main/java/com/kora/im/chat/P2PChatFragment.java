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
        if (com.kora.imui.ImUIKitImpl.INSTANCE.getSessionListener() == null) {
            com.kora.imui.ImUIKitImpl.INSTANCE.setSessionEventListener(new com.kora.imui.listener.SessionEventListener());
        }
        com.kora.imui.ImUIKitImpl.INSTANCE.getSessionListener().onForwardMessageListener(message -> { showForwardSelectorDialog(message); return kotlin.Unit.INSTANCE; });
    }

    private void showForwardSelectorDialog(IMMessage original) {
        com.kora.imcore.attachment.MsgAttachment attachment = original.getAttachment();
        if (!(attachment instanceof com.kora.imui.attachment.TextAttachment)
                && !(attachment instanceof com.kora.imui.attachment.ImageAttachment)
                && !(attachment instanceof com.kora.imui.attachment.VideoAttachment)
                && !(attachment instanceof com.kora.imui.attachment.FileAttachment)
                && !(attachment instanceof com.kora.imui.attachment.LocationAttachment)
                && !(attachment instanceof com.kora.imui.attachment.CardAttachment)) {
            android.widget.Toast.makeText(requireContext(), "当前消息暂不支持转发", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> accounts = DemoUsers.INSTANCE.getAccounts();
        List<String> names = new ArrayList<>();
        for (String account : accounts) {
            DemoUserInfo info = DemoUsers.INSTANCE.demoUser(account);
            names.add(info != null ? info.getNickname() + " (" + account + ")" : account);
        }
        new AlertDialog.Builder(requireContext()).setTitle("选择转发对象")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    String target = accounts.get(which);
                    if (target.equals(com.kora.imcore.ImSdkImpl.INSTANCE.getAccount())) return;
                    sendMessageToOtherSession(MessageBuilder.INSTANCE.createForwardedMessage(target, getSessionType(), target, attachment));
                }).show();
    }

    @Override
    public void onMoreOptionClick(@NotNull String optionName) {
        super.onMoreOptionClick(optionName);
        if ("个人名片".equals(optionName)) {
            showCardSelectorDialog();
        } else if ("位置".equals(optionName)) {
            simulateLocationSelection();
        } else if ("红包".equals(optionName)) {
            showRedPacketDialog();
        }
    }

    private void showRedPacketDialog() {
        android.content.Context context = getContext();
        if (context == null) return;
        android.view.View content = android.view.LayoutInflater.from(context)
                .inflate(com.kora.im.R.layout.dialog_send_red_packet, null, false);
        android.widget.EditText amountInput = content.findViewById(com.kora.im.R.id.et_red_packet_amount);
        android.widget.EditText greetingInput = content.findViewById(com.kora.im.R.id.et_red_packet_greeting);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("发红包")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("塞钱进红包", null)
                .create();
        dialog.setOnShowListener(d -> {
            android.widget.Button btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            btn.setTextColor(android.graphics.Color.parseColor("#D94E3F"));
            btn.setOnClickListener(v -> {
                String amountStr = amountInput.getText().toString().trim();
                java.math.BigDecimal amount;
                try {
                    amount = new java.math.BigDecimal(amountStr);
                } catch (Exception e) {
                    amountInput.setError("请输入正确的金额");
                    return;
                }
                if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    amountInput.setError("请输入正确的金额");
                    return;
                }
                if (amount.compareTo(new java.math.BigDecimal("200")) > 0) {
                    amountInput.setError("单个红包不能超过200元");
                    return;
                }
                if (amount.scale() > 2) {
                    amountInput.setError("金额最多保留两位小数");
                    return;
                }
                long amountFen = amount.movePointRight(2).longValueExact();
                String greeting = greetingInput.getText().toString().trim();
                if (greeting.isEmpty()) {
                    greeting = com.kora.imui.attachment.RedPacketAttachment.DEFAULT_GREETING;
                }
                com.kora.imui.attachment.RedPacketAttachment attachment = new com.kora.imui.attachment.RedPacketAttachment();
                attachment.setPacketId(java.util.UUID.randomUUID().toString());
                attachment.setAmountFen(amountFen);
                attachment.setGreeting(greeting);

                IMMessage message = MessageBuilder.INSTANCE.createRedPacketMessage(
                        getSessionId(),
                        getSessionType(),
                        getPeerId() != null && !getPeerId().isEmpty() ? getPeerId() : getSessionId(),
                        attachment
                );
                if (sendMessage(message)) {
                    dialog.dismiss();
                } else {
                    android.widget.Toast.makeText(context, "红包发送失败", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
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
                                30.5432, 104.0623,
                              "天府软件园",
                             "成都市武侯区天府软件园",
                             ""
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
