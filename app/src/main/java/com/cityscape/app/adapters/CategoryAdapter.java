package com.cityscape.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cityscape.app.R;

import java.util.List;

/**
 * Adapter for category icons in horizontal scrolling
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.ViewHolder> {

    private List<Category> categories;
    private final OnCategoryClickListener listener;
    private int selectedPosition = -1;

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    public CategoryAdapter(List<Category> categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category, position == selectedPosition);
    }

    @Override
    public int getItemCount() {
        return categories != null ? categories.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View container;
        private final ImageView categoryIcon;
        private final TextView categoryName;

        ViewHolder(View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.categoryContainer);
            categoryIcon = itemView.findViewById(R.id.categoryIcon);
            categoryName = itemView.findViewById(R.id.categoryName);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    int previousSelected = selectedPosition;
                    selectedPosition = pos;
                    notifyItemChanged(previousSelected);
                    notifyItemChanged(selectedPosition);
                    listener.onCategoryClick(categories.get(pos).id);
                }
            });
        }

        void bind(Category category, boolean isSelected) {
            categoryIcon.setImageResource(category.iconRes);
            categoryName.setText(category.name);

            if (isSelected) {
                container.setBackgroundResource(R.drawable.bg_category_selected);
                categoryIcon.setColorFilter(itemView.getContext().getColor(R.color.primary_dark));
            } else {
                container.setBackgroundResource(R.drawable.bg_category_circle);
                categoryIcon.setColorFilter(itemView.getContext().getColor(R.color.accent_green));
            }
        }
    }

    /**
     * Category data class
     */
    public static class Category {
        public final String id;
        public final String name;
        public final int iconRes;

        public Category(String id, String name, int iconRes) {
            this.id = id;
            this.name = name;
            this.iconRes = iconRes;
        }
    }
}
