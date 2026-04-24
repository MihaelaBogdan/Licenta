package com.cityscape.app.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.OnConflictStrategy;
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
}, version = 11, exportSchema = false)
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
                                .addCallback(new RoomDatabase.Callback() {
                                    @Override
                                    public void onCreate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                                        super.onCreate(db);
                                        // Room creation callback doesn't have direct access to DAO easily here,
                                        // so we rely on the check in getInstance or just seed later.
                                    }
                                })
                                .fallbackToDestructiveMigration()
                                .build();
                        INSTANCE.seedTestData();
                }
            }
        }
        return INSTANCE;
    }

    public void seedTestData() {
        new Thread(() -> {
            try {
                UserDao dao = userDao();
                if (dao.getUserByEmail("admin@cityscape.app") == null) {
                    User admin = new User("Administrator", "admin@cityscape.app", "Admin123!");
                    admin.level = 10;
                    admin.totalXp = 5000;
                    dao.insert(admin);
                }
                if (dao.getUserByEmail("test@example.com") == null) {
                    User test = new User("Test User", "test@example.com", "Password123!");
                    test.level = 3;
                    test.totalXp = 1200;
                    dao.insert(test);
                }
                if (dao.getUserByEmail("mihaela@licenta.ro") == null) {
                    User mihaela = new User("Mihaela Bogdan", "mihaela@licenta.ro", "Mihaela2026!");
                    mihaela.level = 5;
                    mihaela.totalXp = 2500;
                    dao.insert(mihaela);
                }
            } catch (Exception e) {
                android.util.Log.e("AppDatabase", "Error seeding test data", e);
            }
        }).start();
    }
}
