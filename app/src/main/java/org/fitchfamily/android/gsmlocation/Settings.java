package org.fitchfamily.android.gsmlocation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import androidx.fragment.app.Fragment;

import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class Settings {
    private static final String DB_NAME = "lacells.db";
    private static final String DB_BAK_NAME = DB_NAME + ".bak";
    private static final String DB_NEW_NAME = DB_NAME + ".new";
    private static final String LOG_NAME = "lacells_gen.log";
    private static final String[] FILE_NAMES = new String[]{
            DB_NAME,
            DB_BAK_NAME,
            DB_NEW_NAME,
            LOG_NAME
    };

    private static final File DATABASE_DIRECTORY_OLD = new File(Environment.getExternalStorageDirectory(), ".nogapps");

    private static final boolean USE_LACELLS_DEFAULT = false;

    private static final String FIRST_RUN = "first_run";
    
    private static final String USE_LACELLS = "lacells_preference";

    private static final String USE_MOZILLA_LOCATION_SERVICE = "mls_preference";

    private static final String USE_OPEN_CELL_ID = "oci_preference";

    private static final String OPEN_CELL_ID_API_KEY = "oci_key_preference";

    private static final String OPEN_CELL_ID_CUSTOM_URL = "oci_url_preference";

    private static final String MOZILLA_CUSTOM_URL = "mozilla_url_preference";

    private static final String LACELLS_CUSTOM_URL = "lacells_url_preference";

    private static final String MNC_FILTER = "mnc_filter_preference";

    private static final String MCC_FILTER = "mcc_filter_preference";

    private static final String EXTERNAL_DATABASE_LOCATION = "ext_db_preference";

    private static final String CALCULATE_AREA_RANGE = "calculate_area_range";

    private static final String REMEMBER_LAST_KNOWN_LOCATION = "remember_last_known_location";
    
    private static final String FIXED_ACCURACY = "fixed_accuracy_preference";

    private static final Object lock = new Object();
    private static Settings instance;

    private final SharedPreferences preferences;
    private final Context context;
    
    private Settings(Context context) {
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        moveFilesToNewDirectory(FILE_NAMES);
    }

    private void moveFilesToNewDirectory(String... filenames) {
        for (String filename : filenames) {
            moveFileToNewDirectory(filename);
        }
    }

    private void moveFileToNewDirectory(String filename) {
        File oldFile = new File(DATABASE_DIRECTORY_OLD, filename);

        if (oldFile.exists() && oldFile.canWrite()) {
            /*
             * This will work because "/sdcard/.nogapps/" and
             * "/sdcard/Android/data/org.fitchfamily.android-gsmlocation/files" are on the
             * same mount point "/sdcard/" (The new directory is the external files directory, not
             * the internal one so that both are on the emulated sdcard (internal memory) or an real sdcard)
             */
            oldFile.renameTo(new File(databaseDirectory(), filename));
        }
    }

    public static Settings with(Fragment fragment) {
        return with(fragment.getContext());
    }

    public static Settings with(Context context) {
        if (context == null) {
            throw new NullPointerException();
        }

        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new Settings(context);
                }
            }
        }

        return instance;
    }
    
    public void initSettings() {
        boolean first_run = preferences.getBoolean(FIRST_RUN, true);
        
        if (!first_run) 
            return;

        preferences.edit().putBoolean(FIRST_RUN, false).apply();
        
        String s = preferences.getString(MCC_FILTER, "");
        if (s.isEmpty()) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                s = tm.getNetworkOperator();
                if (s != null && s.length() >= 3) {
                    s = s.substring(0,3);
                    preferences.edit().putString(MCC_FILTER, s).apply();
                }
            }
        }
    }

    public String mccFilters() {
        return preferences.getString(MCC_FILTER, "");
    }

    public String mncFilters() {
        return preferences.getString(MNC_FILTER, "");
    }

    public String fixedAccuracy() {
        return preferences.getString(FIXED_ACCURACY, "");
    }

    public String openCellIdApiKey() {
        return preferences.getString(OPEN_CELL_ID_API_KEY, "");
    }

    public boolean useLacells() {
        return preferences.getBoolean(USE_LACELLS, USE_LACELLS_DEFAULT);
    }

    public String getLacellsCustomURL() {
        return preferences.getString(LACELLS_CUSTOM_URL, "");
    }

    public boolean useOpenCellId() {
        return preferences.getBoolean(USE_OPEN_CELL_ID, false);
    }
    
    public String getOpenCellIdCustomURL() {
        return preferences.getString(OPEN_CELL_ID_CUSTOM_URL, "");
    }

    public String getMozillaCustomURL() {
        return preferences.getString(MOZILLA_CUSTOM_URL, "");
    }

    public boolean useMozillaLocationService() {
        return preferences.getBoolean(USE_MOZILLA_LOCATION_SERVICE, false);
    }
    
    public boolean calculateAreaRange() {
        return preferences.getBoolean(CALCULATE_AREA_RANGE, false);
    }
    
    public boolean rememberLastKnownLocation() {
        return preferences.getBoolean(REMEMBER_LAST_KNOWN_LOCATION, true);
    }


    public File databaseDirectory() {
        File extDir = new File(preferences.getString(EXTERNAL_DATABASE_LOCATION, ""));

        if (extDir.exists() && extDir.isDirectory() && extDir.canRead() && extDir.canWrite()) {
            return extDir;
        }
        return context.getExternalFilesDir(null);
    }

    public File newDatabaseFile() {
        return new File(databaseDirectory(), DB_NEW_NAME);
    }

    public File currentDatabaseFile() {
        return new File(databaseDirectory(), DB_NAME);
    }

    public File bakDatabaseFile() {
        return new File(databaseDirectory(), DB_BAK_NAME);
    }

    public File logfile() {
        return new File(databaseDirectory(), LOG_NAME);
    }

    /**
     * Use this function to get the current database file
     *
     * @return the current database file or null if not found
     */
    public File databaseFile() {
        if (newDatabaseFile().exists()) {
            return newDatabaseFile();
        } else if (currentDatabaseFile().exists()) {
            return currentDatabaseFile();
        } else {
            return null;
        }
    }

    /**
     * Use this function to get the time of the last update of the database
     *
     * @return unix timestamp in milliseconds or 0
     */
    public long databaseLastModified() {
        File databaseFile = databaseFile();
        return (databaseFile != null && databaseFile.canRead()) ? databaseFile.lastModified() : 0;
    }

    public Settings useLacells(boolean enable) {
        if (enable != useLacells()) {
            preferences.edit()
                    .putBoolean(USE_LACELLS, enable)
                    .commit();
        }

        return this;
    }

    public Settings getLacellsCustomURL(String key) {
        if (!TextUtils.equals(key, getLacellsCustomURL())) {
            preferences.edit()
                    .putString(LACELLS_CUSTOM_URL, key)
                    .commit();
        }

        return this;
    }

    public Settings useOpenCellId(boolean enable) {
        if (enable != useOpenCellId()) {
            preferences.edit()
                    .putBoolean(USE_OPEN_CELL_ID, enable)
                    .commit();
        }

        return this;
    }
    
    public Settings getOpenCellIdCustomURL(String key) {
        if (!TextUtils.equals(key, getOpenCellIdCustomURL())) {
            preferences.edit()
                    .putString(OPEN_CELL_ID_CUSTOM_URL, key)
                    .commit();
        }

        return this;
    }

    public Settings getMozillaCustomURL(String key) {
        if (!TextUtils.equals(key, getMozillaCustomURL())) {
            preferences.edit()
                    .putString(MOZILLA_CUSTOM_URL, key)
                    .commit();
        }

        return this;
    }

    public Settings useMozillaLocationService(boolean enable) {
        if (enable != useMozillaLocationService()) {
            preferences.edit()
                    .putBoolean(USE_MOZILLA_LOCATION_SERVICE, enable)
                    .commit();
        }

        return this;
    }
    
    public Settings calculateAreaRange(boolean enable) {
        if (enable != calculateAreaRange()) {
            preferences.edit()
                    .putBoolean(CALCULATE_AREA_RANGE, enable)
                    .commit();
        }

        return this;
    }
    
    public Settings rememberLastKnownLocation(boolean enable) {
        if (enable != rememberLastKnownLocation()) {
            preferences.edit()
                    .putBoolean(REMEMBER_LAST_KNOWN_LOCATION, enable)
                    .commit();
        }

        return this;
    }

    public Settings fixedAccuracy(String key) {
        if (!TextUtils.equals(key, fixedAccuracy())) {
            preferences.edit()
                    .putString(FIXED_ACCURACY, key)
                    .commit();
        }

        return this;
    }

    public Settings openCellIdApiKey(String key) {
        if (!TextUtils.equals(key, openCellIdApiKey())) {
            preferences.edit()
                    .putString(OPEN_CELL_ID_API_KEY, key)
                    .commit();
        }

        return this;
    }

    public Settings externalDatabaseLocation(String key) {
        if (!TextUtils.equals(key, openCellIdApiKey())) {
            preferences.edit()
                    .putString(EXTERNAL_DATABASE_LOCATION, key)
                    .commit();
        }

        return this;
    }

    /**
     * Use this function to get the entered MMC numbers.
     * If there is nothing chosen, an empty list is returned. This should be handled as all areas (when downloading).
     *
     * @return the selected MCC numbers
     */
    public Set<Integer> mccFilterSet() {
        Set<Integer> result = new HashSet<>();

        String mccList = mccFilters();

        if (!TextUtils.isEmpty(mccList)) {
            for (String number : mccList.split(",")) {
                try {
                    result.add(Integer.valueOf(number));
                } catch (NumberFormatException ex) {
                    // ignore
                }
            }
        }

        return Collections.unmodifiableSet(result);
    }

    public Settings mccFilterSet(Set<Integer> numbers) {
        preferences.edit()
                .putString(MCC_FILTER, TextUtils.join(",", numbers.toArray()))
                .commit();

        return this;
    }
}
