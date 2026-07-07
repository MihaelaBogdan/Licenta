package com.cityscape.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

public class LocaleHelper {

    private static final String PREF_NAME = "CityScapeLanguage";
    private static final String KEY_LANGUAGE = "selected_language";
    private static final String DEFAULT_LANGUAGE = "ro";

    
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    
    public static void setLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    
    public static Context applyLocale(Context context) {
        String language = getLanguage(context);
        return updateResources(context, language);
    }

    
    private static Context updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        config.setLocale(locale);

        return context.createConfigurationContext(config);
    }

    
    public static boolean isRomanian(Context context) {
        return "ro".equals(getLanguage(context));
    }

    
    public static boolean isEnglish(Context context) {
        return "en".equals(getLanguage(context));
    }

    
    public static String getChatbotLanguage(Context context) {
        return getLanguage(context);
    }
}
