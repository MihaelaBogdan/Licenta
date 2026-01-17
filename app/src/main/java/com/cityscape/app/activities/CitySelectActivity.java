package com.cityscape.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.City;
import com.cityscape.app.databinding.ActivityCitySelectBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for selecting a city to explore
 */
public class CitySelectActivity extends AppCompatActivity {

    private ActivityCitySelectBinding binding;
    private AppDatabase database;
    private CityAdapter cityAdapter;
    private List<City> allCities = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCitySelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        database = CityScapeApp.getInstance().getDatabase();

        setupRecyclerView();
        setupListeners();
        loadCities();
    }

    private void setupRecyclerView() {
        binding.citiesRecycler.setLayoutManager(new LinearLayoutManager(this));
        cityAdapter = new CityAdapter(new ArrayList<>(), city -> {
            selectCity(city);
        });
        binding.citiesRecycler.setAdapter(cityAdapter);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCities(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void loadCities() {
        database.cityDao().getAllCities().observe(this, cities -> {
            if (cities != null) {
                allCities = cities;
                cityAdapter.updateData(cities);
            }
        });
    }

    private void filterCities(String query) {
        if (query.isEmpty()) {
            cityAdapter.updateData(allCities);
            return;
        }

        List<City> filtered = new ArrayList<>();
        for (City city : allCities) {
            if (city.getName().toLowerCase().contains(query.toLowerCase()) ||
                    city.getCountry().toLowerCase().contains(query.toLowerCase())) {
                filtered.add(city);
            }
        }
        cityAdapter.updateData(filtered);
    }

    private void selectCity(City city) {
        CityScapeApp.getInstance().setSelectedCity(city.getId());

        // If coming from main, just finish
        setResult(RESULT_OK);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    // Inner adapter class
    private static class CityAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<CityAdapter.ViewHolder> {

        private List<City> cities;
        private final OnCityClickListener listener;

        interface OnCityClickListener {
            void onCityClick(City city);
        }

        CityAdapter(List<City> cities, OnCityClickListener listener) {
            this.cities = cities;
            this.listener = listener;
        }

        void updateData(List<City> newCities) {
            this.cities = newCities;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(com.cityscape.app.R.layout.item_city, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.bind(cities.get(position));
        }

        @Override
        public int getItemCount() {
            return cities.size();
        }

        class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            private final android.widget.TextView cityName;
            private final android.widget.TextView countryName;
            private final android.widget.TextView placeCount;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                cityName = itemView.findViewById(com.cityscape.app.R.id.cityName);
                countryName = itemView.findViewById(com.cityscape.app.R.id.countryName);
                placeCount = itemView.findViewById(com.cityscape.app.R.id.placeCount);

                itemView.setOnClickListener(v -> {
                    if (getAdapterPosition() != -1 && listener != null) {
                        listener.onCityClick(cities.get(getAdapterPosition()));
                    }
                });
            }

            void bind(City city) {
                cityName.setText(city.getName());
                countryName.setText(city.getCountry());
                placeCount.setText(city.getPlaceCount() + " places");
            }
        }
    }
}
