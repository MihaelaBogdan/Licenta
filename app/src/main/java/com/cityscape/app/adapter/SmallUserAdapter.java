package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.cityscape.app.R;
import com.cityscape.app.model.User;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SmallUserAdapter extends RecyclerView.Adapter<SmallUserAdapter.VH> {

    private List<User> users = new ArrayList<>();
    private Set<String> selectedUserIds = new HashSet<>();
    private OnSelectionChangedListener listener;

    public interface OnSelectionChangedListener {
        void onSelectionChanged(List<User> selectedUsers);
    }

    public SmallUserAdapter(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<User> newUsers) {
        this.users = newUsers;
        notifyDataSetChanged();
    }

    public List<User> getSelectedUsers() {
        List<User> selected = new ArrayList<>();
        for (User u : users) {
            if (selectedUserIds.contains(u.id)) selected.add(u);
        }
        return selected;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_user_small, p, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int p) {
        User u = users.get(p);
        h.name.setText(u.name != null ? u.name : "Explorer");
        
        if (u.avatar != null && !u.avatar.isEmpty()) {
            Glide.with(h.itemView.getContext()).load(u.avatar).circleCrop().into(h.avatar);
        } else {
            h.avatar.setImageResource(R.drawable.ic_profile);
        }

        h.overlay.setVisibility(selectedUserIds.contains(u.id) ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            if (selectedUserIds.contains(u.id)) {
                selectedUserIds.remove(u.id);
            } else {
                selectedUserIds.add(u.id);
            }
            notifyItemChanged(p);
            if (listener != null) listener.onSelectionChanged(getSelectedUsers());
        });
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView avatar, overlay;
        TextView name;
        VH(View v) {
            super(v);
            avatar = v.findViewById(R.id.avatar);
            overlay = v.findViewById(R.id.selection_overlay);
            name = v.findViewById(R.id.name);
        }
    }
}
