package org.fitchfamily.android.gsmlocation;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.Nullable;

import org.microg.nlp.api.LocationBackendService;

import java.util.List;

import static org.fitchfamily.android.gsmlocation.LogUtils.makeLogTag;

public class GsmService extends LocationBackendService {
    private static final String TAG = makeLogTag("service");
    private static final boolean DEBUG = Config.DEBUG;
    private TelephonyHelper th;
    private TelephonyManager.CellInfoCallback cellInfoCallback = null;
    private TelephonyManager tm = null;
    private Location lastKnownLocation = null;
    private Boolean seenCellInfoChanged = false;

    final PhoneStateListener listener = new PhoneStateListener() {
        @Override
        public void onCellInfoChanged(List<android.telephony.CellInfo> cellInfo) {
            if (DEBUG) Log.d(TAG, "onCellInfoChanged(): " + cellInfo.toString());
            Location location = th.getLocationEstimate(cellInfo);
            if (location != null) lastKnownLocation = location;
            seenCellInfoChanged = true;
        }
    };

    @Override
    protected synchronized Location update() {
        if (DEBUG) Log.d(TAG, "update()");

        if (Build.VERSION.SDK_INT >= 29 && tm != null) {
            if (cellInfoCallback == null)
                cellInfoCallback = new TelephonyManager.CellInfoCallback() {
                    @Override
                    public void onCellInfo(@Nullable List<android.telephony.CellInfo> cellInfo) {
                        if (DEBUG) Log.v(TAG, "onCellInfo() callback!");
                    }

                    @Override
                    public void onError(int errorCode, Throwable detail) {
                        Log.e(TAG, "onError(): " + detail.getMessage());
                    }
                };
            tm.requestCellInfoUpdate(getMainExecutor(), cellInfoCallback);
        }

        if (!seenCellInfoChanged) {
            Location location = th.getLocationEstimate(null);
            if (location != null) lastKnownLocation = location;
        }

        if (lastKnownLocation != null) lastKnownLocation.setTime(System.currentTimeMillis());
        return (lastKnownLocation);
    }

    @Override
    protected synchronized void onOpen() {
        if (DEBUG) Log.d(TAG, "onOpen()");
        Context context = getApplicationContext();
        th = new TelephonyHelper(context);
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null && Build.VERSION.SDK_INT >= 29)
            tm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO);

    }

    protected synchronized void onClose() {
        if (DEBUG) Log.d(TAG, "onClose()");
        if (tm != null && Build.VERSION.SDK_INT >= 29)
            tm.listen(listener, PhoneStateListener.LISTEN_NONE);
        super.onClose();
    }
}
