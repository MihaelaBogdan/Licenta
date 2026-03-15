package com.example.licenta.ui.calendar;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
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
import com.example.licenta.R;
import com.example.licenta.adapter.ActivityAdapter;
import com.example.licenta.adapter.GroupCardAdapter;
import com.example.licenta.adapter.InvitationAdapter;
import com.example.licenta.adapter.MemberScheduleAdapter;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.model.ActivityGroup;
import com.example.licenta.model.GroupMember;
import com.example.licenta.model.Invitation;
import com.example.licenta.model.MemberSchedule;
import com.example.licenta.model.PlannedActivity;
import com.example.licenta.model.User;
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
    private CardView bannerInvitations;
    private TextView textInvitationCount;
    private TextView btnViewInvitations;

    private AppDatabase db;
    private SessionManager sessionManager;
    private ActivityAdapter activityAdapter;
    private GroupCardAdapter groupCardAdapter;
    private long selectedDate;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        db = AppDatabase.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        initViews(view);
        setupCalendar();

        // Select today by default
        selectedDate = normalizeDate(System.currentTimeMillis());
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
        bannerInvitations = view.findViewById(R.id.banner_invitations);
        textInvitationCount = view.findViewById(R.id.text_invitation_count);
        btnViewInvitations = view.findViewById(R.id.btn_view_invitations);

        recyclerActivities.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerGroups.setLayoutManager(new LinearLayoutManager(getContext()));

        btnAddActivity.setOnClickListener(v -> showAddActivityDialog());
        btnJoinGroup.setOnClickListener(v -> showJoinGroupDialog());
        btnViewInvitations.setOnClickListener(v -> showPendingInvitationsDialog());
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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).updateActivityInCloud(activity);
                            sessionManager.recordPlaceVisit(activity.placeName);
                            Toast.makeText(getContext(),
                                    activity.placeName + " completat (+50 XP)",
                                    Toast.LENGTH_SHORT).show();
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
                    });
            recyclerActivities.setAdapter(activityAdapter);
        }
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

        String shareText = "Hai la " + activity.placeName + "!\n" +
                dateStr + ", ora " + activity.scheduledTime + "\n";

        ActivityGroup group = db.groupDao().getGroupForActivity(activity.id);
        if (group != null) {
            shareText += "Cod grup: " + group.groupCode + "\n";
        }

        // Show chooser: WhatsApp or general share
        showShareChooser(shareText, "Hai la " + activity.placeName);
    }

    private void shareOnWhatsApp(ActivityGroup group) {
        PlannedActivity activity = findActivityForGroup(group);

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = activity != null ? sdf.format(new Date(activity.scheduledDate)) : "";

        String shareText = "Hai în grupul " + group.groupName + "!\n";

        if (activity != null) {
            shareText += activity.placeName + "\n" +
                    dateStr + ", ora " + activity.scheduledTime + "\n";
        }

        shareText += "\nCod de intrare: " + group.groupCode;

        // Try WhatsApp first
        try {
            Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
            whatsappIntent.setType("text/plain");
            whatsappIntent.setPackage("com.whatsapp");
            whatsappIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(whatsappIntent);
        } catch (Exception e) {
            // WhatsApp not installed, try general share
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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushGroupToCloud(group);

                User currentUser = sessionManager.getCurrentUser();
                GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
                db.groupDao().insertMember(creatorMember);
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(creatorMember);

                // Check if user typed an email to invite
                String email = inputSearchEmail.getText().toString().trim();
                if (!email.isEmpty()) {
                    User targetUser = db.userDao().getUserByEmail(email);
                    if (targetUser != null) {
                        sendInvitation(group, activity, targetUser);
                    }
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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushGroupToCloud(group);

            User currentUser = sessionManager.getCurrentUser();
            GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
            db.groupDao().insertMember(creatorMember);
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(creatorMember);

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
                .setNeutralButton("Programe", (d, w) -> showGroupScheduleDialog(group))
                .setNegativeButton("Închide", null)
                .show();
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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushInvitationToCloud(invitation);

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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).updateInvitationInCloud(invitation);

                        User currentUser = sessionManager.getCurrentUser();
                        GroupMember member = new GroupMember(invitation.groupId, currentUser.id, currentUser.name,
                                false);
                        member.status = "accepted";
                        db.groupDao().insertMember(member);
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(member);

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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).updateInvitationInCloud(invitation);
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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushMemberToCloud(member);

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
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushScheduleToCloud(schedule);
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

    private void showAddActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_add_activity, null);

        EditText inputName = dialogView.findViewById(R.id.input_place_name);
        EditText inputType = dialogView.findViewById(R.id.input_place_type);
        TextView inputTime = dialogView.findViewById(R.id.input_time);
        EditText inputNotes = dialogView.findViewById(R.id.input_notes);

        final String[] selectedTime = { "10:00" };

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
                .setPositiveButton("Adaugă", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    String type = inputType.getText().toString().trim();
                    String notes = inputNotes.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "Introdu numele locului", Toast.LENGTH_SHORT).show();
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

                    db.activityDao().insert(activity);
                            com.example.licenta.data.SupabaseSyncManager.getInstance(requireContext()).pushActivityToCloud(activity);
                    loadActivitiesForDate(selectedDate);

                    Toast.makeText(getContext(), "Activitate adăugată!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Anulează", null)
                .show();
    }
}
