package com.cityscape.app.ui.calendar;

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
    private ImageView btnAddActivity;
    private ImageView btnJoinGroup;
    private ImageView btnSyncCalendar;
    private ImageView btnExportCalendar;
    private CardView bannerInvitations;
    private TextView textInvitationCount;
    private TextView btnViewInvitations;

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
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
            calendarView.setDate(selectedDate);
            textSelectedDate.setText(dateFormat.format(new Date(selectedDate)));
            
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
        loadActivitiesForDate(selectedDate);
        loadUserGroups();
        checkPendingInvitations();
    }

    private void initViews(View view) {
        calendarView = view.findViewById(R.id.calendar_view);
        recyclerActivities = view.findViewById(R.id.recycler_activities);
        recyclerGroups = view.findViewById(R.id.recycler_groups);
        emptyState = view.findViewById(R.id.empty_state);
        emptyGroupsState = view.findViewById(R.id.empty_groups_state);
        textSelectedDate = view.findViewById(R.id.text_selected_date);
        textGroupCount = view.findViewById(R.id.text_group_count);
        btnAddActivity = view.findViewById(R.id.btn_add_activity);
        btnJoinGroup = view.findViewById(R.id.btn_join_group);
        btnSyncCalendar = view.findViewById(R.id.btn_sync_calendar);
        btnExportCalendar = view.findViewById(R.id.btn_export_calendar);
        bannerInvitations = view.findViewById(R.id.banner_invitations);
        textInvitationCount = view.findViewById(R.id.text_invitation_count);
        btnViewInvitations = view.findViewById(R.id.btn_view_invitations);

        com.google.android.material.button.MaterialButton btnReturnHome = view.findViewById(R.id.btn_return_home);
        if (getArguments() != null && getArguments().containsKey("target_date")) {
            btnReturnHome.setVisibility(View.VISIBLE);
            btnReturnHome.setOnClickListener(v -> {
                androidx.navigation.Navigation.findNavController(view).navigate(R.id.navigation_home);
            });
        }

        recyclerActivities.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerGroups.setLayoutManager(new LinearLayoutManager(getContext()));

        btnAddActivity.setOnClickListener(v -> showAddActivityDialog());
        btnJoinGroup.setOnClickListener(v -> showJoinGroupDialog());
        btnViewInvitations.setOnClickListener(v -> showPendingInvitationsDialog());
        btnSyncCalendar.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.READ_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED) {
                syncGoogleCalendar();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR);
            }
        });
        btnExportCalendar.setOnClickListener(v -> exportDayToExternalCalendar());
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
                String[] parts = activity.scheduledTime.split(":");
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
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
        
        new Thread(() -> {
            android.content.ContentResolver contentResolver = requireContext().getContentResolver();
            
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
                        com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushActivityToCloud(activity);
                        count++;
                    }
                }
                
                final int finalCount = count;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
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
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Eroare la sincronizare. Verifică permisiunile.", Toast.LENGTH_SHORT).show()
                    );
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

        List<PlannedActivity> activities = db.activityDao()
                .getActivitiesForDate(sessionManager.getUserId(), date);

        if (activities.isEmpty()) {
            recyclerActivities.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerActivities.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);

            activityAdapter = new ActivityAdapter(requireContext(), activities,
                    new ActivityAdapter.OnActivityActionListener() {
                        @Override
                        public void onCompleteClick(PlannedActivity activity, int position) {
                            activity.isCompleted = true;
                            db.activityDao().update(activity);
                            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
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
                            Toast.makeText(getContext(),
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
                                showCreateGroupDialog(activity);
                            }
                        }

                        @Override
                        public void onExportClick(PlannedActivity activity) {
                            exportActivityToPhone(activity);
                        }
                    });
            recyclerActivities.setAdapter(activityAdapter);
        }
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
        List<ActivityGroup> groups = db.groupDao().getGroupsForUser(sessionManager.getUserId());

        if (groups.isEmpty()) {
            recyclerGroups.setVisibility(View.GONE);
            emptyGroupsState.setVisibility(View.VISIBLE);
            textGroupCount.setText("");
        } else {
            recyclerGroups.setVisibility(View.VISIBLE);
            emptyGroupsState.setVisibility(View.GONE);
            textGroupCount.setText(groups.size() + (groups.size() == 1 ? " grup" : " grupuri"));

            groupCardAdapter = new GroupCardAdapter(requireContext(), groups,
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
                            PlannedActivity activity = findActivityForGroup(group);
                            if (activity != null) {
                                showGroupDetails(group, activity);
                            }
                        }
                    });
            recyclerGroups.setAdapter(groupCardAdapter);
        }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_group, null);

        EditText inputGroupName = dialogView.findViewById(R.id.input_group_name);
        EditText inputSearchEmail = dialogView.findViewById(R.id.input_search_email);
        TextView btnShareLink = dialogView.findViewById(R.id.btn_share_link);
        
        // Following list
        RecyclerView rvFollowing = dialogView.findViewById(R.id.rv_following_to_invite);
        View labelFollowing = dialogView.findViewById(R.id.text_following_label);
        
        com.cityscape.app.adapter.SmallUserAdapter followingAdapter = new com.cityscape.app.adapter.SmallUserAdapter(null);
        if (rvFollowing != null) {
            rvFollowing.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
            rvFollowing.setAdapter(followingAdapter);
            
            // Fetch following from API
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

        inputGroupName.setText(activity.placeName + " - Grup");

        AlertDialog dialog = builder.setView(dialogView)
                .setTitle("Creează Grup")
                .setPositiveButton("Creează", null)
                .setNegativeButton("Anulează", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String groupName = inputGroupName.getText().toString().trim();
                if (groupName.isEmpty()) {
                    Toast.makeText(getContext(), "Introdu un nume de grup", Toast.LENGTH_SHORT).show();
                    return;
                }

                ActivityGroup group = new ActivityGroup(activity.id, sessionManager.getUserId(), groupName);
                db.groupDao().insertGroup(group);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext()).pushGroupToCloud(group);

                User currentUser = sessionManager.getCurrentUser();
                GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
                db.groupDao().insertMember(creatorMember);
                com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                        .pushMemberToCloud(creatorMember);

                // 1. Invite selected users from Following list
                List<User> selectedUsers = followingAdapter.getSelectedUsers();
                for (User targetUser : selectedUsers) {
                    sendInvitation(group, activity, targetUser);
                }

                // 2. Check if user typed an email to invite
                String email = inputSearchEmail.getText().toString().trim();
                if (!email.isEmpty()) {
                    new Thread(() -> {
                        User targetUser = db.userDao().getUserByEmail(email);
                        if (targetUser != null && getActivity() != null) {
                            getActivity().runOnUiThread(() -> sendInvitation(group, activity, targetUser));
                        }
                    }).start();
                }

                Toast.makeText(getContext(), "Grup creat! Cod: " + group.groupCode, Toast.LENGTH_LONG).show();
                dialog.dismiss();
                loadActivitiesForDate(selectedDate);
                loadUserGroups();

                // Offer to share on WhatsApp
                shareOnWhatsApp(group);
            });
        });

        btnShareLink.setOnClickListener(v -> {
            String groupName = inputGroupName.getText().toString().trim();
            if (groupName.isEmpty())
                groupName = activity.placeName + " - Grup";

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
        List<GroupMember> members = db.groupDao().getMembersForGroup(group.id);

        StringBuilder memberList = new StringBuilder();
        for (GroupMember member : members) {
            memberList.append("• ").append(member.userName);
            if (member.isCreator)
                memberList.append(" (Creator)");
            memberList.append(" — ").append(member.status).append("\n");
        }

        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle(group.groupName)
                .setMessage("Cod partajare: " + group.groupCode + "\n\n" +
                        "Membri (" + members.size() + "):\n" + memberList.toString())
                .setPositiveButton("WhatsApp", (d, w) -> shareOnWhatsApp(group))
                .setNeutralButton("Votare", (d, w) -> showVotingDialog(group))
                .setNegativeButton("Închide", null)
                .show();
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
        if (pendingCount > 0) {
            bannerInvitations.setVisibility(View.VISIBLE);
            textInvitationCount.setText("Ai " + pendingCount +
                    (pendingCount == 1 ? " invitație nouă" : " invitații noi"));
        } else {
            bannerInvitations.setVisibility(View.GONE);
        }
    }

    private void showPendingInvitationsDialog() {
        List<Invitation> pending = db.invitationDao().getPendingInvitations(sessionManager.getUserId());

        if (pending.isEmpty()) {
            bannerInvitations.setVisibility(View.GONE);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
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
                requireContext(), android.R.layout.simple_dropdown_item_1line, placeNames);
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
            com.cityscape.app.data.SupabaseSyncManager.getInstance(requireContext())
                    .pushActivityToCloud(activity);
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
}
