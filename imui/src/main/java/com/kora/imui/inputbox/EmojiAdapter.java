package com.kora.imui.inputbox;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kora.imui.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class EmojiAdapter extends RecyclerView.Adapter<EmojiAdapter.ViewHolder> {

    private Context context;
    private List<EmojiItem> emojiItems;
    private OnEmojiClickListener listener;

    public interface OnEmojiClickListener {
        void onEmojiClick(String emojiTag);
    }

    public void setOnEmojiClickListener(OnEmojiClickListener listener) {
        this.listener = listener;
    }

    public EmojiAdapter(Context context, List<EmojiItem> emojiItems) {
        this.context = context;
        this.emojiItems = emojiItems;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_emoji, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EmojiItem item = emojiItems.get(position);
        
        // 从 assets 加载图片
        AssetManager assetManager = context.getAssets();
        InputStream inputStream = null;
        try {
            // 构建图片在 assets 中的完整路径
            String filePath = "emoji/default/" + item.getFileName();
            inputStream = assetManager.open(filePath);
            Drawable drawable = Drawable.createFromStream(inputStream, null);
            holder.emojiImage.setImageDrawable(drawable);
        } catch (IOException e) {
            e.printStackTrace();
            // 可以设置一个加载失败的默认图
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEmojiClick(item.getTag());
            }
        });
    }

    @Override
    public int getItemCount() {
        return emojiItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView emojiImage;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            emojiImage = itemView.findViewById(R.id.iv_emoji_item);
        }
    }
}