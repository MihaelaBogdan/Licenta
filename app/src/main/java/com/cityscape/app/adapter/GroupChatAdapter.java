package com.cityscape.app.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.GroupMessage;
import java.util.List;

public class GroupChatAdapter extends RecyclerView.Adapter<GroupChatAdapter.GroupChatViewHolder> {

    private final List<GroupMessage> messages;
    private final String currentUserId;

    public GroupChatAdapter(List<GroupMessage> messages, String currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public GroupChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_chat_message, parent, false);
        return new GroupChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupChatViewHolder holder, int position) {
        GroupMessage message = messages.get(position);
        boolean isCurrentUser = message.userId != null && message.userId.equals(currentUserId);

        holder.txtMessageBody.setText(message.message);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.layoutBubbleContainer.getLayoutParams();

        if (isCurrentUser) {
            
            params.gravity = Gravity.END;
            holder.txtSenderName.setVisibility(View.GONE);
            holder.txtMessageBody.setBackgroundResource(R.drawable.bg_message_user);
            holder.txtMessageBody.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.app_background));
        } else {
            
            params.gravity = Gravity.START;
            holder.txtSenderName.setVisibility(View.VISIBLE);
            holder.txtSenderName.setText(message.userName != null ? message.userName : "Prieten");
            holder.txtMessageBody.setBackgroundResource(R.drawable.bg_message_bot);
            holder.txtMessageBody.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.app_text_primary));
        }

        holder.layoutBubbleContainer.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class GroupChatViewHolder extends RecyclerView.ViewHolder {
        TextView txtSenderName;
        LinearLayout layoutBubbleContainer;
        TextView txtMessageBody;

        GroupChatViewHolder(View itemView) {
            super(itemView);
            txtSenderName = itemView.findViewById(R.id.txt_chat_sender_name);
            layoutBubbleContainer = itemView.findViewById(R.id.layout_bubble_container);
            txtMessageBody = itemView.findViewById(R.id.txt_chat_message_body);
        }
    }
}
