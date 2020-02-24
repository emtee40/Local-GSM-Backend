package org.fitchfamily.android.gsmlocation;

import android.content.Context;
import android.location.Location;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.microg.nlp.api.LocationBackendService;

import java.util.List;

import static org.fitchfamily.android.gsmlocation.LogUtils.makeLogTag;

public class GsmService extends LocationBackendService {
    private static final String TAG = makeLogTag("service");
    private static final boolean DEBUG = Config.DEBUG;
    private TelephonyHelper th;
    private TelephonyManager tm = null;
    private Context ctx;
    private Location lastKnownLocation = null;
    private Boolean cellInfoChanged = true;
    final PhoneStateListener listener = new PhoneStateListener() {
        public void onCellInfoChanged(List<android.telephony.CellInfo> cellInfo) {
            cellInfoChanged = true;
            if (DEBUG) Log.i(TAG, "onCellInfoChanged()");
        }
    };

    @Override
    protected synchronized Location update() {
        if (cellInfoChanged) {
            if (tm == null) {
                tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) tm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO);
            } else cellInfoChanged = false;
            Location rslt = th.getLocationEstimate();
            if (rslt != null) lastKnownLocation = rslt;
        }
        if (lastKnownLocation != null) lastKnownLocation.setTime(System.currentTimeMillis());
        return lastKnownLocation;
    }

    @Override
    protected synchronized void onOpen() {
        ctx = getApplicationContext();
        if (DEBUG) Log.i(TAG, "onOpen()");
        th = new TelephonyHelper(ctx);
    }

    protected synchronized void onClose() {
        if (DEBUG) Log.i(TAG, "onClose");
        super.onClose();
    }
}
