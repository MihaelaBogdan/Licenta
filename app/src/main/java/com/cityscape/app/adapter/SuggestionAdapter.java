package com.cityscape.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.model.GroupSuggestion;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.ViewHolder> {

    private List<GroupSuggestion> suggestions;
    private OnVoteClickListener listener;

    public interface OnVoteClickListener {
        void onVoteClick(GroupSuggestion suggestion);
    }

    public SuggestionAdapter(List<GroupSuggestion> suggestions, OnVoteClickListener listener) {
        this.suggestions = suggestions;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GroupSuggestion suggestion = suggestions.get(position);
        holder.placeName.setText(suggestion.placeName);
        holder.suggestedBy.setText("Propus de: " + suggestion.suggestedByUserName);
        holder.voteCount.setText(String.valueOf(suggestion.voteCount));

        holder.btnVote.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVoteClick(suggestion);
            }
        });
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView placeName, suggestedBy, voteCount;
        MaterialButton btnVote;

        ViewHolder(View view) {
            super(view);
            placeName = view.findViewById(R.id.text_place_name);
            suggestedBy = view.findViewById(R.id.text_suggested_by);
            voteCount = view.findViewById(R.id.text_vote_count);
            btnVote = view.findViewById(R.id.btn_vote);
            btnVote.setIconResource(R.drawable.ic_favorite);
        }
    }
}
