package com.cityscape.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.model.User;
import com.google.android.material.button.MaterialButton;
import de.hdodenhof.circleimageview.CircleImageView;
import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private List<User> users = new ArrayList<>();
    private OnUserActionListener listener;

    public interface OnUserActionListener {
        void onFollowClicked(User user, int position);
        void onUserClicked(User user);
    }

    public UserAdapter(OnUserActionListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<User> newUsers) {
        this.users = newUsers;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = users.get(position);
        Context context = holder.itemView.getContext();

        holder.userName.setText(user.name != null ? user.name : "Explorer");
        holder.userLevel.setText("Level " + user.level + " Explorer");

        if (user.avatar != null && !user.avatar.isEmpty()) {
            Glide.with(context).load(user.avatar).placeholder(R.drawable.ic_profile).circleCrop().into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile);
        }

        // Follow Button State
        if (user.isFollowing) {
            holder.btnFollow.setText("Urmărești");
            holder.btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getColor(R.color.app_surface)));
            holder.btnFollow.setTextColor(context.getColor(R.color.app_text_primary));
            holder.btnFollow.setStrokeWidth(1);
            holder.btnFollow.setStrokeColor(android.content.res.ColorStateList.valueOf(context.getColor(R.color.app_text_hint)));
        } else {
            holder.btnFollow.setText("Urmărește");
            holder.btnFollow.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getColor(R.color.primary)));
            holder.btnFollow.setTextColor(context.getColor(android.R.color.white));
            holder.btnFollow.setStrokeWidth(0);
        }

        holder.btnFollow.setOnClickListener(v -> {
            if (listener != null) listener.onFollowClicked(user, position);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onUserClicked(user);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        CircleImageView avatar;
        TextView userName, userLevel;
        MaterialButton btnFollow;

        UserViewHolder(@NonNull View v) {
            super(v);
            avatar = v.findViewById(R.id.user_avatar);
            userName = v.findViewById(R.id.user_name);
            userLevel = v.findViewById(R.id.user_level);
            btnFollow = v.findViewById(R.id.btn_follow);
        }
    }
}
