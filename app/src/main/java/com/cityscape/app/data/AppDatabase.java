package com.cityscape.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.cityscape.app.model.User;
import com.cityscape.app.model.UserBadge;
import com.cityscape.app.model.UserAchievement;
import com.cityscape.app.model.PlannedActivity;
import com.cityscape.app.model.ActivityGroup;
import com.cityscape.app.model.GroupMember;
import com.cityscape.app.model.Invitation;
import com.cityscape.app.model.MemberSchedule;

@Database(entities = {
        User.class,
        UserBadge.class,
        UserAchievement.class,
        PlannedActivity.class,
        ActivityGroup.class,
        GroupMember.class,
        Invitation.class,
        MemberSchedule.class,
        com.cityscape.app.model.GroupSuggestion.class,
        com.cityscape.app.model.Vote.class
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
                            "cityscape_database")
                            .allowMainThreadQueries()
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
