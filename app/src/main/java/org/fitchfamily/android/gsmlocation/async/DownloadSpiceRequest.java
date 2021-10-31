package org.fitchfamily.android.gsmlocation.async;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import com.octo.android.robospice.SpiceManager;
import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.SpiceRequest;
import com.octo.android.robospice.request.listener.RequestListener;
import com.octo.android.robospice.retry.DefaultRetryPolicy;

import org.fitchfamily.android.gsmlocation.Config;
import org.fitchfamily.android.gsmlocation.CsvParser;
import org.fitchfamily.android.gsmlocation.DatabaseCreator;
import org.fitchfamily.android.gsmlocation.LogUtils;
import org.fitchfamily.android.gsmlocation.R;
import org.fitchfamily.android.gsmlocation.Settings;
import org.fitchfamily.android.gsmlocation.data.Source;
import org.fitchfamily.android.gsmlocation.data.SourceConnection;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.fitchfamily.android.gsmlocation.LogUtils.makeLogTag;

/**
 * Background tasks gathers data from OpenCellId and/or Mozilla Location
 * services and produces a new database file in the name specified in the
 * Config class. We don't actually touch the file being used by
 * the actual tower lookup.
 *
 * If/when the tower lookup is called, it will check for the existence of
 * the new file and if so, close the file it is using, purge its caches,
 * and then move the old file to backup and the new file to active.
 */

public class DownloadSpiceRequest extends SpiceRequest<DownloadSpiceRequest.Result> {
    public static final int PROGRESS_MAX = 1000;

    public static final String CACHE_KEY = "DownloadSpiceRequest";

    private static final String TAG = makeLogTag(DownloadSpiceRequest.class);

    private static final boolean DEBUG = Config.DEBUG;

    private static final int TRANSACTION_SIZE_LIMIT = 1000;

    public static DownloadSpiceRequest lastInstance = null; // bad style, but there should never be more than one instance

    private final Context context;

    private final PowerManager.WakeLock wakeLock;

    private final WifiManager.WifiLock wifiLock;

    private boolean[] mccFilter = new boolean[1000];
    private boolean[] mncFilter = new boolean[1000];
    private DatabaseCreator databaseCreator;

    private StringBuffer logBuilder = new StringBuffer();

    private String lastProgressMessage;

    private int lastProgress;
    public DownloadSpiceRequest(Context context) {
        super(Result.class);
        this.context = context.getApplicationContext();
        wakeLock = ((PowerManager) this.context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, CACHE_KEY);
        wifiLock = ((WifiManager) this.context.getSystemService(Context.WIFI_SERVICE)).createWifiLock(CACHE_KEY);
        setRetryPolicy(new DefaultRetryPolicy(0, 0, 0));    // never retry automatically
    }

    public static void executeWith(Context context, SpiceManager spiceManager) {
        spiceManager.execute(new DownloadSpiceRequest(context.getApplicationContext()), DownloadSpiceRequest.CACHE_KEY, DurationInMillis.ALWAYS_EXPIRED, new RequestListener<Result>() {
            @Override
            public void onRequestFailure(SpiceException spiceException) {
                // ignore
            }

            @Override
            public void onRequestSuccess(DownloadSpiceRequest.Result result) {
                // ignore
            }
        });
    }

