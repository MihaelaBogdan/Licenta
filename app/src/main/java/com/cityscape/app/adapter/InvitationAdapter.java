package com.cityscape.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.Invitation;
import java.util.List;

public class InvitationAdapter extends RecyclerView.Adapter<InvitationAdapter.InvitationViewHolder> {

    private Context context;
    private List<Invitation> invitations;
    private OnInvitationActionListener listener;

    public interface OnInvitationActionListener {
        void onAccept(Invitation invitation, int position);

        void onDecline(Invitation invitation, int position);
    }

    public InvitationAdapter(Context context, List<Invitation> invitations, OnInvitationActionListener listener) {
        this.context = context;
        this.invitations = invitations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public InvitationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_invitation, parent, false);
        return new InvitationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InvitationViewHolder holder, int position) {
        Invitation invitation = invitations.get(position);
        holder.bind(invitation, position);
    }

    @Override
    public int getItemCount() {
        return invitations.size();
    }

    class InvitationViewHolder extends RecyclerView.ViewHolder {
        TextView invitationFrom, invitationActivity, invitationTime;
        Button btnAccept;
        ImageView btnDecline;

        InvitationViewHolder(View itemView) {
            super(itemView);
            invitationFrom = itemView.findViewById(R.id.invitation_from);
            invitationActivity = itemView.findViewById(R.id.invitation_activity);
            invitationTime = itemView.findViewById(R.id.invitation_time);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
        }

        void bind(Invitation invitation, int position) {
            invitationFrom.setText(invitation.fromUserName + " invited you");
            invitationActivity.setText(invitation.activityName);
            invitationTime.setText(invitation.activityDate + " at " + invitation.activityTime);

            btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(invitation, position);
                }
            });

            btnDecline.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDecline(invitation, position);
                }
            });
        }
    }
}
