package org.fitchfamily.android.gsmlocation;

import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.androidannotations.annotations.EService;
import org.microg.nlp.api.LocationBackendService;

import java.util.List;

import static org.fitchfamily.android.gsmlocation.LogUtils.makeLogTag;

@EService
public class GsmService extends LocationBackendService {
    private static final String TAG = makeLogTag("service");
    private static final boolean DEBUG = Config.DEBUG;
    private TelephonyManager tm;
    private TelephonyHelper th;
    protected Thread worker = null;
    private Thread thread;
    private Context ctx = null;
    private Location lastKnownLocation = null;
    private Boolean cellInfoChanged = true;

    @Override
    protected synchronized Location update() {
        if (cellInfoChanged) scanGsm("update");
        else {
            lastKnownLocation.setTime(System.currentTimeMillis());
            return lastKnownLocation;
        }
        return null;
    }

    @Override
    protected synchronized void onOpen() {
        if (DEBUG) Log.i(TAG, "Binder OPEN called");
        ctx = getApplicationContext();
        setServiceRunning(true);
    }

    protected synchronized void onClose() {
        if (DEBUG) Log.i(TAG, "Binder CLOSE called");
        super.onClose();
        setServiceRunning(false);
    }

    private void setServiceRunning(boolean st) {
        final boolean cur_st = (worker != null);

        if (cur_st == st)
            return;

        if (st) {
            tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            th = new TelephonyHelper(ctx);

            try {
                if (worker != null && worker.isAlive()) worker.interrupt();

                worker = new Thread() {

                    public void run() {
                        if (DEBUG) Log.i(TAG, "Starting reporter thread");
                        Looper.prepare();

                        final PhoneStateListener listener = new PhoneStateListener() {
                            public void onCellInfoChanged(List<android.telephony.CellInfo> cellInfo) {
                                cellInfoChanged = true;
                            }

                            public void onSignalStrengthsChanged(android.telephony.SignalStrength signalStrength) {
                                cellInfoChanged = true;
                            }
                        };
                        tm.listen(listener,
                                PhoneStateListener.LISTEN_CELL_INFO |
                                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        );
                        Looper.loop();
                    }
                };
                worker.start();
            } catch (Exception e) {
                if (DEBUG) Log.e(TAG, "Start failed: " + e.getMessage());
                e.printStackTrace();
                worker = null;
            }
        } else {
            try {
                if (worker != null && worker.isAlive())
                    worker.interrupt();

                if (worker != null)
                    worker = null;

            } finally {
                worker = null;
            }
        }
    }

    public synchronized void scanGsm(final String calledFrom) {
        if (thread != null) {
            if (DEBUG)
                Log.i(TAG, "scanGsm() : Thread busy?!");
            return;
        }

        if (th != null) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    cellInfoChanged = false;
                    Location rslt = th.getLocationEstimate();
                    String logString;
                    if (rslt != null) {
                        rslt.setTime(System.currentTimeMillis());
                        lastKnownLocation = rslt;
                        logString = "scanGsm(" + calledFrom + ") " + rslt.toString();
                        report(rslt);
                    } else
                        logString = "scanGsm(" + calledFrom + ")  null position";
                    if (DEBUG) Log.i(TAG, logString);
                    //report(rslt);
                    thread = null;
                }
            });
            thread.start();
        } else if (DEBUG)
            Log.i(TAG, "Telephony helper is null?!?");
    }
}
