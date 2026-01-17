package com.cityscape.app.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.cityscape.app.database.dao.*;
import com.cityscape.app.database.entities.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room Database for CityScape application
 * Contains all entities and provides DAOs
 */
@Database(entities = {
                User.class,
                UserPreference.class,
                City.class,
                Place.class,
                PlacePhoto.class,
                Review.class,
                Favorite.class,
                Visit.class,
                PlannedActivity.class,
                Itinerary.class,
                Badge.class,
                UserBadge.class,
                Event.class,
                ActivityInvite.class
}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

        // DAOs
        public abstract UserDao userDao();

        public abstract UserPreferenceDao userPreferenceDao();

        public abstract CityDao cityDao();

        public abstract PlaceDao placeDao();

        public abstract PlacePhotoDao placePhotoDao();

        public abstract ReviewDao reviewDao();

        public abstract FavoriteDao favoriteDao();

        public abstract VisitDao visitDao();

        public abstract PlannedActivityDao plannedActivityDao();

        public abstract ItineraryDao itineraryDao();

        public abstract BadgeDao badgeDao();

        public abstract EventDao eventDao();

        public abstract ActivityInviteDao activityInviteDao();

        // Singleton instance
        private static volatile AppDatabase INSTANCE;

        // Executor for database operations
        private static final int NUMBER_OF_THREADS = 4;
        public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

        /**
         * Get singleton instance of database
         */
        public static AppDatabase getDatabase(final Context context) {
                if (INSTANCE == null) {
                        synchronized (AppDatabase.class) {
                                if (INSTANCE == null) {
                                        INSTANCE = Room.databaseBuilder(
                                                        context.getApplicationContext(),
                                                        AppDatabase.class,
                                                        "cityscape_database")
                                                        .addCallback(sRoomDatabaseCallback)
                                                        .fallbackToDestructiveMigration()
                                                        .build();
                                }
                        }
                }
                return INSTANCE;
        }

        /**
         * Callback for database creation - populates initial data
         */
        private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
                @Override
                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);

                        // Populate initial data in background
                        databaseWriteExecutor.execute(() -> {
                                AppDatabase database = INSTANCE;
                                if (database != null) {
                                        populateInitialData(database);
                                }
                        });
                }
        };

        /**
         * Populate database with initial cities and badges
         */
        private static void populateInitialData(AppDatabase database) {
                CityDao cityDao = database.cityDao();
                BadgeDao badgeDao = database.badgeDao();

                // Add Romanian cities
                cityDao.insert(createCity("bucuresti", "București", "Romania", "RO", 44.4268, 26.1025, true));
                cityDao.insert(createCity("cluj", "Cluj-Napoca", "Romania", "RO", 46.7712, 23.6236, true));
                cityDao.insert(createCity("timisoara", "Timișoara", "Romania", "RO", 45.7489, 21.2087, true));
                cityDao.insert(createCity("iasi", "Iași", "Romania", "RO", 47.1585, 27.6014, true));
                cityDao.insert(createCity("brasov", "Brașov", "Romania", "RO", 45.6427, 25.5887, true));
                cityDao.insert(createCity("constanta", "Constanța", "Romania", "RO", 44.1598, 28.6348, true));
                cityDao.insert(createCity("sibiu", "Sibiu", "Romania", "RO", 45.7983, 24.1256, false));
                cityDao.insert(createCity("craiova", "Craiova", "Romania", "RO", 44.3302, 23.7949, false));
                cityDao.insert(createCity("oradea", "Oradea", "Romania", "RO", 47.0465, 21.9189, false));

                // Add European cities
                cityDao.insert(createCity("paris", "Paris", "France", "FR", 48.8566, 2.3522, true));
                cityDao.insert(createCity("london", "London", "United Kingdom", "GB", 51.5074, -0.1278, true));
                cityDao.insert(createCity("rome", "Rome", "Italy", "IT", 41.9028, 12.4964, true));
                cityDao.insert(createCity("barcelona", "Barcelona", "Spain", "ES", 41.3851, 2.1734, true));
                cityDao.insert(createCity("vienna", "Vienna", "Austria", "AT", 48.2082, 16.3738, true));
                cityDao.insert(createCity("berlin", "Berlin", "Germany", "DE", 52.5200, 13.4050, true));
                cityDao.insert(createCity("amsterdam", "Amsterdam", "Netherlands", "NL", 52.3676, 4.9041, true));
                cityDao.insert(createCity("prague", "Prague", "Czech Republic", "CZ", 50.0755, 14.4378, true));
                cityDao.insert(createCity("budapest", "Budapest", "Hungary", "HU", 47.4979, 19.0402, true));

                // Add Global cities
                cityDao.insert(createCity("newyork", "New York", "United States", "US", 40.7128, -74.0060, true));
                cityDao.insert(createCity("tokyo", "Tokyo", "Japan", "JP", 35.6762, 139.6503, true));
                cityDao.insert(createCity("dubai", "Dubai", "UAE", "AE", 25.2048, 55.2708, true));
                cityDao.insert(createCity("singapore", "Singapore", "Singapore", "SG", 1.3521, 103.8198, true));
                cityDao.insert(createCity("sydney", "Sydney", "Australia", "AU", -33.8688, 151.2093, true));
                cityDao.insert(createCity("istanbul", "Istanbul", "Turkey", "TR", 41.0082, 28.9784, true));

                // Add badges
                badgeDao.insertBadge(
                                createBadge("explorer_bronze", "Explorer", "Visit 10 places", Badge.TYPE_VISITS, 10,
                                                Badge.TIER_BRONZE));
                badgeDao.insertBadge(createBadge("explorer_silver", "Super Explorer", "Visit 25 places",
                                Badge.TYPE_VISITS, 25,
                                Badge.TIER_SILVER));
                badgeDao.insertBadge(createBadge("explorer_gold", "Master Explorer", "Visit 50 places",
                                Badge.TYPE_VISITS, 50,
                                Badge.TIER_GOLD));
                badgeDao.insertBadge(
                                createBadge("critic_bronze", "Critic", "Write 5 reviews", Badge.TYPE_REVIEWS, 5,
                                                Badge.TIER_BRONZE));
                badgeDao.insertBadge(
                                createBadge("critic_silver", "Super Critic", "Write 15 reviews", Badge.TYPE_REVIEWS, 15,
                                                Badge.TIER_SILVER));
                badgeDao.insertBadge(
                                createBadge("critic_gold", "Master Critic", "Write 30 reviews", Badge.TYPE_REVIEWS, 30,
                                                Badge.TIER_GOLD));
                badgeDao.insertBadge(createBadge("globetrotter_bronze", "Globetrotter", "Explore 3 cities",
                                Badge.TYPE_CITIES,
                                3, Badge.TIER_BRONZE));
                badgeDao.insertBadge(createBadge("globetrotter_silver", "World Traveler", "Explore 5 cities",
                                Badge.TYPE_CITIES,
                                5, Badge.TIER_SILVER));
                badgeDao.insertBadge(
                                createBadge("globetrotter_gold", "Globe Master", "Explore 10 cities", Badge.TYPE_CITIES,
                                                10, Badge.TIER_GOLD));
                badgeDao.insertBadge(
                                createBadge("photographer", "Photographer", "Add 10 photos", Badge.TYPE_PHOTOS, 10,
                                                Badge.TIER_BRONZE));
                badgeDao.insertBadge(createBadge("trendsetter", "Trendsetter", "First to review a new place",
                                Badge.TYPE_FIRST_REVIEW, 1, Badge.TIER_GOLD));
                badgeDao.insertBadge(createBadge("vip", "VIP", "Make 50+ visits", Badge.TYPE_VISITS, 50,
                                Badge.TIER_PLATINUM));
        }

        private static City createCity(String id, String name, String country, String countryCode,
                        double lat, double lng, boolean isPopular) {
                City city = new City(id, name, country, lat, lng);
                city.setCountryCode(countryCode);
                city.setPopular(isPopular);
                return city;
        }

        private static Badge createBadge(String id, String name, String description,
                        String type, int required, String tier) {
                Badge badge = new Badge(id, name, description, type, required);
                badge.setTier(tier);
                badge.setIconName("ic_badge_" + id);
                return badge;
        }
}
