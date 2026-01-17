package com.cityscape.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.CalendarView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cityscape.app.CityScapeApp;
import com.cityscape.app.R;
import com.cityscape.app.adapters.PlannedActivityAdapter;
import com.cityscape.app.database.AppDatabase;
import com.cityscape.app.database.entities.PlannedActivity;
import com.cityscape.app.database.entities.Place;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Calendar Activity for planning and viewing activities
 */
public class CalendarActivity extends AppCompatActivity {

    private CalendarView calendarView;
    private RecyclerView activitiesRecycler;
    private TextView selectedDateText;
    private View noActivitiesText;
    private FloatingActionButton addActivityFab;

    private AppDatabase database;
    private String userId;
    private long selectedDate;
    private PlannedActivityAdapter adapter;
    private List<PlannedActivity> activities = new ArrayList<>();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault());
    private SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        database = AppDatabase.getDatabase(this);
        userId = CityScapeApp.getInstance().getCurrentUserId();

        initViews();
        setupCalendar();
        setupRecyclerView();

        // Load today's activities
        selectedDate = System.currentTimeMillis();
        loadActivitiesForDate(selectedDate);
    }

    private void initViews() {
        calendarView = findViewById(R.id.calendarView);
        activitiesRecycler = findViewById(R.id.activitiesRecycler);
        selectedDateText = findViewById(R.id.selectedDateText);
        noActivitiesText = findViewById(R.id.noActivitiesText);
        addActivityFab = findViewById(R.id.addActivityFab);

        // Back button
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        // Add activity button
        addActivityFab.setOnClickListener(v -> showAddActivityDialog());
    }

    private void setupCalendar() {
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth, 0, 0, 0);
            selectedDate = cal.getTimeInMillis();

            selectedDateText.setText(dateFormat.format(new Date(selectedDate)));
            loadActivitiesForDate(selectedDate);
        });

        // Set initial date text
        selectedDateText.setText(dateFormat.format(new Date(selectedDate)));
    }

    private void setupRecyclerView() {
        adapter = new PlannedActivityAdapter(activities, new PlannedActivityAdapter.OnActivityClickListener() {
            @Override
            public void onActivityClick(PlannedActivity activity) {
                // Open place details
                Toast.makeText(CalendarActivity.this,
                        "Opening: " + activity.getPlaceName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteClick(PlannedActivity activity) {
                deleteActivity(activity);
            }
        });

        activitiesRecycler.setLayoutManager(new LinearLayoutManager(this));
        activitiesRecycler.setAdapter(adapter);
    }

    private void loadActivitiesForDate(long date) {
        String dateStr = dbDateFormat.format(new Date(date));

        Executors.newSingleThreadExecutor().execute(() -> {
            List<PlannedActivity> result = database.plannedActivityDao()
                    .getActivitiesByUserAndDateSync(userId, dateStr);

            runOnUiThread(() -> {
                activities.clear();
                activities.addAll(result);
                adapter.notifyDataSetChanged();

                if (activities.isEmpty()) {
                    noActivitiesText.setVisibility(View.VISIBLE);
                    activitiesRecycler.setVisibility(View.GONE);
                } else {
                    noActivitiesText.setVisibility(View.GONE);
                    activitiesRecycler.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void showAddActivityDialog() {
        // Show choice dialog: Personal or Group activity
        String[] options = { "🙋 Activitate personală", "👥 Activitate de grup" };

        new AlertDialog.Builder(this, R.style.Theme_CityScape_Dialog)
                .setTitle("Alege tipul activității")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Personal activity
                        showPersonalActivityDialog();
                    } else {
                        // Group activity
                        showGroupActivityDialog();
                    }
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void showPersonalActivityDialog() {
        // Step 1: Choose place source
        String[] options = { "📍 Din Favorite", "🔍 Din aplicație", "✏️ Scrie manual" };

        new AlertDialog.Builder(this, R.style.Theme_CityScape_Dialog)
                .setTitle("Alege locul")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // From favorites
                        loadAndShowFavoritePlaces(false);
                    } else if (which == 1) {
                        // From app
                        loadAndShowAllPlaces(false);
                    } else {
                        // Manual entry
                        showManualPlaceEntry(false);
                    }
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void loadAndShowFavoritePlaces(boolean isGroupActivity) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Place> favorites = database.favoriteDao().getFavoritePlacesSync(userId);

            runOnUiThread(() -> {
                if (favorites == null || favorites.isEmpty()) {
                    Toast.makeText(this, "Nu ai locuri favorite. Adaugă din aplicație!", Toast.LENGTH_SHORT).show();
                    loadAndShowAllPlaces(isGroupActivity);
                } else {
                    showPlaceSelectionDialog(favorites, isGroupActivity);
                }
            });
        });
    }

    private void loadAndShowAllPlaces(boolean isGroupActivity) {
        String cityId = CityScapeApp.getInstance().getSelectedCityId();

        Executors.newSingleThreadExecutor().execute(() -> {
            List<Place> places = database.placeDao().getPlacesByCitySync(cityId);

            runOnUiThread(() -> {
                if (places == null || places.isEmpty()) {
                    Toast.makeText(this, "Nu există locuri în acest oraș.", Toast.LENGTH_SHORT).show();
                    showManualPlaceEntry(isGroupActivity);
                } else {
                    showPlaceSelectionDialog(places, isGroupActivity);
                }
            });
        });
    }

    private void showPlaceSelectionDialog(List<Place> places, boolean isGroupActivity) {
        String[] placeNames = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            placeNames[i] = p.getName() + " (" + p.getCategory() + ")";
        }

        new AlertDialog.Builder(this, R.style.Theme_CityScape_Dialog)
                .setTitle("Selectează locul")
                .setItems(placeNames, (dialog, which) -> {
                    Place selectedPlace = places.get(which);
                    showDatePickerForActivity(selectedPlace.getId(), selectedPlace.getName(), isGroupActivity);
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void showManualPlaceEntry(boolean isGroupActivity) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Numele locului");
        input.setPadding(50, 40, 50, 40);
        input.setTextColor(getResources().getColor(R.color.text_primary, null));
        input.setHintTextColor(getResources().getColor(R.color.text_hint, null));

        new AlertDialog.Builder(this, R.style.Theme_CityScape_Dialog)
                .setTitle("Introdu locul")
                .setView(input)
                .setPositiveButton("Continuă", (dialog, which) -> {
                    String placeName = input.getText().toString().trim();
                    if (placeName.isEmpty()) {
                        placeName = "Loc personalizat";
                    }
                    showDatePickerForActivity("custom_" + System.currentTimeMillis(), placeName, isGroupActivity);
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void showDatePickerForActivity(String placeId, String placeName, boolean isGroupActivity) {
        Calendar cal = Calendar.getInstance();

        DatePickerDialog datePicker = new DatePickerDialog(this, R.style.Theme_CityScape_Dialog,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year, month, dayOfMonth);
                    String dateStr = dbDateFormat.format(selectedCal.getTime());

                    // Now show time picker
                    showTimePickerForActivity(placeId, placeName, dateStr, isGroupActivity);
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));

        datePicker.setTitle("Selectează data");
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePicker.show();
    }

    private void showTimePickerForActivity(String placeId, String placeName, String dateStr, boolean isGroupActivity) {
        Calendar cal = Calendar.getInstance();

        TimePickerDialog timePicker = new TimePickerDialog(this, R.style.Theme_CityScape_Dialog,
                (view, hourOfDay, minute) -> {
                    String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    createActivityWithPlace(placeId, placeName, dateStr, time, isGroupActivity);
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true);

        timePicker.setTitle("Selectează ora");
        timePicker.show();
    }

    private void createActivityWithPlace(String placeId, String placeName, String dateStr, String time,
            boolean isGroupActivity) {
        PlannedActivity activity = new PlannedActivity(
                java.util.UUID.randomUUID().toString(),
                userId,
                placeId,
                dateStr);

        activity.setPlaceName(placeName);
        activity.setTime(time);
        activity.setNotes(isGroupActivity ? "👥 Activitate de grup" : "📍 " + placeName);
        activity.setReminderEnabled(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            database.plannedActivityDao().insert(activity);

            runOnUiThread(() -> {
                Toast.makeText(this, "✅ Activitate adăugată: " + placeName + " la " + time, Toast.LENGTH_SHORT).show();
                loadActivitiesForDate(selectedDate);
            });
        });
    }

    private void showGroupActivityDialog() {
        // Step 1: Choose place source for group activity
        String[] options = { "📍 Din Favorite", "🔍 Din aplicație", "✏️ Scrie manual" };

        new AlertDialog.Builder(this, R.style.Theme_CityScape_Dialog)
                .setTitle("Alege locul pentru grup")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        loadAndShowFavoritePlaces(true);
                    } else if (which == 1) {
                        loadAndShowAllPlaces(true);
                    } else {
                        showManualPlaceEntry(true);
                    }
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void showActivityNameDialog(String time, boolean isGroupActivity, String groupId) {
        // Create a simple input dialog for activity name
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Numele activității");
        input.setPadding(50, 40, 50, 40);
        input.setTextColor(getResources().getColor(R.color.text_primary, null));
        input.setHintTextColor(getResources().getColor(R.color.text_hint, null));

        new AlertDialog.Builder(this, R.style.Theme_CityScape_Dialog)
                .setTitle(isGroupActivity ? "Activitate de grup" : "Activitate personală")
                .setMessage("La ora " + time + "\nIntroduce numele activității:")
                .setView(input)
                .setPositiveButton("Adaugă", (dialog, which) -> {
                    String activityName = input.getText().toString().trim();
                    if (activityName.isEmpty()) {
                        activityName = isGroupActivity ? "Întâlnire de grup" : "Activitate nouă";
                    }
                    createActivity(time, activityName, isGroupActivity);
                })
                .setNegativeButton("Anulează", null)
                .show();
    }

    private void createActivity(String time, String activityName, boolean isGroupActivity) {
        PlannedActivity activity = new PlannedActivity(
                java.util.UUID.randomUUID().toString(),
                userId,
                "activity_" + System.currentTimeMillis(),
                dbDateFormat.format(new Date(selectedDate)));

        activity.setPlaceName(activityName);
        activity.setTime(time);
        activity.setNotes(isGroupActivity ? "👥 Activitate de grup" : "🙋 Activitate personală");
        activity.setReminderEnabled(true);

        Executors.newSingleThreadExecutor().execute(() -> {
            database.plannedActivityDao().insert(activity);

            runOnUiThread(() -> {
                Toast.makeText(this, "Activitate adăugată: " + activityName, Toast.LENGTH_SHORT).show();
                loadActivitiesForDate(selectedDate);
            });
        });
    }

    private void createQuickActivity(String time) {
        PlannedActivity activity = new PlannedActivity(
                java.util.UUID.randomUUID().toString(),
                userId,
                "quick_activity",
                dbDateFormat.format(new Date(selectedDate)));

        activity.setPlaceName("New Activity");
        activity.setTime(time);
        activity.setNotes("Tap to add details");
        activity.setReminderEnabled(false);

        Executors.newSingleThreadExecutor().execute(() -> {
            database.plannedActivityDao().insert(activity);

            runOnUiThread(() -> {
                Toast.makeText(this, "Activity added!", Toast.LENGTH_SHORT).show();
                loadActivitiesForDate(selectedDate);
            });
        });
    }

    private void deleteActivity(PlannedActivity activity) {
        Executors.newSingleThreadExecutor().execute(() -> {
            database.plannedActivityDao().delete(activity);

            runOnUiThread(() -> {
                Toast.makeText(this, "Activity deleted", Toast.LENGTH_SHORT).show();
                loadActivitiesForDate(selectedDate);
            });
        });
    }

    /**
     * Add activity for a specific place (called from PlaceDetailActivity)
     */
    public static void addPlaceToCalendar(AppCompatActivity context, String placeId, String placeName) {
        AppDatabase database = AppDatabase.getDatabase(context);
        String userId = CityScapeApp.getInstance().getCurrentUserId();

        // Show date picker
        Calendar cal = Calendar.getInstance();
        DatePickerDialog datePicker = new DatePickerDialog(context, R.style.Theme_CityScape_Dialog,
                (view, year, month, dayOfMonth) -> {
                    Calendar selectedCal = Calendar.getInstance();
                    selectedCal.set(year, month, dayOfMonth);
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(selectedCal.getTime());

                    // Show time picker
                    TimePickerDialog timePicker = new TimePickerDialog(context, R.style.Theme_CityScape_Dialog,
                            (timeView, hourOfDay, minute) -> {
                                String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);

                                // Create activity
                                PlannedActivity activity = new PlannedActivity(
                                        java.util.UUID.randomUUID().toString(),
                                        userId,
                                        placeId,
                                        dateStr);
                                activity.setPlaceName(placeName);
                                activity.setTime(time);
                                activity.setReminderEnabled(true);
                                activity.setReminderMinutesBefore(60);

                                Executors.newSingleThreadExecutor().execute(() -> {
                                    database.plannedActivityDao().insert(activity);
                                    context.runOnUiThread(() -> {
                                        Toast.makeText(context,
                                                "Added to calendar: " + placeName,
                                                Toast.LENGTH_SHORT).show();
                                    });
                                });
                            },
                            12, 0, true);
                    timePicker.setTitle("Select Time");
                    timePicker.show();
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH));

        datePicker.setTitle("Select Date");
        datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
        datePicker.show();
    }
}
