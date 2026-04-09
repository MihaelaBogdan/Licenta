package com.example.licenta.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Helper class to manage app language/locale settings.
 * Default language is Romanian ("ro").
 * User can switch to English ("en").
 */
public class LocaleHelper {

    private static final String PREF_NAME = "LicentaLanguage";
    private static final String KEY_LANGUAGE = "selected_language";
    private static final String DEFAULT_LANGUAGE = "ro";

    /**
     * Get the currently saved language code.
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    /**
     * Save the selected language code.
     */
    public static void setLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    /**
     * Apply the saved locale to the given context.
     * Call this in attachBaseContext of every Activity.
     */
    public static Context applyLocale(Context context) {
        String language = getLanguage(context);
        return updateResources(context, language);
    }

    /**
     * Update the resources configuration with the given language.
     */
    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);

        return context.createConfigurationContext(config);
    }

    /**
     * Check if the current language is Romanian.
     */
    public static boolean isRomanian(Context context) {
        return "ro".equals(getLanguage(context));
    }

    /**
     * Check if the current language is English.
     */
    public static boolean isEnglish(Context context) {
        return "en".equals(getLanguage(context));
    }

    /**
     * Get the language code to send to the chatbot backend.
     * The backend can use this to return responses in the correct language.
     */
    public static String getChatbotLanguage(Context context) {
        return getLanguage(context);
    }
}
