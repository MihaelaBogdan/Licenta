package com.cityscape.app.ui.calendar;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.cityscape.app.R;
import com.cityscape.app.adapter.ActivityAdapter;
import com.cityscape.app.adapter.GroupCardAdapter;
import com.cityscape.app.adapter.InvitationAdapter;
import com.cityscape.app.adapter.MemberScheduleAdapter;
import com.cityscape.app.data.AppDatabase;
import com.cityscape.app.data.SessionManager;
import com.cityscape.app.model.ActivityGroup;
import com.cityscape.app.model.GroupMember;
import com.cityscape.app.model.Invitation;
import com.cityscape.app.model.MemberSchedule;
import com.cityscape.app.model.PlannedActivity;
import com.cityscape.app.model.User;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.navigation.Navigation;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerActivities;
    private RecyclerView recyclerGroups;
    private LinearLayout emptyState;
    private LinearLayout emptyGroupsState;
    private TextView textSelectedDate;
    private TextView textGroupCount;
    private TextView textMonthYear;
    private TextView textActivitySummary;
    private ImageView btnAddActivity;
    private ImageView btnJoinGroup;
    private ImageView btnSyncCalendar;
    private ImageView btnExportCalendar;

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    syncGoogleCalendar();
                } else {
                    Toast.makeText(getContext(), "Permisiune refuzată pentru Calendar", Toast.LENGTH_SHORT).show();
                }
            });

    private AppDatabase db;
    private SessionManager sessionManager;
    private ActivityAdapter activityAdapter;
    private GroupCardAdapter groupCardAdapter;
    private com.cityscape.app.api.ApiService apiService;
    private List<com.cityscape.app.model.Place> allPlaces = new ArrayList<>();
    private long selectedDate;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        db = AppDatabase.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        apiService = com.cityscape.app.api.ApiClient.getClient().create(com.cityscape.app.api.ApiService.class);
        fetchPlaces();

        initViews(view);
        setupCalendar();

        // Select today by default or target date from args
        selectedDate = normalizeDate(System.currentTimeMillis());
        if (getArguments() != null && getArguments().containsKey("target_date")) {
            selectedDate = normalizeDate(getArguments().getLong("target_date"));
        }
        
        calendarView.setDate(selectedDate);
        textSelectedDate.setText(dateFormat.format(new Date(selectedDate)));

        if (getArguments() != null) {
            // Auto-trigger group creation if requested
            if (getArguments().getBoolean("auto_create_group", false)) {
                String activityId = getArguments().getString("target_activity_id");
                if (activityId != null) {
                    new Thread(() -> {
                        PlannedActivity activity = db.activityDao().getActivityById(activityId);
                        if (activity != null && getActivity() != null) {
                            getActivity().runOnUiThread(() -> showCreateGroupDialog(activity));
                        }
                    }).start();
                }
            }
        }
        
        loadActivitiesForDate(selectedDate);
        loadUserGroups();
        checkPendingInvitations();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllData();
    }

    private void refreshAllData() {
        if (sessionManager.getUserId() == null) return;
        
        loadActivitiesForDate(selectedDate);
        loadUserGroups();
        checkPendingInvitations();
        
        // Auto-sync external calendar if permission is already granted
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED) {
            syncGoogleCalendar();
        }
    }

    private RecyclerView recyclerHorizontalCalendar;
    private com.cityscape.app.adapter.CalendarDateAdapter horizontalAdapter;
    private List<com.cityscape.app.model.CalendarDate> dateItems = new ArrayList<>();
    private CardView cardMonthlyCalendar;
    private boolean isMonthlyCalendarExpanded = false;
    private android.content.Context appContext;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();
    }

    private void initViews(View view) {
        calendarView = view.findViewById(R.id.calendar_view);
        recyclerActivities = view.findViewById(R.id.recycler_activities);
        recyclerGroups = view.findViewById(R.id.recycler_groups);
        recyclerHorizontalCalendar = view.findViewById(R.id.recycler_horizontal_calendar);
        cardMonthlyCalendar = view.findViewById(R.id.card_monthly_calendar);
        emptyState = view.findViewById(R.id.empty_state);
        emptyGroupsState = view.findViewById(R.id.empty_groups_state);
        textSelectedDate = view.findViewById(R.id.text_selected_date);
        textGroupCount = view.findViewById(R.id.text_group_count);
        textMonthYear = view.findViewById(R.id.text_month_year);
        textActivitySummary = view.findViewById(R.id.text_activity_summary);
        btnAddActivity = view.findViewById(R.id.btn_add_activity);
        btnJoinGroup = view.findViewById(R.id.btn_join_group);
        btnSyncCalendar = view.findViewById(R.id.btn_sync_calendar);
        btnExportCalendar = view.findViewById(R.id.btn_export_calendar);

        // UI Collapsing Logic
        calendarView.setVisibility(View.GONE);
        view.findViewById(R.id.btn_expand_calendar).setOnClickListener(v -> {
            isMonthlyCalendarExpanded = !isMonthlyCalendarExpanded;
            calendarView.setVisibility(isMonthlyCalendarExpanded ? View.VISIBLE : View.GONE);
            v.animate().rotation(isMonthlyCalendarExpanded ? 180 : 0).setDuration(300).start();
        });

        setupHorizontalCalendar();

        com.google.android.material.button.MaterialButton btnReturnHome = view.findViewById(R.id.btn_return_home);
        if (getArguments() != null && getArguments().containsKey("target_date")) {
            btnReturnHome.setVisibility(View.VISIBLE);
            btnReturnHome.setOnClickListener(v -> {
                Navigation.findNavController(view).navigate(R.id.navigation_home);
            });
        }

        recyclerActivities.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerGroups.setLayoutManager(new LinearLayoutManager(getContext()));

        btnAddActivity.setOnClickListener(v -> showAddActivityDialog());
        // Map the new quick action banner buttons to existing logic
        view.findViewById(R.id.action_invitations).setOnClickListener(v -> showPendingInvitationsDialog());
        view.findViewById(R.id.action_groups).setOnClickListener(v -> {
            // Scroll to groups section
            View groupsLabel = view.findViewById(R.id.recycler_groups);
            groupsLabel.getParent().requestChildFocus(groupsLabel, groupsLabel);
        });
        view.findViewById(R.id.action_export).setOnClickListener(v -> exportDayToExternalCalendar());

        btnSyncCalendar.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED) {
                syncGoogleCalendar();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR);
            }
        });
    }

    private void setupHorizontalCalendar() {
        dateItems.clear();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7); // Start 1 week ago
        
        for (int i = 0; i < 45; i++) {
            long time = normalizeDate(cal.getTimeInMillis());
            boolean isSelected = (time == selectedDate);
            // Default to false for indicators, fill via thread below
            dateItems.add(new com.cityscape.app.model.CalendarDate(new Date(time), isSelected, false));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        horizontalAdapter = new com.cityscape.app.adapter.CalendarDateAdapter(dateItems, date -> {
            selectedDate = normalizeDate(date.date.getTime());
            updateDateSelectionUI();
            calendarView.setDate(selectedDate); // Sync with monthly grid
            loadActivitiesForDate(selectedDate);
        });

        recyclerHorizontalCalendar.setAdapter(horizontalAdapter);
        
        // Background update for dots/indicators
        new Thread(() -> {
            String userId = sessionManager.getUserId();
            if (userId == null) return;
            boolean changed = false;
            for (com.cityscape.app.model.CalendarDate item : dateItems) {
                boolean hasLocal = db.activityDao().hasActivitiesForDate(userId, normalizeDate(item.date.getTime()));
                if (hasLocal != item.hasEvents) {
                    item.hasEvents = hasLocal;
                    changed = true;
                }
            }
            if (changed && getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (isAdded()) horizontalAdapter.notifyDataSetChanged();
                });
            }
        }).start();
        
        scrollToSelectedDate();
    }

    private void updateDateSelectionUI() {
        for (com.cityscape.app.model.CalendarDate item : dateItems) {
            item.isSelected = normalizeDate(item.date.getTime()) == selectedDate;
        }
        horizontalAdapter.notifyDataSetChanged();
        scrollToSelectedDate();
    }

    private void scrollToSelectedDate() {
        for (int i = 0; i < dateItems.size(); i++) {
            if (dateItems.get(i).isSelected) {
                recyclerHorizontalCalendar.smoothScrollToPosition(i);
                break;
            }
        }
    }

    private void exportDayToExternalCalendar() {
        String userId = sessionManager.getUserId();
        if (userId == null) return;
        
        List<PlannedActivity> dayActivities = db.activityDao().getActivitiesForDate(userId, selectedDate);
        if (dayActivities.isEmpty()) {
            Toast.makeText(getContext(), "Nu ai activități planificate pentru această zi.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show a dialog to confirm exporting all or first one
        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Export în Calendar")
            .setMessage("Vrei să exporți toate cele " + dayActivities.size() + " activități în calendarul telefonului?")
            .setPositiveButton("Exportă Tot", (d, w) -> {
                for (PlannedActivity activity : dayActivities) {
                    exportActivityToPhone(activity);
                }
                Toast.makeText(getContext(), "Am deschis exportul pentru activitățile tale!", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Anulează", null)
            .show();
    }

    private void exportActivityToPhone(PlannedActivity activity) {
        try {
            // Parse time
            int hour = 9, minute = 0;
            if (activity.scheduledTime != null && activity.scheduledTime.contains(":")) {
                try {
                    String timeStr = activity.scheduledTime;
                    if (timeStr.contains("-")) timeStr = timeStr.split("-")[0].trim();
                    String[] parts = timeStr.split(":");
                    hour = Integer.parseInt(parts[0].trim());
                    minute = Integer.parseInt(parts[1].trim());
                } catch (Exception e) { hour = 9; }
            } else if (activity.scheduledTime != null) {
                String slot = activity.scheduledTime.toLowerCase();
                if (slot.contains("mic")) hour = 8;
                else if (slot.contains("prânz") || slot.contains("lunch")) hour = 13;
                else if (slot.contains("cină") || slot.contains("dinner")) hour = 20;
                else if (slot.contains("după")) hour = 16;
            }


            Calendar beginTime = Calendar.getInstance();
            beginTime.setTimeInMillis(activity.scheduledDate);
            beginTime.set(Calendar.HOUR_OF_DAY, hour);
            beginTime.set(Calendar.MINUTE, minute);

            Calendar endTime = (Calendar) beginTime.clone();
            endTime.add(Calendar.HOUR_OF_DAY, 2); // Default duration 2h

            Intent intent = new Intent(Intent.ACTION_INSERT)
                    .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime.getTimeInMillis())
                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, endTime.getTimeInMillis())
                    .putExtra(android.provider.CalendarContract.Events.TITLE, activity.placeName)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Planificat via Antigravity App. " + activity.notes)
                    .putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, activity.placeName)
                    .putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY);
            
            startActivity(intent);
        } catch (Exception e) {
            Log.e("CalendarFragment", "Export failed", e);
            Toast.makeText(getContext(), "Eroare la export: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupCalendar() {
        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month, dayOfMonth, 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            selectedDate = calendar.getTimeInMillis();
            loadActivitiesForDate(selectedDate);
        });
    }

    private void syncGoogleCalendar() {
        Toast.makeText(getContext(), "Citesc evenimentele din calendarul tău...", Toast.LENGTH_SHORT).show();
        
        android.content.Context context = getContext();
        if (context == null) return;
        new Thread(() -> {
            android.content.ContentResolver contentResolver = context.getContentResolver();
            
            java.util.Calendar startTime = java.util.Calendar.getInstance();
            java.util.Calendar endTime = java.util.Calendar.getInstance();
            endTime.add(java.util.Calendar.DAY_OF_YEAR, 30); // Urmatoarele 30 zile
            
            String selection = android.provider.CalendarContract.Events.DTSTART + " >= ? AND " + 
                               android.provider.CalendarContract.Events.DTSTART + " <= ?";
            String[] selectionArgs = new String[] {
                    String.valueOf(startTime.getTimeInMillis()),
                    String.valueOf(endTime.getTimeInMillis())
            };
            
            android.database.Cursor cursor = null;
            try {
                cursor = contentResolver.query(
                        android.provider.CalendarContract.Events.CONTENT_URI,
                        new String[]{
                                android.provider.CalendarContract.Events.TITLE,
                                android.provider.CalendarContract.Events.EVENT_LOCATION,
                                android.provider.CalendarContract.Events.DTSTART
                        },
                        selection, selectionArgs, 
                        android.provider.CalendarContract.Events.DTSTART + " ASC");

                if (cursor == null) return;
                
                int count = 0;
                String userId = sessionManager.getUserId();
                if (userId == null) return;

                while(cursor.moveToNext()) {
                    String title = cursor.getString(0);
                    String location = cursor.getString(1);
                    long dtstart = cursor.getLong(2);
                    
                    // Fallback to title if location is empty
                    String activityName = (location != null && !location.trim().isEmpty()) ? location : title;
                    if (activityName == null || activityName.isEmpty()) continue;

                    long normalized = normalizeDate(dtstart);
                    java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    String timeStr = timeFormat.format(new java.util.Date(dtstart));
                    
                    PlannedActivity activity = new PlannedActivity(
                            userId,
                            null,
                            "📅 " + activityName,
                            "System Calendar",
                            "",
                            normalized,
                            timeStr
                    );
                    activity.notes = title != null ? title : "";
                    
                    List<PlannedActivity> existing = db.activityDao().getActivitiesForDate(userId, normalized);
                    boolean exists = false;
                    for (PlannedActivity e : existing) {
                        if (e.placeName.equals(activity.placeName) && e.scheduledTime.equals(activity.scheduledTime)) {
                            exists = true; 
                            break;
                        }
                    }
                    
                    if (!exists) {
                        db.activityDao().insert(activity);
                        com.cityscape.app.data.SupabaseSyncManager.getInstance(context).pushActivityToCloud(activity);
                        count++;
                    }
                }
                
                final int finalCount = count;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        loadActivitiesForDate(selectedDate);
                        if (finalCount > 0) {
                            Toast.makeText(getContext(), "🎉 Magie! Am importat " + finalCount + " activități noi!", Toast.LENGTH_LONG).show();
                            sessionManager.awardAchievement("Sincronizare Calendar", 50);
                        } else {
                            Toast.makeText(getContext(), "Nu am găsit activități noi în calendar pentru următoarele 30 de zile.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("CalendarFragment", "Error syncing calendar", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null)
                             Toast.makeText(getContext(), "Eroare la sincronizare. Verifică permisiunile.", Toast.LENGTH_SHORT).show();
                    });
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }).start();
    }

    private long normalizeDate(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void loadActivitiesForDate(long date) {
        String formattedDate = dateFormat.format(new Date(date));

        if (normalizeDate(System.currentTimeMillis()) == date) {
            textSelectedDate.setText("Activitățile de azi");
        } else {
            textSelectedDate.setText(formattedDate);
        }

        if (textMonthYear != null) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(date);
            String[] luni = {"Ianuarie", "Februarie", "Martie", "Aprilie", "Mai", "Iunie", "Iulie", "August", "Septembrie", "Octombrie", "Noiembrie", "Decembrie"};
            String lunaAn = luni[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
            textMonthYear.setText(lunaAn);
        }

        android.content.Context context = getContext();
        if (context == null) return;
        new Thread(() -> {
            String userId = sessionManager.getUserId();
            if (userId == null) return;
            
            List<PlannedActivity> activities = db.activityDao().getActivitiesForDate(userId, date);
            
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    if (textActivitySummary != null) {
                        int count = activities.size();
                        String summaryText;
                        if (count == 0) {
                            summaryText = "Nu ai nicio activitate planificată";
                        } else if (count == 1) {
                            summaryText = "Ai o activitate planificată";
                        } else {
                            summaryText = "Ai " + count + " activități planificate";
                        }
                        textActivitySummary.setText(summaryText);
                    }
                    // Animation for "Pro" feel
                    recyclerActivities.setAlpha(0f);
                    recyclerActivities.animate().alpha(1f).setDuration(400).start();

                    if (activities.isEmpty()) {
                        recyclerActivities.setVisibility(View.GONE);
                        emptyState.setVisibility(View.VISIBLE);
                    } else {
                        recyclerActivities.setVisibility(View.VISIBLE);
                        emptyState.setVisibility(View.GONE);

                        activityAdapter = new ActivityAdapter(context, activities,
                                new ActivityAdapter.OnActivityActionListener() {
                        @Override
                        public void onCompleteClick(PlannedActivity activity, int position) {
                            activity.isCompleted = true;
                            db.activityDao().update(activity);
                            com.cityscape.app.data.SupabaseSyncManager.getInstance(context)
                                    .updateActivityInCloud(activity);
                            
                            // Record visit in Cloud
                            User user = sessionManager.getCurrentUser();
                            if (user != null) {
                                com.cityscape.app.api.VisitRequest req = new com.cityscape.app.api.VisitRequest(
                                    user.id,
                                    null,
                                    activity.placeId != null ? String.valueOf(activity.placeId) : null,
                                    activity.placeName,
                                    activity.placeType
                                );
                                apiService.recordVisit(req).enqueue(new retrofit2.Callback<Void>() {
                                    @Override public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {}
                                    @Override public void onFailure(retrofit2.Call<Void> call, Throwable t) {}
                                });
                            }

                            sessionManager.recordPlaceVisit(activity.placeName);
                            if (getContext() != null) Toast.makeText(getContext(),
                                    activity.placeName + " completat (+50 XP)",
                                    Toast.LENGTH_SHORT).show();
                            
                            showFeedbackDialog(activity.placeName);
                            loadActivitiesForDate(selectedDate);
                        }

                        @Override
                        public void onShareClick(PlannedActivity activity) {
                            shareActivity(activity);
                        }

                        @Override
                        public void onCreateGroupClick(PlannedActivity activity) {
                            ActivityGroup existingGroup = db.groupDao().getGroupForActivity(activity.id);
                            if (existingGroup != null) {
                                showGroupDetails(existingGroup, activity);
                            } else {
                                // Offer choice: full group or 1-on-1 availability check
                                new AlertDialog.Builder(context, R.style.DarkDialogTheme)
                                    .setTitle("Cum vrei să inviți?")
                                    .setMessage("Creează un grup sau invită un singur prieten după ce verifici disponibilitatea.")
                                    .setPositiveButton("🗓️ Verifică disponibilitate", (d, w) ->
                                            showAvailabilityPickerDialog(activity))
                                    .setNegativeButton("👥 Creează grup", (d, w) ->
                                            showCreateGroupDialog(activity))
                                    .setNeutralButton("Anulează", null)
                                    .show();
                            }
                        }

                        @Override
                        public void onExportClick(PlannedActivity activity) {
                            exportActivityToPhone(activity);
                        }

                        @Override
                        public void onEditClick(PlannedActivity activity) {
                            showEditActivityDialog(activity);
                        }
                        });
                        recyclerActivities.setAdapter(activityAdapter);
                    }
                });
            }
        }).start();
    }


    private void showFeedbackDialog(String placeName) {
        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
            .setTitle("Feedback pentru " + placeName)
            .setMessage("Cum a fost activitatea? Feedback-ul tău ne ajută să-ți oferim planuri mai bune!")
            .setPositiveButton("Minunat! ⭐", (d, w) -> Toast.makeText(getContext(), "Mulțumim!", Toast.LENGTH_SHORT).show())
            .setNegativeButton("Nu prea", (d, w) -> Toast.makeText(getContext(), "Vom ține cont!", Toast.LENGTH_SHORT).show())
            .show();
    }

    private void loadUserGroups() {
        android.content.Context context = getContext();
        if (context == null) return;
        
        new Thread(() -> {
            String userId = sessionManager.getUserId();
            if (userId == null) return;
            List<ActivityGroup> groups = db.groupDao().getGroupsForUser(userId);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (groups.isEmpty()) {
                        recyclerGroups.setVisibility(View.GONE);
                        emptyGroupsState.setVisibility(View.VISIBLE);
                        textGroupCount.setText("");
                    } else {
                        recyclerGroups.setVisibility(View.VISIBLE);
                        emptyGroupsState.setVisibility(View.GONE);
                        textGroupCount.setText(groups.size() + (groups.size() == 1 ? " grup" : " grupuri"));

                        groupCardAdapter = new GroupCardAdapter(context, groups,
                                new GroupCardAdapter.OnGroupActionListener() {
                                    @Override
                                    public void onViewSchedule(ActivityGroup group) {
                                        showGroupScheduleDialog(group);
                                    }

                                    @Override
                                    public void onShareWhatsApp(ActivityGroup group) {
                                        shareOnWhatsApp(group);
                                    }

                                    @Override
                                    public void onGroupClick(ActivityGroup group) {
                                        // Find the activity for this group
                                        new Thread(() -> {
                                            PlannedActivity activity = findActivityForGroup(group);
                                            if (activity != null && getActivity() != null) {
                                                getActivity().runOnUiThread(() -> {
                                                    if (isAdded()) showGroupDetails(group, activity);
                                                });
                                            }
                                        }).start();
                                    }
                                });
                        recyclerGroups.setAdapter(groupCardAdapter);
                    }
                });
            }
        }).start();
    }

    private PlannedActivity findActivityForGroup(ActivityGroup group) {
        if (group.activityId != null && !group.activityId.isEmpty()) {
            List<PlannedActivity> allActivities = db.activityDao()
                    .getActivitiesForUser(group.creatorId);
            for (PlannedActivity a : allActivities) {
                if (a.id != null && a.id.equals(group.activityId)) {
                    return a;
                }
            }
        }
        return null;
    }

    // ==================== SHARE METHODS ====================

    private void shareActivity(PlannedActivity activity) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(activity.scheduledDate));

        String shareText = "🌟 Hai la " + activity.placeName + "!\n" +
                "📅 Data: " + dateStr + ", ora " + activity.scheduledTime + "\n\n" +
                "📍 Vezi detaliile și alătură-te: https://cityscape.app/join\n";

        ActivityGroup group = db.groupDao().getGroupForActivity(activity.id);
        if (group != null) {
            shareText += "🔑 Cod grup: " + group.groupCode + "\n";
            shareText += "👉 Descarcă CityScape, mergi la Calendar și introdu codul!";
        }

        showShareChooser(shareText, "Hai la " + activity.placeName);
    }

    private void shareOnWhatsApp(ActivityGroup group) {
        PlannedActivity activity = findActivityForGroup(group);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = activity != null ? sdf.format(new Date(activity.scheduledDate)) : "";

        String shareText = "🚀 Te invit în grupul " + group.groupName + " pe CityScape!\n";
        if (activity != null) {
            shareText += "📍 Locație: " + activity.placeName + "\n" +
                    "⏰ Când: " + dateStr + ", ora " + activity.scheduledTime + "\n";
        }

        shareText += "\n🔑 Cod de intrare: " + group.groupCode + "\n" +
                "🌐 Descarcă aplicația aici: https://cityscape.app/join\n\n" +
                "După instalare, mergi la Calendar -> Adaugă (+) și introdu codul!";

        try {
            Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
            whatsappIntent.setType("text/plain");
            whatsappIntent.setPackage("com.whatsapp");
            whatsappIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(whatsappIntent);
        } catch (Exception e) {
            showShareChooser(shareText, "Intră în " + group.groupName);
        }
    }

    private void showShareChooser(String text, String subject) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Partajează prin"));
    }

    private void shareGroupLink(ActivityGroup group, PlannedActivity activity) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(activity.scheduledDate));

        String shareText = "Hai în grupul " + group.groupName + "!\n" +
                activity.placeName + "\n" +
                dateStr + ", ora " + activity.scheduledTime + "\n\n" +
                "Cod de intrare: " + group.groupCode;

        showShareChooser(shareText, "Intră în " + group.groupName);
    }

    // ==================== CREATE GROUP ====================

    private void showCreateGroupDialog(PlannedActivity activity) {
        if (!isAdded() || getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_group, null);

        EditText inputGroupName = dialogView.findViewById(R.id.input_group_name);
        EditText inputSearchEmail = dialogView.findViewById(R.id.input_search_email);
        TextView btnShareLink = dialogView.findViewById(R.id.btn_share_link);
        
        // Recommended Friends section
        RecyclerView rvRecommended = dialogView.findViewById(R.id.rv_recommended_friends);
        View labelRecommended = dialogView.findViewById(R.id.text_recommended_label);
        com.cityscape.app.adapter.SmallUserAdapter recommendedAdapter = new com.cityscape.app.adapter.SmallUserAdapter(null);
        if (rvRecommended != null) {
            rvRecommended.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            rvRecommended.setAdapter(recommendedAdapter);
            apiService.getRecommendedUsers(sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<User>>() {
                @Override
                public void onResponse(retrofit2.Call<List<User>> call, retrofit2.Response<List<User>> response) {
                    if (isAdded() && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        labelRecommended.setVisibility(View.VISIBLE);
                        rvRecommended.setVisibility(View.VISIBLE);
                        recommendedAdapter.setUsers(response.body());
                    } else {
                        labelRecommended.setVisibility(View.GONE);
                        rvRecommended.setVisibility(View.GONE);
                    }
                }
                @Override public void onFailure(retrofit2.Call<List<User>> call, Throwable t) {}
            });
        }

        // Following list
        RecyclerView rvFollowing = dialogView.findViewById(R.id.rv_following_to_invite);
        View labelFollowing = dialogView.findViewById(R.id.text_following_label);
        com.cityscape.app.adapter.SmallUserAdapter followingAdapter = new com.cityscape.app.adapter.SmallUserAdapter(null);
        if (rvFollowing != null) {
            rvFollowing.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            rvFollowing.setAdapter(followingAdapter);
            apiService.getFollowing(sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<User>>() {
                @Override
                public void onResponse(retrofit2.Call<List<User>> call, retrofit2.Response<List<User>> response) {
                    if (isAdded() && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        labelFollowing.setVisibility(View.VISIBLE);
                        rvFollowing.setVisibility(View.VISIBLE);
                        followingAdapter.setUsers(response.body());
                    }
                }
                @Override public void onFailure(retrofit2.Call<List<User>> call, Throwable t) {}
            });
        }

        inputGroupName.setText("Aventură la " + activity.placeName);

        AlertDialog dialog = builder.setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        dialogView.findViewById(R.id.btn_create_group_action).setOnClickListener(v -> {
            String groupName = inputGroupName.getText().toString().trim();
            if (groupName.isEmpty()) {
                Toast.makeText(getContext(), "Introdu un nume de grup", Toast.LENGTH_SHORT).show();
                return;
            }

            User currentUser = sessionManager.getCurrentUser();
            if (currentUser == null) return;

            ActivityGroup group = new ActivityGroup(activity.id, currentUser.id, groupName);
            db.groupDao().insertGroup(group);
            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushGroupToCloud(group);

            GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
            db.groupDao().insertMember(creatorMember);
            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(creatorMember);

            // Invite selected users
            List<User> selectedUsers = new ArrayList<>();
            selectedUsers.addAll(followingAdapter.getSelectedUsers());
            selectedUsers.addAll(recommendedAdapter.getSelectedUsers());
            for (User u : selectedUsers) {
                sendInvitation(group, activity, u);
            }

            // Also check email/username if typed
            String query = inputSearchEmail.getText().toString().trim();
            if (!query.isEmpty()) {
                android.content.Context context = getContext();
                if (context != null) {
                    new Thread(() -> {
                        // Search locally first, then via API
                        User targetUser = db.userDao().getUserByEmail(query);
                        if (targetUser == null) targetUser = db.userDao().getUserByUsername(query);
                        
                        if (targetUser != null && getActivity() != null) {
                            User finalTarget = targetUser;
                            getActivity().runOnUiThread(() -> {
                                if (isAdded()) sendInvitation(group, activity, finalTarget);
                            });
                        } else {
                            // Fallback to API search
                            apiService.searchUsers(query, sessionManager.getUserId()).enqueue(new retrofit2.Callback<List<User>>() {
                                @Override
                                public void onResponse(retrofit2.Call<List<User>> call, retrofit2.Response<List<User>> response) {
                                    if (isAdded() && response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                        sendInvitation(group, activity, response.body().get(0));
                                    }
                                }
                                @Override public void onFailure(retrofit2.Call<List<User>> call, Throwable t) {}
                            });
                        }
                    }).start();
                }
            }

            Toast.makeText(getContext(), "Grup creat! Cod: " + group.groupCode, Toast.LENGTH_LONG).show();
            dialog.dismiss();
            loadActivitiesForDate(selectedDate);
            loadUserGroups();
        });

        btnShareLink.setOnClickListener(v -> {
            String groupName = inputGroupName.getText().toString().trim();
            if (groupName.isEmpty()) groupName = activity.placeName + " - Grup";
            ActivityGroup group = new ActivityGroup(activity.id, sessionManager.getUserId(), groupName);
            db.groupDao().insertGroup(group);
            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushGroupToCloud(group);
            User currentUser = sessionManager.getCurrentUser();
            GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
            db.groupDao().insertMember(creatorMember);
            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(creatorMember);
            dialog.dismiss();
            loadUserGroups();
            shareOnWhatsApp(group);
        });

        dialog.show();
    }

    // ==================== GROUP DETAILS ====================

    private void showGroupDetails(ActivityGroup group, PlannedActivity activity) {
        if (!isAdded()) return;

        List<GroupMember> members = db.groupDao().getMembersForGroup(group.id);

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_group_details, null);
        TextView txtGroupName = dialogView.findViewById(R.id.txt_detail_group_name);
        TextView txtGroupCode = dialogView.findViewById(R.id.txt_detail_group_code);
        TextView lblMembersCount = dialogView.findViewById(R.id.lbl_members_count);
        TextView txtMembersList = dialogView.findViewById(R.id.txt_members_list);
        Button btnChat = dialogView.findViewById(R.id.btn_group_chat);
        Button btnVote = dialogView.findViewById(R.id.btn_group_vote);
        Button btnWhatsapp = dialogView.findViewById(R.id.btn_group_whatsapp);
        Button btnClose = dialogView.findViewById(R.id.btn_close_group_details);
        ImageView btnCopy = dialogView.findViewById(R.id.btn_copy_code);

        if (txtGroupName != null) txtGroupName.setText(group.groupName);
        if (txtGroupCode != null) txtGroupCode.setText(group.groupCode);
        if (lblMembersCount != null) lblMembersCount.setText("Membri (" + members.size() + "):");

        StringBuilder memberList = new StringBuilder();
        for (GroupMember member : members) {
            memberList.append("• ").append(member.userName);
            if (member.isCreator)
                memberList.append(" (Creator)");
            memberList.append(" — ").append(member.status).append("\n");
        }
        if (txtMembersList != null) txtMembersList.setText(memberList.toString());

        AlertDialog detailsDialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .create();

        if (detailsDialog.getWindow() != null) {
            detailsDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }

        if (btnChat != null) {
            btnChat.setOnClickListener(v -> {
                detailsDialog.dismiss();
                showGroupChatDialog(group);
            });
        }

        if (btnVote != null) {
            btnVote.setOnClickListener(v -> {
                detailsDialog.dismiss();
                showVotingDialog(group);
            });
        }

        if (btnWhatsapp != null) {
            btnWhatsapp.setOnClickListener(v -> {
                shareOnWhatsApp(group);
            });
        }

        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("CityScape Group Code", group.groupCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Cod copiat în clipboard! 📋", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> detailsDialog.dismiss());
        }

        detailsDialog.show();
    }

    private void showGroupChatDialog(ActivityGroup group) {
        if (!isAdded()) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_group_chat, null);
        TextView txtTitle = dialogView.findViewById(R.id.txt_chat_group_title);
        RecyclerView rvChat = dialogView.findViewById(R.id.rv_chat_messages);
        EditText inputChat = dialogView.findViewById(R.id.input_chat_message);
        ImageView btnSend = dialogView.findViewById(R.id.btn_send_chat);
        ImageView btnClose = dialogView.findViewById(R.id.btn_close_chat);

        if (txtTitle != null) {
            txtTitle.setText("💬 Chat: " + group.groupName);
        }

        String currentUserId = sessionManager.getUserId();
        User currentUser = sessionManager.getCurrentUser();
        String currentUserName = currentUser != null ? currentUser.name : "Eu";

        // Load historical messages from DB
        List<com.cityscape.app.model.GroupMessage> chatMessages = new ArrayList<>(db.groupMessageDao().getMessagesForGroup(group.id));

        // Loaded clean historical messages with no predefined seeds

        com.cityscape.app.adapter.GroupChatAdapter chatAdapter = new com.cityscape.app.adapter.GroupChatAdapter(chatMessages, currentUserId);
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(chatAdapter);
        rvChat.scrollToPosition(chatMessages.size() - 1);

        AlertDialog chatDialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .create();

        if (chatDialog.getWindow() != null) {
            chatDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> chatDialog.dismiss());
        }

        btnSend.setOnClickListener(v -> {
            String rawMsg = inputChat.getText().toString().trim();
            if (rawMsg.isEmpty()) return;

            inputChat.setText("");

            // Insert user message
            com.cityscape.app.model.GroupMessage userMsg = new com.cityscape.app.model.GroupMessage(group.id, currentUserId, currentUserName, rawMsg);
            db.groupMessageDao().insert(userMsg);
            chatMessages.add(userMsg);
            chatAdapter.notifyItemInserted(chatMessages.size() - 1);
            rvChat.scrollToPosition(chatMessages.size() - 1);

            // Award small social XP
            com.cityscape.app.util.BadgeManager.addExperience(requireContext(), currentUserId, 10);

            // Delayed reply simulation exclusively from other group members (1.5s)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (!isAdded()) return;

                // Load active group members
                List<com.cityscape.app.model.GroupMember> membersList = db.groupDao().getMembersForGroup(group.id);
                List<com.cityscape.app.model.GroupMember> otherMembers = new ArrayList<>();
                for (com.cityscape.app.model.GroupMember m : membersList) {
                    if (m.userId != null && !m.userId.equals(currentUserId)) {
                        otherMembers.add(m);
                    }
                }

                // If other members exist in this group, simulate a reply from one of them
                if (!otherMembers.isEmpty()) {
                    com.cityscape.app.model.GroupMember replier = otherMembers.get((int) (Math.random() * otherMembers.size()));
                    String sender = replier.userName;
                    String senderId = replier.userId;

                    String[] replies = {
                        "Sună excelent planul ăsta! 🎯",
                        "Eu ajung exact la fix! Ne vedem acolo.",
                        "Super tare! Să nu uităm să facem poze! 📸",
                        "Să ne strângem în fața intrării principale.",
                        "Eu vin sigur! Sună extraordinar! 🌟",
                        "De acord! Vreți să ne oprim și la o cafea înainte? ☕"
                    };
                    String replyText = replies[(int) (Math.random() * replies.length)];

                    com.cityscape.app.model.GroupMessage replyMsg = new com.cityscape.app.model.GroupMessage(group.id, "simulated_" + senderId + "_" + System.currentTimeMillis(), sender, replyText);
                    db.groupMessageDao().insert(replyMsg);
                    chatMessages.add(replyMsg);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                    rvChat.scrollToPosition(chatMessages.size() - 1);

                    com.cityscape.app.util.BadgeManager.addExperience(requireContext(), currentUserId, 15);
                }
            }, 1500);
        });

        chatDialog.show();
    }


    private void showVotingDialog(ActivityGroup group) {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_group_voting, null);
        RecyclerView recycler = dialogView.findViewById(R.id.recycler_suggestions);
        EditText input = dialogView.findViewById(R.id.input_suggestion);
        Button btnAdd = dialogView.findViewById(R.id.btn_add_suggestion);
        TextView emptyText = dialogView.findViewById(R.id.text_no_suggestions);
        Button btnFinalize = dialogView.findViewById(R.id.btn_finalize_vote);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));

        loadSuggestions(group, recycler, emptyText);

        // Only creator can finalize
        if (group.creatorId.equals(sessionManager.getUserId())) {
            btnFinalize.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setView(dialogView)
                .setPositiveButton("Gata", null)
                .show();

        btnAdd.setOnClickListener(v -> {
            String placeName = input.getText().toString().trim();
            if (placeName.isEmpty())
                return;

            User user = sessionManager.getCurrentUser();
            com.cityscape.app.model.GroupSuggestion suggestion = new com.cityscape.app.model.GroupSuggestion(
                    group.id, null, placeName, user.id, user.name);

            db.suggestionDao().insert(suggestion);
            db.voteDao().insert(new com.cityscape.app.model.Vote(suggestion.id, user.id, group.id));

            input.setText("");
            loadSuggestions(group, recycler, emptyText);
        });

        btnFinalize.setOnClickListener(v -> {
            List<com.cityscape.app.model.GroupSuggestion> suggestions = db.suggestionDao()
                    .getSuggestionsForGroup(group.id);
            if (suggestions.isEmpty()) {
                Toast.makeText(getContext(), "Nu există propuneri!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Find winner (first in sorted list)
            com.cityscape.app.model.GroupSuggestion winner = suggestions.get(0);

            // Update the linked activity
            com.cityscape.app.model.PlannedActivity activity = findActivityForGroup(group);
            if (activity != null) {
                activity.placeName = "🏆 " + winner.placeName;
                db.activityDao().update(activity);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                        .updateActivityInCloud(activity);

                Toast.makeText(getContext(), "Vot finalizat! Mergem la: " + winner.placeName, Toast.LENGTH_LONG).show();
                dialog.dismiss();
                loadActivitiesForDate(selectedDate);
            }
        });
    }

    private void loadSuggestions(ActivityGroup group, RecyclerView recycler, TextView emptyText) {
        List<com.cityscape.app.model.GroupSuggestion> suggestions = db.suggestionDao()
                .getSuggestionsForGroup(group.id);

        if (suggestions.isEmpty()) {
            recycler.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);

            com.cityscape.app.adapter.SuggestionAdapter adapter = new com.cityscape.app.adapter.SuggestionAdapter(
                    suggestions, suggestion -> {
                        // Vote logic
                        String userId = sessionManager.getUserId();
                        List<com.cityscape.app.model.Vote> userVotes = db.voteDao().getUserVotesInGroup(userId,
                                group.id);

                        boolean alreadyVoted = false;
                        for (com.cityscape.app.model.Vote v : userVotes) {
                            if (v.suggestionId.equals(suggestion.id)) {
                                alreadyVoted = true;
                                break;
                            }
                        }

                        if (!alreadyVoted) {
                            db.voteDao().insert(new com.cityscape.app.model.Vote(suggestion.id, userId, group.id));
                            suggestion.voteCount = db.voteDao().getVoteCountForSuggestion(suggestion.id);
                            db.suggestionDao().update(suggestion);
                            loadSuggestions(group, recycler, emptyText);
                        } else {
                            Toast.makeText(getContext(), "Ai votat deja!", Toast.LENGTH_SHORT).show();
                        }
                    });
            recycler.setAdapter(adapter);
        }
    }

    // ==================== INVITATIONS ====================

    private void sendInvitation(ActivityGroup group, PlannedActivity activity, User targetUser) {
        // Check if already a member
        GroupMember existingMember = db.groupDao().getMember(group.id, targetUser.id);
        if (existingMember != null) {
            Toast.makeText(getContext(), targetUser.name + " este deja în grup!", Toast.LENGTH_SHORT).show();
            return;
        }

        User currentUser = sessionManager.getCurrentUser();
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());

        Invitation invitation = new Invitation(
                currentUser.id,
                currentUser.name,
                targetUser.id,
                group.id,
                group.groupName,
                activity.placeName,
                sdf.format(new Date(activity.scheduledDate)),
                activity.scheduledTime);
        db.invitationDao().insert(invitation);
        com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushInvitationToCloud(invitation);

        Toast.makeText(getContext(), "Invitație trimisă lui " + targetUser.name + "!", Toast.LENGTH_SHORT).show();
    }

    private void checkPendingInvitations() {
        int pendingCount = db.invitationDao().getPendingCount(sessionManager.getUserId());
        // For the new design, we might want a badge on action_invitations
        // But for now, we'll just handle the dialog trigger via action_invitations click
    }

    private void showPendingInvitationsDialog() {
        List<Invitation> pending = db.invitationDao().getPendingInvitations(sessionManager.getUserId());

        if (pending.isEmpty()) {
            Toast.makeText(getContext(), "Nu ai invitații în așteptare.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_invitations, null);
        RecyclerView recyclerInvitations = dialogView.findViewById(R.id.recycler_invitations);
        recyclerInvitations.setLayoutManager(new LinearLayoutManager(getContext()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Invitații (" + pending.size() + ")")
                .setView(dialogView)
                .setNegativeButton("Închide", null)
                .create();

        InvitationAdapter invAdapter = new InvitationAdapter(requireContext(), pending,
                new InvitationAdapter.OnInvitationActionListener() {
                    @Override
                    public void onAccept(Invitation invitation, int position) {
                        invitation.status = "accepted";
                        db.invitationDao().update(invitation);
                        com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                                .updateInvitationInCloud(invitation);

                        User currentUser = sessionManager.getCurrentUser();
                        GroupMember member = new GroupMember(invitation.groupId, currentUser.id, currentUser.name,
                                false);
                        member.status = "accepted";
                        db.groupDao().insertMember(member);
                        com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                                .pushMemberToCloud(member);

                        Toast.makeText(getContext(), "Te-ai alăturat grupului " + invitation.groupName + "!",
                                Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        sessionManager.awardAchievement("S-a alăturat grupului: " + invitation.groupName, 25);
                        loadUserGroups();
                        checkPendingInvitations();
                    }

                    @Override
                    public void onDecline(Invitation invitation, int position) {
                        invitation.status = "declined";
                        db.invitationDao().update(invitation);
                        com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                                .updateInvitationInCloud(invitation);
                        Toast.makeText(getContext(), "Invitația a fost refuzată", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        checkPendingInvitations();
                    }
                });
        recyclerInvitations.setAdapter(invAdapter);

        dialog.show();
    }

    // ==================== JOIN GROUP ====================

    private void showJoinGroupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_join_group, null);

        TextInputEditText inputCode = dialogView.findViewById(R.id.input_group_code);
        Button btnJoin = dialogView.findViewById(R.id.btn_join_group);

        AlertDialog dialog = builder.setView(dialogView).create();

        btnJoin.setOnClickListener(v -> {
            String code = inputCode.getText().toString().trim().toUpperCase();
            if (code.isEmpty() || code.length() != 6) {
                Toast.makeText(getContext(), "Codul trebuie să aibă 6 caractere", Toast.LENGTH_SHORT).show();
                return;
            }

            ActivityGroup group = db.groupDao().getGroupByCode(code);
            if (group == null) {
                Toast.makeText(getContext(), "Grup negăsit. Verifică codul!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check if already a member
            User currentUser = sessionManager.getCurrentUser();
            GroupMember existingMember = db.groupDao().getMember(group.id, currentUser.id);
            if (existingMember != null) {
                Toast.makeText(getContext(), "Ești deja în acest grup!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }

            // Check member limit
            int currentMembers = db.groupDao().getAcceptedMemberCount(group.id);
            if (currentMembers >= group.maxMembers) {
                Toast.makeText(getContext(), "Grupul este plin!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Join the group
            GroupMember member = new GroupMember(group.id, currentUser.id, currentUser.name, false);
            member.status = "accepted";
            db.groupDao().insertMember(member);
            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(member);

            Toast.makeText(getContext(), "Te-ai alăturat grupului " + group.groupName + "!",
                    Toast.LENGTH_SHORT).show();
            sessionManager.awardAchievement("S-a alăturat grupului: " + group.groupName, 25);
            dialog.dismiss();
            loadUserGroups();
        });

        dialog.show();
    }

    // ==================== GROUP SCHEDULE ====================

    private void showGroupScheduleDialog(ActivityGroup group) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_group_schedule, null);

        TextView textDate = dialogView.findViewById(R.id.text_selected_schedule_date);
        TextView textStartTime = dialogView.findViewById(R.id.text_start_time);
        TextView textEndTime = dialogView.findViewById(R.id.text_end_time);
        SwitchMaterial switchAvailable = dialogView.findViewById(R.id.switch_available);
        TextInputEditText inputNote = dialogView.findViewById(R.id.input_schedule_note);
        RecyclerView recyclerSchedules = dialogView.findViewById(R.id.recycler_member_schedules);
        TextView textNoSchedules = dialogView.findViewById(R.id.text_no_schedules);

        recyclerSchedules.setLayoutManager(new LinearLayoutManager(getContext()));

        // Default to selected date from calendar
        final long[] scheduleDate = { selectedDate };
        final String[] startTime = { "09:00" };
        final String[] endTime = { "18:00" };

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());
        textDate.setText(sdf.format(new Date(selectedDate)));

        // Load existing schedules
        loadSchedulesForGroup(group, scheduleDate[0], recyclerSchedules, textNoSchedules);

        // Date picker
        textDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(scheduleDate[0]);
            new DatePickerDialog(requireContext(), (view, year, month, day) -> {
                Calendar selected = Calendar.getInstance();
                selected.set(year, month, day, 0, 0, 0);
                selected.set(Calendar.MILLISECOND, 0);
                scheduleDate[0] = selected.getTimeInMillis();
                textDate.setText(sdf.format(new Date(scheduleDate[0])));
                loadSchedulesForGroup(group, scheduleDate[0], recyclerSchedules, textNoSchedules);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Time pickers
        textStartTime.setOnClickListener(v -> {
            new TimePickerDialog(getContext(), (view, hour, minute) -> {
                startTime[0] = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                textStartTime.setText(startTime[0]);
            }, 9, 0, true).show();
        });

        textEndTime.setOnClickListener(v -> {
            new TimePickerDialog(getContext(), (view, hour, minute) -> {
                endTime[0] = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                textEndTime.setText(endTime[0]);
            }, 18, 0, true).show();
        });

        AlertDialog dialog = builder.setView(dialogView)
                .setPositiveButton("Salvează", (d, w) -> {
                    User currentUser = sessionManager.getCurrentUser();
                    if (currentUser == null)
                        return;

                    // Clear old schedule for this date
                    db.scheduleDao().clearUserScheduleForDate(group.id, currentUser.id, scheduleDate[0]);

                    // Save new schedule
                    MemberSchedule schedule = new MemberSchedule(
                            group.id,
                            currentUser.id,
                            currentUser.name,
                            scheduleDate[0],
                            startTime[0],
                            endTime[0],
                            switchAvailable.isChecked());

                    String note = inputNote.getText() != null ? inputNote.getText().toString().trim() : "";
                    schedule.note = note;

                    db.scheduleDao().insert(schedule);
                    com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                            .pushScheduleToCloud(schedule);
                    Toast.makeText(getContext(), "Programul tău a fost salvat!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Închide", null)
                .create();

        dialog.show();
    }

    private void loadSchedulesForGroup(ActivityGroup group, long date,
            RecyclerView recycler, TextView emptyText) {
        List<MemberSchedule> schedules = db.scheduleDao().getSchedulesForGroupOnDate(group.id, date);
        if (schedules.isEmpty()) {
            recycler.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
            MemberScheduleAdapter adapter = new MemberScheduleAdapter(requireContext(), schedules);
            recycler.setAdapter(adapter);
        }
    }

    // ==================== ADD ACTIVITY ====================

    private void fetchPlaces() {
        apiService.getPlaces().enqueue(new retrofit2.Callback<List<com.cityscape.app.model.Place>>() {
            @Override
            public void onResponse(retrofit2.Call<List<com.cityscape.app.model.Place>> call,
                    retrofit2.Response<List<com.cityscape.app.model.Place>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    allPlaces = response.body();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<com.cityscape.app.model.Place>> call, Throwable t) {

                Log.e("CalendarFragment", "Failed to fetch places", t);
            }
        });
    }

    private void showAddActivityDialog() {
        if (!isAdded() || getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_activity, null);

        android.widget.AutoCompleteTextView inputName = dialogView.findViewById(R.id.input_place_name);
        EditText inputType = dialogView.findViewById(R.id.input_place_type);
        TextView inputTime = dialogView.findViewById(R.id.input_time);
        EditText inputBudget = dialogView.findViewById(R.id.input_budget);
        com.google.android.material.button.MaterialButtonToggleGroup toggleCurrency = dialogView
                .findViewById(R.id.toggle_currency);
        TextView textConversion = dialogView.findViewById(R.id.text_conversion_result);
        EditText inputNotes = dialogView.findViewById(R.id.input_notes);

        // Setup AutoComplete
        List<String> placeNames = new ArrayList<>();
        for (com.cityscape.app.model.Place p : allPlaces) {
            placeNames.add(p.name);
        }
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, placeNames);
        inputName.setAdapter(adapter);
        inputName.setDropDownBackgroundResource(R.color.app_card);

        inputName.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            for (com.cityscape.app.model.Place p : allPlaces) {
                if (p.name.equals(selectedName)) {
                    inputType.setText(p.type);
                    break;
                }
            }
        });

        com.google.android.material.chip.ChipGroup suggestionChips = dialogView.findViewById(R.id.dialog_activity_suggestions);
        if (suggestionChips != null) {
            for (int i = 0; i < suggestionChips.getChildCount(); i++) {
                com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) suggestionChips.getChildAt(i);
                chip.setOnClickListener(v -> {
                    String name = chip.getText().toString().replaceAll("[🎬🎾☕🍔 ]", "");
                    inputName.setText(name);
                    inputType.setText(name.equals("Cinema") ? "Movie Theater" : (name.equals("Tenis") ? "Sports" : name));
                });
            }
        }

        final String[] selectedTime = { "10:00" };
        final double EUR_RATE = 4.97; // 1 EUR = 4.97 RON

        android.text.TextWatcher budgetWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateConversion(s.toString(), toggleCurrency.getCheckedButtonId(), textConversion, EUR_RATE);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        };

        inputBudget.addTextChangedListener(budgetWatcher);
        toggleCurrency.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                updateConversion(inputBudget.getText().toString(), checkedId, textConversion, EUR_RATE);
            }
        });

        inputTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(getContext(),
                    (view, hourOfDay, minute) -> {
                        selectedTime[0] = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                        inputTime.setText(selectedTime[0]);
                    }, 10, 0, true);
            timePicker.show();
        });

        builder.setView(dialogView)
                .setTitle("Adaugă Activitate")
                .setPositiveButton("Adaugă", null); // Set to null first to override listener and prevent dismissal

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = inputName.getText().toString().trim();
            String type = inputType.getText().toString().trim();
            String notes = inputNotes.getText().toString().trim();

            if (name.isEmpty()) {
                inputName.setError("Introdu numele locului");
                return;
            }

            // Validate against list
            boolean isValid = false;
            for (com.cityscape.app.model.Place p : allPlaces) {
                if (p.name.equalsIgnoreCase(name)) {
                    isValid = true;
                    // Ensure exact casing from the list
                    name = p.name;
                    if (type.isEmpty())
                        type = p.type;
                    break;
                }
            }

            if (!isValid) {
                inputName.setError("Te rugăm să alegi o locație validă din listă!");
                return;
            }

            PlannedActivity activity = new PlannedActivity(
                    sessionManager.getUserId(),
                    null,
                    name,
                    type.isEmpty() ? "Activitate" : type,
                    "",
                    selectedDate,
                    selectedTime[0]);
            activity.notes = notes;

            String budgetStr = inputBudget.getText().toString();
            if (!budgetStr.isEmpty()) {
                activity.budget = Double.parseDouble(budgetStr);
            }
            activity.currency = toggleCurrency.getCheckedButtonId() == R.id.btn_eur ? "EUR" : "RON";

            db.activityDao().insert(activity);
            android.content.Context context = getContext();
            if (context != null) {
                com.cityscape.app.data.SupabaseSyncManager.getInstance(context)
                        .pushActivityToCloud(activity);
            }
            loadActivitiesForDate(selectedDate);

            Toast.makeText(getContext(), "Activitate adăugată!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    private void updateConversion(String amountStr, int checkedId, TextView textConversion, double rate) {
        if (amountStr.isEmpty()) {
            textConversion.setVisibility(View.GONE);
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            textConversion.setVisibility(View.VISIBLE);
            if (checkedId == R.id.btn_ron) {
                double converted = amount / rate;
                textConversion.setText(String.format(Locale.getDefault(), "Conversie: %.2f EUR", converted));
            } else {
                double converted = amount * rate;
                textConversion.setText(String.format(Locale.getDefault(), "Conversie: %.2f RON", converted));
            }
        } catch (NumberFormatException e) {
            textConversion.setVisibility(View.GONE);
        }
    }

    // ==================== EDIT ACTIVITY ====================

    /**
     * Opens the add-activity dialog pre-filled with the existing activity's data.
     * On save, updates the Room record and pushes to Supabase.
     */
    private void showEditActivityDialog(PlannedActivity existingActivity) {
        if (!isAdded() || getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_activity, null);

        android.widget.AutoCompleteTextView inputName = dialogView.findViewById(R.id.input_place_name);
        EditText inputType    = dialogView.findViewById(R.id.input_place_type);
        TextView inputTime    = dialogView.findViewById(R.id.input_time);
        EditText inputBudget  = dialogView.findViewById(R.id.input_budget);
        com.google.android.material.button.MaterialButtonToggleGroup toggleCurrency =
                dialogView.findViewById(R.id.toggle_currency);
        TextView textConversion = dialogView.findViewById(R.id.text_conversion_result);
        EditText inputNotes   = dialogView.findViewById(R.id.input_notes);

        // Pre-fill existing values
        inputName.setText(existingActivity.placeName);
        inputType.setText(existingActivity.placeType);
        inputNotes.setText(existingActivity.notes);
        if (existingActivity.budget > 0)
            inputBudget.setText(String.valueOf((int) existingActivity.budget));

        // Pre-select currency
        if ("EUR".equals(existingActivity.currency)) {
            toggleCurrency.check(R.id.btn_eur);
        } else {
            toggleCurrency.check(R.id.btn_ron);
        }

        // Autocomplete
        List<String> placeNames = new ArrayList<>();
        for (com.cityscape.app.model.Place p : allPlaces) placeNames.add(p.name);
        android.widget.ArrayAdapter<String> acAdapter = new android.widget.ArrayAdapter<>(
                getContext(), android.R.layout.simple_dropdown_item_1line, placeNames);
        inputName.setAdapter(acAdapter);
        inputName.setDropDownBackgroundResource(R.color.app_card);

        final String[] selectedTime = { existingActivity.scheduledTime != null ? existingActivity.scheduledTime : "10:00" };
        inputTime.setText(selectedTime[0]);
        inputTime.setOnClickListener(v -> {
            int h = 10, m = 0;
            try {
                String[] parts = selectedTime[0].split(":");
                h = Integer.parseInt(parts[0]); m = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
            new TimePickerDialog(getContext(), (view, hourOfDay, minute) -> {
                selectedTime[0] = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                inputTime.setText(selectedTime[0]);
            }, h, m, true).show();
        });

        final double EUR_RATE = 4.97;
        inputBudget.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                updateConversion(s.toString(), toggleCurrency.getCheckedButtonId(), textConversion, EUR_RATE);
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        toggleCurrency.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (isChecked) updateConversion(inputBudget.getText().toString(), checkedId, textConversion, EUR_RATE);
        });

        AlertDialog dialog = builder.setView(dialogView)
                .setTitle("Editează Activitate")
                .setPositiveButton("Salvează", null)
                .setNegativeButton("Anulează", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name  = inputName.getText().toString().trim();
            String type  = inputType.getText().toString().trim();
            String notes = inputNotes.getText().toString().trim();

            if (name.isEmpty()) { inputName.setError("Introdu numele locului"); return; }

            existingActivity.placeName    = name;
            existingActivity.placeType    = type.isEmpty() ? existingActivity.placeType : type;
            existingActivity.scheduledTime = selectedTime[0];
            existingActivity.notes        = notes;

            String budgetStr = inputBudget.getText().toString();
            if (!budgetStr.isEmpty()) {
                try { existingActivity.budget = Double.parseDouble(budgetStr); } catch (Exception ignored) {}
            }
            existingActivity.currency = toggleCurrency.getCheckedButtonId() == R.id.btn_eur ? "EUR" : "RON";

            new Thread(() -> {
                db.activityDao().update(existingActivity);
                android.content.Context ctx = getContext();
                if (ctx != null)
                    com.cityscape.app.data.SupabaseSyncManager.getInstance(ctx).updateActivityInCloud(existingActivity);
                if (getActivity() != null)
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) {
                            loadActivitiesForDate(selectedDate);
                            Toast.makeText(getContext(), "Activitate actualizată!", Toast.LENGTH_SHORT).show();
                        }
                    });
            }).start();

            dialog.dismiss();
        });
    }

    // ==================== AVAILABILITY / DOODLE ====================

    /**
     * Shows a "Who's free?" dialog.
     * - User picks a date range (up to 7 days)
     * - Loads the calendar of the current user + every invited user for those days
     * - Highlights hours where EVERYONE is free (no PlannedActivity overlaps)
     * - Proposes the best common slot
     * - Allows inviting a single friend (1-on-1) or a full group
     */
    private void showAvailabilityPickerDialog(PlannedActivity activityToSchedule) {
        if (!isAdded() || getContext() == null) return;

        android.content.Context ctx = getContext();
        User currentUser = sessionManager.getCurrentUser();
        if (currentUser == null) return;

        // ── Build dialog UI programmatically ──────────────────────────
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // Section title
        TextView titleLabel = new TextView(ctx);
        titleLabel.setText("🗓️ Verifică disponibilitate");
        titleLabel.setTextSize(16f);
        titleLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        titleLabel.setTextColor(android.graphics.Color.WHITE);
        root.addView(titleLabel);

        // Date range picker row
        TextView dateRangeInfo = new TextView(ctx);
        SimpleDateFormat sdf7 = new SimpleDateFormat("dd MMM", Locale.getDefault());
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(selectedDate);
        final long[] rangeStart = { normalizeDate(startCal.getTimeInMillis()) };
        final long[] rangeEnd   = { normalizeDate(startCal.getTimeInMillis() + 6L * 86400_000L) };
        dateRangeInfo.setText("Interval: " + sdf7.format(new Date(rangeStart[0]))
                + " – " + sdf7.format(new Date(rangeEnd[0])));
        dateRangeInfo.setTextColor(0xFFAAAAAA);
        root.addView(dateRangeInfo);

        com.google.android.material.button.MaterialButton btnPickRange =
                new com.google.android.material.button.MaterialButton(ctx);
        btnPickRange.setText("Schimbă intervalul");
        root.addView(btnPickRange);

        btnPickRange.setOnClickListener(bv -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(rangeStart[0]);
            new DatePickerDialog(ctx, (dv, y, mo, d) -> {
                Calendar picked = Calendar.getInstance();
                picked.set(y, mo, d);
                rangeStart[0] = normalizeDate(picked.getTimeInMillis());
                rangeEnd[0]   = normalizeDate(picked.getTimeInMillis() + 6L * 86400_000L);
                dateRangeInfo.setText("Interval: " + sdf7.format(new Date(rangeStart[0]))
                        + " – " + sdf7.format(new Date(rangeEnd[0])));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Invite user field (email or username) — 1-on-1 or group
        TextView inviteLabel = new TextView(ctx);
        inviteLabel.setText("\nInvită utilizator (email / username):");
        inviteLabel.setTextColor(android.graphics.Color.WHITE);
        root.addView(inviteLabel);

        EditText inputInvite = new EditText(ctx);
        inputInvite.setHint("ex: ana@email.com sau @ana_pop");
        inputInvite.setHintTextColor(0xFF888888);
        inputInvite.setTextColor(android.graphics.Color.WHITE);
        root.addView(inputInvite);

        // Result area
        TextView resultText = new TextView(ctx);
        resultText.setText("");
        resultText.setTextColor(0xFF00E5FF);
        resultText.setPadding(0, pad / 2, 0, 0);
        root.addView(resultText);

        android.widget.ScrollView scroll = new android.widget.ScrollView(ctx);
        scroll.addView(root);

        AlertDialog dialog = new AlertDialog.Builder(ctx, R.style.DarkDialogTheme)
                .setTitle("Disponibilitate & Invitație")
                .setView(scroll)
                .setPositiveButton("Analizează & Invită", null)
                .setNegativeButton("Închide", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String query = inputInvite.getText().toString().trim();
            resultText.setText("⏳ Se analizează calendarele...");

            new Thread(() -> {
                // 1. Collect current user's busy hours across range
                java.util.Map<Long, List<String>> myBusy = collectBusySlots(
                        currentUser.id, rangeStart[0], rangeEnd[0]);

                // 2. Try to find the invited user
                User invited = null;
                if (!query.isEmpty()) {
                    String q = query.startsWith("@") ? query.substring(1) : query;
                    invited = db.userDao().getUserByEmail(q);
                    if (invited == null) invited = db.userDao().getUserByUsername(q);
                }

                java.util.Map<Long, List<String>> theirBusy = new java.util.HashMap<>();
                if (invited != null) {
                    theirBusy = collectBusySlots(invited.id, rangeStart[0], rangeEnd[0]);
                }

                // 3. Find best common free slot (first 1-hour window free for both)
                String bestSlot = findBestOverlap(myBusy, theirBusy, rangeStart[0], rangeEnd[0]);

                final User finalInvited = invited;
                final String finalBest = bestSlot;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;

                        StringBuilder sb = new StringBuilder();
                        if (finalInvited != null) {
                            sb.append("✅ Utilizator găsit: ").append(finalInvited.name).append("\n\n");
                        } else if (!query.isEmpty()) {
                            sb.append("⚠️ Utilizatorul '").append(query).append("' nu a fost găsit local.\n");
                            sb.append("Invitația va fi trimisă prin cod de grup.\n\n");
                        }

                        if (finalBest != null) {
                            sb.append("🟢 Slot liber propus: ").append(finalBest);
                        } else {
                            sb.append("🔴 Nu există un slot comun liber în intervalul ales.\n");
                            sb.append("Încearcă un interval mai larg.");
                        }

                        resultText.setText(sb.toString());

                        // If best slot found + user exists → offer to send invite
                        if (finalBest != null && finalInvited != null) {
                            new AlertDialog.Builder(ctx, R.style.DarkDialogTheme)
                                    .setTitle("Trimite invitație?")
                                    .setMessage("Cel mai bun slot comun: " + finalBest
                                            + "\n\nVrei să trimiți o invitație lui " + finalInvited.name + "?")
                                    .setPositiveButton("Trimite invitație", (dd, ww) -> {
                                        // Create a group (or invite 1-on-1 via group with 2 members)
                                        ActivityGroup grp = new ActivityGroup(
                                                activityToSchedule.id, currentUser.id,
                                                "Activitate cu " + finalInvited.name);
                                        db.groupDao().insertGroup(grp);
                                        com.cityscape.app.data.SupabaseSyncManager
                                                .getInstance(ctx).pushGroupToCloud(grp);

                                        GroupMember myMember = new GroupMember(
                                                grp.id, currentUser.id, currentUser.name, true);
                                        db.groupDao().insertMember(myMember);
                                        com.cityscape.app.data.SupabaseSyncManager
                                                .getInstance(ctx).pushMemberToCloud(myMember);

                                        sendInvitation(grp, activityToSchedule, finalInvited);
                                        Toast.makeText(ctx,
                                                "Invitație trimisă lui " + finalInvited.name + "!",
                                                Toast.LENGTH_LONG).show();
                                        loadUserGroups();
                                    })
                                    .setNegativeButton("Nu acum", null)
                                    .show();
                        }
                    });
                }
            }).start();
        });
    }

    /**
     * Collects all busy time slots ("dd/MM HH") for a user in [start, end] from local DB.
     */
    private java.util.Map<Long, List<String>> collectBusySlots(
            String userId, long rangeStart, long rangeEnd) {
        java.util.Map<Long, List<String>> busyByDay = new java.util.LinkedHashMap<>();
        long cursor = rangeStart;
        while (cursor <= rangeEnd) {
            List<PlannedActivity> acts = db.activityDao().getActivitiesForDate(userId, cursor);
            List<String> busyHours = new ArrayList<>();
            for (PlannedActivity a : acts) {
                if (a.scheduledTime != null && a.scheduledTime.contains(":")) {
                    busyHours.add(a.scheduledTime.split(":")[0].trim()); // busy hour string
                }
            }
            busyByDay.put(cursor, busyHours);
            cursor += 86400_000L; // next day
        }
        return busyByDay;
    }

    /**
     * Finds the earliest 1-hour slot where BOTH users are free (08:00–22:00 window).
     * Returns a human-readable string or null if none found.
     */
    private String findBestOverlap(
            java.util.Map<Long, List<String>> myBusy,
            java.util.Map<Long, List<String>> theirBusy,
            long rangeStart, long rangeEnd) {

        SimpleDateFormat dayFmt  = new SimpleDateFormat("EEE dd MMM", Locale.getDefault());
        long cursor = rangeStart;
        while (cursor <= rangeEnd) {
            List<String> mine  = myBusy.getOrDefault(cursor, new ArrayList<>());
            List<String> theirs = theirBusy.isEmpty() ? new ArrayList<>() :
                    theirBusy.getOrDefault(cursor, new ArrayList<>());

            for (int hour = 8; hour <= 21; hour++) {
                String h = String.format(Locale.getDefault(), "%02d", hour);
                boolean myFree    = !mine.contains(h);
                boolean theirFree = !theirs.contains(h);
                if (myFree && theirFree) {
                    return dayFmt.format(new Date(cursor)) + " la " + h + ":00–"
                            + String.format(Locale.getDefault(), "%02d", hour + 1) + ":00";
                }
            }
            cursor += 86400_000L;
        }
        return null;
    }
}