    /**
     * Use this function to get the download url
     *
     * @param context a context
     * @return a list of data urls based on the settings
     */
    private static List<Source> getSources(Context context) throws IOException {
        List<Source> sources = new ArrayList<>();

        if (Settings.with(context).useLacells()) {
            Map<Integer, MccDetails> mccMap = new HashMap<>();
            String lacellsUrlFmt;
            if (!Settings.with(context).getLacellsCustomURL().isEmpty()) {
                lacellsUrlFmt = Settings.with(context).getLacellsCustomURL();
            } else {
                lacellsUrlFmt = Config.LACELLS_MCC_URL;
            }

            try {
                final URI baseUrl = new URI(lacellsUrlFmt);

                SourceConnection connection = new Source(lacellsUrlFmt, Source.Compression.none).connect();
                CsvParser parser = new CsvParser(connection.inputStream());
                final List<String> header = parser.parseLine();

                final int index_mcc = header.indexOf("mcc");
                final int index_cells = header.indexOf("cells");
                final int index_urls = header.indexOf("urls");

                List<String> line;

                while ((line = parser.parseLine()) != null && line.size() > index_mcc) {
                    int cells = Integer.parseInt(line.get(index_cells));
                    int mcc = Integer.parseInt(line.get(index_mcc));
                    List<String> urls = new ArrayList<>();
                    for(String url : line.get(index_urls).split(" ")) {
                        urls.add(baseUrl.resolve(url).toString());
                    }

                    mccMap.put(mcc, new MccDetails(cells, urls));
                }
            } catch (Exception ex) {
                throw new IOException(ex);
            }

            Set<Integer> mccs = Settings.with(context).mccFilterSet();

            if (mccs.isEmpty()) {
                // get all supported
                mccs = mccMap.keySet();
            }

            for (int mcc : mccs) {
                MccDetails details = mccMap.get(mcc);

                if (details == null) {
                    throw new IOException("lacells does not contain " + mcc);
                }

                sources.add(new Source(details.urls(), Source.Compression.none, details.numberOfRecords()));
            }
        } else {
            // only use the other sources when lacells is not enabled

            if (Settings.with(context).useOpenCellId()) {
                String ociUrlFmt;
                if (!Settings.with(context).getOpenCellIdCustomURL().isEmpty())
                    ociUrlFmt = Settings.with(context).getOpenCellIdCustomURL();
                else
                    ociUrlFmt = Config.OCI_URL_FMT;
                    
                sources.add(new Source(String.format(Locale.US, ociUrlFmt, Settings.with(context).openCellIdApiKey()), Source.Compression.gzip));
            }

            if (Settings.with(context).useMozillaLocationService()) {
                String mozillaUrlFmt;
                if (!Settings.with(context).getMozillaCustomURL().isEmpty())
                    mozillaUrlFmt = Settings.with(context).getMozillaCustomURL();
                else
                    mozillaUrlFmt = Config.MLS_URL_FMT;

                SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                // Mozilla publishes new CSV files at a bit after the beginning of
                // a new day in GMT time. Get the time for a place a couple hours
                // west of Greenwich to allow time for the data to be posted.
                dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT-03"));
                sources.add(new Source(String.format(Locale.US, mozillaUrlFmt, dateFormatGmt.format(new Date())), Source.Compression.gzip));
            }
        }

        return Collections.unmodifiableList(sources);
    }

    private static int indexOf(List<String> data, String[] searched) {
        for (String search : searched) {
            final int index = data.indexOf(search);

            if (index != -1) {
                return index;
            }
        }

        return -1;
    }

    @Override
    public Result loadDataFromNetwork() throws Exception {
        lastInstance = this;

        final long startTime = System.currentTimeMillis();

        wakeLock.acquire();
        wifiLock.acquire();

        try {
            LogUtils.with(context).clearLog();

            // Prepare the MCC and MNC code filters.
            final String mccCodes = Settings.with(context).mccFilters();
            final String mncCodes = Settings.with(context).mncFilters();

            if (makeFilterArray(mccCodes, mccFilter)) {
                logInfo(context.getString(R.string.log_MCC_FILTER, mccCodes));
            } else {
                logInfo(context.getString(R.string.log_MCC_WORLD));
            }

            if (makeFilterArray(mncCodes, mncFilter)) {
                logInfo(context.getString(R.string.log_MNC_FILTER, mncCodes));
            } else {
                logInfo(context.getString(R.string.log_MNC_WORLD));
            }

            try {
                databaseCreator = DatabaseCreator.withTempFile(context).open().createTable();

                final List<Source> sources = getSources(context);
                final int sources_size = sources.size();
                final long expected_records = Source.expectedRecords(sources);
                final boolean progress_by_records_count = expected_records != Source.UNKNOWN;
                long processed_records = 0;

                for (int i = 0; i < sources_size; i++) {
                    final Source source = sources.get(i);

                    int progressStart, progressEnd;

                    if(progress_by_records_count) {
                        progressStart = (int) (processed_records * PROGRESS_MAX / expected_records);
                        processed_records += source.expectedRecords();
                        progressEnd = (int) (processed_records * PROGRESS_MAX / expected_records);
                    } else {
                        progressStart = i * PROGRESS_MAX / sources_size;
                        progressEnd = (i + 1) * PROGRESS_MAX / sources_size;
                    }

                    getData(source, progressStart, progressEnd);

                    if (isCancelled()) {
                        break;
                    }
                }

                if (!isCancelled()) {
                    publishProgress(PROGRESS_MAX, context.getString(R.string.log_INDICIES));

                    databaseCreator
                            .createIndex()
                            .close()
                            .removeJournal()
                            .replace(Settings.with(context).newDatabaseFile());

                } else {
                    databaseCreator.close().delete();
                }
            } catch (Exception ex) {
                logError(ex.getMessage());
                publishProgress(PROGRESS_MAX, ex.getMessage());

                // On any failure, remove the result file.
                if(databaseCreator != null) {
                    databaseCreator.close().delete();
                }

                throw ex;
            }
        } finally {
            wifiLock.release();
            wakeLock.release();

            final long exitTime = System.currentTimeMillis();
            final long execTime = exitTime - startTime;

            logInfo(context.getString(R.string.log_TOT_TIME, execTime));

            logInfo(context.getString(R.string.log_FINISHED));
        }

        return null;
    }

