package com.example.licenta.ui.calendar;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.licenta.R;
import com.example.licenta.adapter.ActivityAdapter;
import com.example.licenta.adapter.InvitationAdapter;
import com.example.licenta.data.AppDatabase;
import com.example.licenta.data.SessionManager;
import com.example.licenta.model.ActivityGroup;
import com.example.licenta.model.GroupMember;
import com.example.licenta.model.Invitation;
import com.example.licenta.model.PlannedActivity;
import com.example.licenta.model.User;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarFragment extends Fragment {

    private CalendarView calendarView;
    private RecyclerView recyclerActivities;
    private LinearLayout emptyState;
    private TextView textSelectedDate;
    private ImageView btnAddActivity;

    private AppDatabase db;
    private SessionManager sessionManager;
    private ActivityAdapter adapter;
    private long selectedDate;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_calendar, container, false);

        db = AppDatabase.getInstance(requireContext());
        sessionManager = new SessionManager(requireContext());

        initViews(view);
        setupCalendar();
        checkPendingInvitations();

        // Select today by default
        selectedDate = normalizeDate(System.currentTimeMillis());
        loadActivitiesForDate(selectedDate);

        return view;
    }

    private void initViews(View view) {
        calendarView = view.findViewById(R.id.calendar_view);
        recyclerActivities = view.findViewById(R.id.recycler_activities);
        emptyState = view.findViewById(R.id.empty_state);
        textSelectedDate = view.findViewById(R.id.text_selected_date);
        btnAddActivity = view.findViewById(R.id.btn_add_activity);

        recyclerActivities.setLayoutManager(new LinearLayoutManager(getContext()));

        btnAddActivity.setOnClickListener(v -> showAddActivityDialog());
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
            textSelectedDate.setText("Today's Activities");
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

            adapter = new ActivityAdapter(requireContext(), activities, new ActivityAdapter.OnActivityActionListener() {
                @Override
                public void onCompleteClick(PlannedActivity activity, int position) {
                    activity.isCompleted = true;
                    db.activityDao().update(activity);
                    sessionManager.recordPlaceVisit(activity.placeName);
                    Toast.makeText(getContext(),
                            "Completed: " + activity.placeName + " (+50 XP)",
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
            recyclerActivities.setAdapter(adapter);
        }
    }

    private void shareActivity(PlannedActivity activity) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(activity.scheduledDate));

        String shareText = "🎯 Join me for an activity!\n\n" +
                "📍 " + activity.placeName + "\n" +
                "📅 " + dateStr + " at " + activity.scheduledTime + "\n";

        // Check if there's a group
        ActivityGroup group = db.groupDao().getGroupForActivity(activity.id);
        if (group != null) {
            shareText += "\n🔗 Join with code: " + group.groupCode + "\n" +
                    "Link: " + group.getShareLink();
        }

        shareText += "\n\nSent via MysticMinds";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join my activity: " + activity.placeName);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private void showCreateGroupDialog(PlannedActivity activity) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_create_group, null);

        EditText inputGroupName = dialogView.findViewById(R.id.input_group_name);
        EditText inputSearchEmail = dialogView.findViewById(R.id.input_search_email);
        RecyclerView recyclerSelectedUsers = dialogView.findViewById(R.id.recycler_selected_users);
        TextView btnShareLink = dialogView.findViewById(R.id.btn_share_link);

        inputGroupName.setText(activity.placeName + " Group");

        List<User> selectedUsers = new ArrayList<>();

        // Simple search functionality
        inputSearchEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Could implement user search here
            }
        });

        AlertDialog dialog = builder.setView(dialogView)
                .setTitle("Create Group")
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String groupName = inputGroupName.getText().toString().trim();
                if (groupName.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter a group name", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Create the group
                ActivityGroup group = new ActivityGroup(activity.id, sessionManager.getUserId(), groupName);
                long groupId = db.groupDao().insertGroup(group);
                group.id = (int) groupId;

                // Add creator as member
                User currentUser = sessionManager.getCurrentUser();
                GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
                db.groupDao().insertMember(creatorMember);

                // Send invitations to selected users
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
                for (User user : selectedUsers) {
                    Invitation invitation = new Invitation(
                            currentUser.id,
                            currentUser.name,
                            user.id,
                            group.id,
                            groupName,
                            activity.placeName,
                            sdf.format(new Date(activity.scheduledDate)),
                            activity.scheduledTime);
                    db.invitationDao().insert(invitation);
                }

                Toast.makeText(getContext(), "Group created! Share code: " + group.groupCode, Toast.LENGTH_LONG).show();
                dialog.dismiss();
                loadActivitiesForDate(selectedDate);

                // Offer to share
                shareGroupLink(group, activity);
            });
        });

        btnShareLink.setOnClickListener(v -> {
            // Create group first, then share
            String groupName = inputGroupName.getText().toString().trim();
            if (groupName.isEmpty())
                groupName = activity.placeName + " Group";

            ActivityGroup group = new ActivityGroup(activity.id, sessionManager.getUserId(), groupName);
            long groupId = db.groupDao().insertGroup(group);
            group.id = (int) groupId;

            User currentUser = sessionManager.getCurrentUser();
            GroupMember creatorMember = new GroupMember(group.id, currentUser.id, currentUser.name, true);
            db.groupDao().insertMember(creatorMember);

            dialog.dismiss();
            shareGroupLink(group, activity);
            loadActivitiesForDate(selectedDate);
        });

        dialog.show();
    }

    private void shareGroupLink(ActivityGroup group, PlannedActivity activity) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        String dateStr = sdf.format(new Date(activity.scheduledDate));

        String shareText = "🎉 You're invited to join my group!\n\n" +
                "👥 Group: " + group.groupName + "\n" +
                "📍 " + activity.placeName + "\n" +
                "📅 " + dateStr + " at " + activity.scheduledTime + "\n\n" +
                "🔑 Join Code: " + group.groupCode + "\n" +
                "🔗 " + group.getShareLink() + "\n\n" +
                "Open MysticMinds app and enter the code to join!";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Join " + group.groupName);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share invite via"));
    }

    private void showGroupDetails(ActivityGroup group, PlannedActivity activity) {
        List<GroupMember> members = db.groupDao().getMembersForGroup(group.id);

        StringBuilder memberList = new StringBuilder();
        for (GroupMember member : members) {
            memberList.append("• ").append(member.userName);
            if (member.isCreator)
                memberList.append(" (Creator)");
            memberList.append(" - ").append(member.status).append("\n");
        }

        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle(group.groupName)
                .setMessage("📌 Share Code: " + group.groupCode + "\n\n" +
                        "👥 Members (" + members.size() + "):\n" + memberList.toString())
                .setPositiveButton("Share Invite", (d, w) -> shareGroupLink(group, activity))
                .setNeutralButton("Invite by Email", (d, w) -> showInviteByEmailDialog(group, activity))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showInviteByEmailDialog(ActivityGroup group, PlannedActivity activity) {
        EditText input = new EditText(requireContext());
        input.setHint("Enter friend's email");
        input.setPadding(50, 30, 50, 30);

        new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("Invite by Email")
                .setView(input)
                .setPositiveButton("Send Invite", (d, w) -> {
                    String email = input.getText().toString().trim();
                    User targetUser = db.userDao().getUserByEmail(email);

                    if (targetUser == null) {
                        Toast.makeText(getContext(), "User not found. Share the link instead!", Toast.LENGTH_LONG)
                                .show();
                        shareGroupLink(group, activity);
                        return;
                    }

                    // Check if already a member
                    GroupMember existingMember = db.groupDao().getMember(group.id, targetUser.id);
                    if (existingMember != null) {
                        Toast.makeText(getContext(), "User is already in the group!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Send invitation
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

                    Toast.makeText(getContext(), "Invitation sent to " + targetUser.name + "!", Toast.LENGTH_SHORT)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkPendingInvitations() {
        int pendingCount = db.invitationDao().getPendingCount(sessionManager.getUserId());
        if (pendingCount > 0) {
            showPendingInvitationsDialog();
        }
    }

    private void showPendingInvitationsDialog() {
        List<Invitation> pending = db.invitationDao().getPendingInvitations(sessionManager.getUserId());

        if (pending.isEmpty())
            return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_invitations, null);
        RecyclerView recyclerInvitations = dialogView.findViewById(R.id.recycler_invitations);
        recyclerInvitations.setLayoutManager(new LinearLayoutManager(getContext()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.DarkDialogTheme)
                .setTitle("📬 Pending Invitations (" + pending.size() + ")")
                .setView(dialogView)
                .setNegativeButton("Close", null)
                .create();

        InvitationAdapter invAdapter = new InvitationAdapter(requireContext(), pending,
                new InvitationAdapter.OnInvitationActionListener() {
                    @Override
                    public void onAccept(Invitation invitation, int position) {
                        // Accept invitation
                        invitation.status = "accepted";
                        db.invitationDao().update(invitation);

                        // Add user to group
                        User currentUser = sessionManager.getCurrentUser();
                        GroupMember member = new GroupMember(invitation.groupId, currentUser.id, currentUser.name,
                                false);
                        member.status = "accepted";
                        db.groupDao().insertMember(member);

                        Toast.makeText(getContext(), "Joined " + invitation.groupName + "!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();

                        // Award XP for joining
                        sessionManager.awardAchievement("Joined group: " + invitation.groupName, 25);
                    }

                    @Override
                    public void onDecline(Invitation invitation, int position) {
                        invitation.status = "declined";
                        db.invitationDao().update(invitation);
                        Toast.makeText(getContext(), "Invitation declined", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                });
        recyclerInvitations.setAdapter(invAdapter);

        dialog.show();
    }

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
                .setTitle("Add Activity")
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = inputName.getText().toString().trim();
                    String type = inputType.getText().toString().trim();
                    String notes = inputNotes.getText().toString().trim();

                    if (name.isEmpty()) {
                        Toast.makeText(getContext(), "Please enter a place name", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    PlannedActivity activity = new PlannedActivity(
                            sessionManager.getUserId(),
                            0,
                            name,
                            type.isEmpty() ? "Activity" : type,
                            "",
                            selectedDate,
                            selectedTime[0]);
                    activity.notes = notes;

                    db.activityDao().insert(activity);
                    loadActivitiesForDate(selectedDate);

                    Toast.makeText(getContext(), "Activity added!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
