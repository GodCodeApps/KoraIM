package com.kora.imui.inputbox;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kora.imui.R;

import java.util.List;

public class MoreOptionsAdapter extends RecyclerView.Adapter<MoreOptionsAdapter.ViewHolder> {

    private Context context;
    private List<MoreOptionItem> options;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MoreOptionItem item);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public MoreOptionsAdapter(Context context, List<MoreOptionItem> options) {
        this.context = context;
        this.options = options;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_more_option, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MoreOptionItem item = options.get(position);
        holder.icon.setImageResource(item.getIconResId());
        holder.name.setText(item.getName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return options.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView name;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_option_icon);
            name = itemView.findViewById(R.id.tv_option_name);
        }
    }
}