    /**
     * Turn comma-separated string with MCC/MNC codes into a boolean array for filtering.
     * @param codesStr Empty string or a string of comma-separated numbers.
     * @param outputArray 1000-element boolean array filled with false values. Elements with
     *                    indices corresponding to codes found in {@code codesStr} will be
     *                    changed to true.
     * @return True if the string contained at least one valid (0-999) code, false otherwise.
     */
    private boolean makeFilterArray(String codesStr, boolean[] outputArray) {
        if (codesStr.isEmpty()) {
            Arrays.fill(outputArray, Boolean.TRUE);
            return false;
        } else {
            int enabledCount = 0, code;
            for (String codeStr : codesStr.split(",")) {
                try {
                    code = Integer.parseInt(codeStr);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (code >= 0 && code <= 999) {
                    outputArray[code] = true;
                    enabledCount++;
                }
            }
            if (enabledCount == 0) {
                // The string contained only number(s) larger than
                // 999, only commas or some other surprise.
                Arrays.fill(outputArray, Boolean.TRUE);
                return false;
            }
        }

        return true;
    }

    private void getData(Source source, int progressStart, int progressEnd) throws Exception {
        // no risk, because progressStart + (x * 0) == progressStart
        if(progressStart > progressEnd) {
            throw new IllegalArgumentException(progressStart + " > " + progressEnd);
        }

        final long progressSize = progressEnd - progressStart;

        int totalRecords = 0;
        int insertedRecords = 0;
        long entryTime = System.currentTimeMillis();

        try {
            logInfo(context.getString(R.string.log_URL, source));

            SourceConnection connection = source.connect();

            logInfo(context.getString(R.string.log_CONT_LENGTH, String.valueOf(connection.getCompressedContentLength())));
            final long maxLength = connection.getContentLength();
            long maxProgress = 0;

            CsvParser cvs = new CsvParser(connection.inputStream());

            // CSV Field    ==> Database Field
            // radio        ==>
            // mcc          ==> mcc
            // net          ==> mnc
            // area         ==> lac
            // cell         ==> cid
            // unit         ==>
            // lon          ==> longitude
            // lat          ==> latitude
            // range        ==> accuracy
            // samples      ==> samples
            // changeable   ==>
            // created      ==>
            // updated      ==>
            // averageSignal==>
            List<String> headers = cvs.parseLine();
            int mccIndex = headers.indexOf("mcc");
            int mncIndex = indexOf(headers, new String[]{"net", "mnc"});
            int lacIndex = indexOf(headers, new String[]{"area", "lac"});
            int cidIndex = indexOf(headers, new String[]{"cell", "cid"});
            int lonIndex = indexOf(headers, new String[]{"lon", "longitude"});
            int latIndex = indexOf(headers, new String[]{"lat", "latitude"});
            int accIndex = indexOf(headers, new String[]{"range", "accuracy"});
            int smpIndex = headers.indexOf("samples");

            databaseCreator.beginTransaction();

            List<String> rec;

            while (((rec = cvs.parseLine()) != null) &&
                    (rec.size() > 8) &&
                    (!isCancelled())) {

                totalRecords++;

                if ((totalRecords % 1000) == 0) {
                    final String statusText = context.getString(R.string.log_REC_STATS, totalRecords, insertedRecords);
                    long progress;

                    if (source.expectedRecords() != Source.UNKNOWN) {
                        progress = Math.min(totalRecords, source.expectedRecords()) * progressSize / source.expectedRecords();
                    } else {
                        progress = ((((long) cvs.bytesRead()) * progressSize)) / maxLength;
                    }

                    // OpenCellId files seem to have the wrong length and our progress starts
                    // to go backwards. So only report the maximum positive progress we have
                    // achieved.
                    if (progress > maxProgress)
                        maxProgress = progress;
                    publishProgress(progressStart + (int) maxProgress, statusText);
                }

                int mcc = Integer.parseInt(rec.get(mccIndex));
                int mnc = Integer.parseInt(rec.get(mncIndex));

                if ((mcc >= 0) && (mcc <= 999) && mccFilter[mcc] &&
                        (mnc >= 0) && (mnc <= 999) && mncFilter[mnc]) {

                    // Keep transaction size limited
                    if ((insertedRecords % TRANSACTION_SIZE_LIMIT) == 0) {
                        databaseCreator
                                .commitTransaction()
                                .beginTransaction();
                    }

                    databaseCreator.insert(
                            mcc,
                            rec.get(mncIndex),
                            rec.get(lacIndex),
                            rec.get(cidIndex),
                            rec.get(lonIndex),
                            rec.get(latIndex),
                            rec.get(accIndex),
                            rec.get(smpIndex)
                    );

                    insertedRecords++;
                }
            }

            if (isCancelled()) {
                logWarn(context.getString(R.string.st_CANCELED));
            }

            databaseCreator.commitTransaction();

            logInfo(context.getString(R.string.log_REC_STATS, totalRecords, insertedRecords));

            long exitTime = System.currentTimeMillis();
            long execTime = exitTime - entryTime;

            float f = (Math.round((1000.0f * execTime) / Math.max(totalRecords, 1)) / 1000.0f);

            logInfo(context.getString(R.string.log_END_STATS, execTime, f));

        } catch (MalformedURLException e) {
            logError("getData('" + source + "') failed: " + e.getMessage());

            throw e;
        } catch (Exception e) {
            logError(e.getMessage());
            e.printStackTrace();

            // OpenCellId files seem to have wrong length. If we've read at least 10
            // million records, assume we've read all the data and exit normally.
            // Otherwise we will pass our exception up the line.
            if (totalRecords > 10000000) {
                databaseCreator.commitTransaction();
            } else {
                throw e;
            }
        }
    }

    private void publishProgress(int progress, String message) {
        lastProgress = progress;
        logProgress(progress / (PROGRESS_MAX / 100), message);
    }

    private void logInfo(String info) {
        logGeneral("info", info, false);

        if(DEBUG) {
            Log.i(TAG, info);
        }
    }

    private void logError(String error) {
        logGeneral("fail", error, false);

        if(DEBUG) {
            Log.e(TAG, error);
        }
    }

    private void logWarn(String warning) {
        logGeneral("warn", warning, false);

        if(DEBUG) {
            Log.w(TAG, warning);
        }
    }

    private void logProgress(int progress, String message) {
        logGeneral(String.format("%03d", progress) + "%", message, true);

        if(DEBUG) {
            Log.v(TAG, Integer.toString(progress) + "%  " + message);
        }
    }

    private void logGeneral(String tag, String message, boolean isProgress) {
        if(isProgress) {
            lastProgressMessage = '[' + tag + "]  " + message + "\n";
        } else {
            lastProgressMessage = null;

            logBuilder.append('[')
                    .append(tag)
                    .append("]  ")
                    .append(message)
                    .append('\n');
        }

        LogUtils.with(context).appendToLog(tag + ": " + message);

        publishProgress(lastProgress);
    }

    public String getLog() {
        String lastProgressMessage = this.lastProgressMessage;
        String log = this.logBuilder.toString();

        if(TextUtils.isEmpty(lastProgressMessage)) {
            return log;
        } else {
            return log + lastProgressMessage;
        }
    }

    public static final class Result {

    }
}
