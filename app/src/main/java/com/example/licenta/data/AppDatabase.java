package com.example.licenta.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.licenta.model.User;
import com.example.licenta.model.UserBadge;
import com.example.licenta.model.UserAchievement;
import com.example.licenta.model.PlannedActivity;
import com.example.licenta.model.ActivityGroup;
import com.example.licenta.model.GroupMember;
import com.example.licenta.model.Invitation;
import com.example.licenta.model.MemberSchedule;

@Database(entities = {
        User.class,
        UserBadge.class,
        UserAchievement.class,
        PlannedActivity.class,
        ActivityGroup.class,
        GroupMember.class,
        Invitation.class,
        MemberSchedule.class,
        com.example.licenta.model.GroupSuggestion.class,
        com.example.licenta.model.Vote.class
}, version = 9, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract UserDao userDao();

    public abstract BadgeDao badgeDao();

    public abstract AchievementDao achievementDao();

    public abstract ActivityDao activityDao();

    public abstract GroupDao groupDao();

    public abstract InvitationDao invitationDao();

    public abstract ScheduleDao scheduleDao();

    public abstract GroupSuggestionDao suggestionDao();

    public abstract VoteDao voteDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "licenta_database")
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
