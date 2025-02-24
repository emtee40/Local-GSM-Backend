package org.fitchfamily.android.gsmlocation;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import org.fitchfamily.android.gsmlocation.database.CellLocationDatabase;
import org.fitchfamily.android.gsmlocation.Settings;
import org.microg.nlp.api.LocationHelper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.fitchfamily.android.gsmlocation.LogUtils.makeLogTag;

class TelephonyHelper {
    private static final String TAG = makeLogTag(TelephonyHelper.class);
    private static final boolean DEBUG = Config.DEBUG;
    private TelephonyManager tm;
    private CellLocationDatabase db;
    private long lastTimeStamp = 0;
    private final Context context;
    private Method getNeighboringCellInfoMethod;
    private boolean hasGetNeighboringCellInfo = false;

    TelephonyHelper(Context context) {
        this.context = context.getApplicationContext();
        if (Build.VERSION.SDK_INT <= 28) {
            loadGetNeighboringClassMethod();
        }
        tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        db = new CellLocationDatabase(context);
    }

    private int toInteger(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double calcRange(double dBm) {
        final double defaultRange = 5000;
        // https://wiki.teltonika-networks.com/view/Mobile_Signal_Strength_Recommendations
        if (dBm == 0) return defaultRange;
        double range = defaultRange * Math.pow(10d, ((double) -100 - dBm) / 20);
        if (range > 2 * defaultRange) return 2 * defaultRange;
        if (range < 100) return 100;
        return range;
    }
    
    private void loadGetNeighboringClassMethod() {
        try {
            Class<?> tmClass = Class.forName("android.telephony.TelephonyManager");
            getNeighboringCellInfoMethod = tmClass.getMethod("getNeighboringCellInfo");
        } catch (final ClassNotFoundException e) {
            if (DEBUG)
                Log.d(TAG, "ClassNotFoundException: Cannot get getNeighboringCellInfo method");
            hasGetNeighboringCellInfo = false;
            return;
        } catch (final NoSuchMethodException e) {
            if (DEBUG)
                Log.d(TAG, "NoSuchMethodException: Cannot get getNeighboringCellInfo method");
            hasGetNeighboringCellInfo = false;
            return;
        }
        if (DEBUG)
            Log.d(TAG, "hasGetNeighboringCellInfo");
        hasGetNeighboringCellInfo = true;
    }

    @Nullable
    private synchronized List<Location> getTowerLocations(List<android.telephony.CellInfo> allCells) {

        if (tm == null) return null;

        //if (DEBUG) Log.d(TAG, "getAllCellInfo(): " + allCells.toString());

        if ((allCells == null) || allCells.isEmpty()) {
            Log.i(TAG, "getAllCellInfo() returned null or empty set");
            return null;
        }

        List<Location> rslt = new ArrayList<Location>();

        for (android.telephony.CellInfo inputCellInfo : allCells) {
            Location cellLocation = null;
            int mcc = -1;
            int mnc = -1;
            int cid = -1;
            int lac = -1;
            double range = 0;
            double dBm;
            String tech = "";

            if (inputCellInfo instanceof CellInfoLte) {
                tech = "LTE";
                CellInfoLte info = (CellInfoLte) inputCellInfo;
                dBm = info.getCellSignalStrength().getDbm();
                range = calcRange(dBm);
                CellIdentityLte id = info.getCellIdentity();
                if (Build.VERSION.SDK_INT >= 28) {
                    mcc = toInteger(id.getMccString());
                    mnc = toInteger(id.getMncString());
                } else {
                    mcc = id.getMcc();
                    mnc = id.getMnc();
                }
                cid = id.getCi();
                lac = id.getTac();
            } else if (inputCellInfo instanceof CellInfoGsm) {
                tech = "GSM";
                CellInfoGsm info = (CellInfoGsm) inputCellInfo;
                dBm = info.getCellSignalStrength().getDbm();
                range = calcRange(dBm);
                CellIdentityGsm id = info.getCellIdentity();
                if (Build.VERSION.SDK_INT >= 28) {
                    mcc = toInteger(id.getMccString());
                    mnc = toInteger(id.getMncString());
                } else {
                    mcc = id.getMcc();
                    mnc = id.getMnc();
                }
                cid = id.getCid();
                lac = id.getLac();
            } else if (Build.VERSION.SDK_INT >= 18 && inputCellInfo instanceof CellInfoWcdma) {
                tech = "WCDMA";
                CellInfoWcdma info = (CellInfoWcdma) inputCellInfo;
                dBm = info.getCellSignalStrength().getDbm();
                range = calcRange(dBm);
                CellIdentityWcdma id = info.getCellIdentity();
                if (Build.VERSION.SDK_INT >= 28) {
                    mcc = toInteger(id.getMccString());
                    mnc = toInteger(id.getMncString());
                } else {
                    mcc = id.getMcc();
                    mnc = id.getMnc();
                }
                cid = id.getCid();
                lac = id.getLac();
            } else if (Build.VERSION.SDK_INT >= 29 && inputCellInfo instanceof CellInfoNr) {
                tech = "5G";
                CellInfoNr info = (CellInfoNr) inputCellInfo;
                dBm = info.getCellSignalStrength().getDbm();
                range = calcRange(dBm);
                CellIdentityNr id = (CellIdentityNr) ((CellInfoNr) inputCellInfo).getCellIdentity();
                mcc = toInteger(id.getMccString());
                mnc = toInteger(id.getMncString());
                cid = id.getPci();
                lac = id.getTac();
            } else if (Build.VERSION.SDK_INT >= 29 && inputCellInfo instanceof CellInfoTdscdma) {
                tech = "TDSCDMA";
                CellInfoTdscdma info = (CellInfoTdscdma) inputCellInfo;
                dBm = info.getCellSignalStrength().getDbm();
                range = calcRange(dBm);
                CellIdentityTdscdma id = info.getCellIdentity();
                mcc = toInteger(id.getMccString());
                mnc = toInteger(id.getMncString());
                cid = id.getCid();
                lac = id.getLac();
            } else if (inputCellInfo instanceof CellInfoCdma) {
                tech = "CDMA";
                CellInfoCdma info = (CellInfoCdma) inputCellInfo;
                dBm = info.getCellSignalStrength().getDbm();
                range = calcRange(dBm);
                CellIdentityCdma id = ((CellInfoCdma) inputCellInfo).getCellIdentity();
                if (id.getLatitude() != CellInfo.UNAVAILABLE) {
                    cellLocation = new Location("CDMA");
                    cellLocation.setLatitude(id.getLatitude() * 0.25 / 3600);
                    cellLocation.setLongitude(id.getLongitude() * 0.25 / 3600);
                    cellLocation.setAccuracy((float) range);
                    cellLocation.setTime(inputCellInfo.getTimeStamp() / 1000000);
                    rslt.add(cellLocation);
                    continue;
                } else {
                    ServiceState state = new ServiceState();
                    // https://groups.google.com/forum/#!msg/android-developers/ASVpv7RbLL8/hRYtSerT3RAJ
                    String s = state.getOperatorNumeric();
                    if (s != null && s.length() >= 3) {
                        mcc = toInteger(state.getOperatorNumeric().substring(0, 3));
                        mnc = id.getSystemId();
                        cid = id.getBasestationId();
                        lac = id.getNetworkId();
                    }
                }
            } else continue;

            if (mcc <= 0) continue;

            // Uncomment to simulate 0-prefix MNC bug
            //if (DEBUG) mnc = -1;

            Log.i(TAG, "CellInfo: " + tech + " MCC=" + mcc + " MNC="
                    + mnc + " CID=" + cid + " LAC=" + lac + " dBm=" + Math.round(dBm));
            if (cid == CellInfo.UNAVAILABLE || lac == CellInfo.UNAVAILABLE
                    || mcc < 200 || mcc == 999) continue;


            // Workaround for 0-prefix MNC bug
            if (mnc < 0 && inputCellInfo.isRegistered()) {
                String s = tm.getNetworkOperator();
                if (s != null && s.length() >= 3) {
                    mnc = toInteger(tm.getNetworkOperator().substring(3));
                    Log.w(TAG, "Fixed MNC=" + mnc);
                }
            }

            cellLocation = db.query(mcc, mnc, cid, lac);

            if ((cellLocation != null)) {
                String fixedAccuracyValue = Settings.with(context).fixedAccuracy();
                
                if (!TextUtils.isEmpty(fixedAccuracyValue)) {
                    range = Integer.parseInt(fixedAccuracyValue);
                    cellLocation.setAccuracy((float) range);
                }
                else if (Settings.with(context).calculateAreaRange() && 
                    cellLocation.getAccuracy() > range) {
                    cellLocation.setAccuracy((float) range);
                }

                rslt.add(cellLocation);
            }

        }
        if (rslt.isEmpty()) return null;
        return rslt;
    }
    
    public synchronized List<Location> legacyGetCellTowers() {
        if (tm == null)
            return null;

        List<Location> rslt = new ArrayList<Location>();
        String mncString = tm.getNetworkOperator();

        if ((mncString == null) || (mncString.length() < 5) || (mncString.length() > 6)) {
            if (DEBUG)
                Log.i(TAG, "legacyGetCellTowers(): mncString is NULL or not recognized.");
            return null;
        }
        int mcc = 0;
        int mnc = 0;
        try {
            mcc = Integer.parseInt(mncString.substring(0, 3));
            mnc = Integer.parseInt(mncString.substring(3));
        } catch (NumberFormatException e) {
            if (DEBUG)
                Log.i(TAG, "legacyGetCellTowers(), Unable to parse mncString: " + e.toString());
            return null;
        }
        final CellLocation cellLocation = tm.getCellLocation();

        if ((cellLocation != null) && (cellLocation instanceof GsmCellLocation)) {
            GsmCellLocation cell = (GsmCellLocation) cellLocation;
            Location cellLocInfo = db.query(mcc, mnc, cell.getCid(), cell.getLac());
            if (cellLocInfo != null)
                rslt.add(cellLocInfo);
            else if (DEBUG)
                Log.i(TAG, "Unknown cell tower detected: mcc="+mcc+
                        ", mnc="+mnc+", cid="+cell.getCid()+", lac="+cell.getLac());
        } else {
            if (DEBUG)
                Log.i(TAG, "getCellLocation() returned null or no GsmCellLocation.");
        }

        if (hasGetNeighboringCellInfo) {
            try {
                final List<NeighboringCellInfo> neighbours = (List<NeighboringCellInfo>)getNeighboringCellInfoMethod.invoke(tm);
                if ((neighbours != null) && !neighbours.isEmpty()) {
                    for (NeighboringCellInfo neighbour : neighbours) {
                        Location cellLocInfo = db.query(mcc, mnc, neighbour.getCid(), neighbour.getLac());
                        if (cellLocInfo != null) {
                            rslt.add(cellLocInfo);
                        }
                    }
                } else {
                    if (DEBUG) Log.i(TAG, "getNeighboringCellInfo() returned null or empty set.");
                }
            } catch (IllegalAccessException e) {
                if (DEBUG) Log.i(TAG, "IllegalAccessException for getNeighboringCellInfoMethod");
            } catch (InvocationTargetException e) {
                if (DEBUG) Log.i(TAG, "InvocationTargetException for getNeighboringCellInfoMethod");
            } catch (NoSuchMethodError e) {
                if (DEBUG) Log.i(TAG, "no such method: getNeighboringCellInfo().");
            }
        }
        if (rslt.isEmpty() && DEBUG) {
            Log.i(TAG, "No known cell towers found.");
        }
        if (rslt.isEmpty())
            return null;
        return rslt;
    }

    private Location weightedAverage(Collection<Location> locations) {
        Location rslt;

        if (locations == null || locations.size() == 0) {
            return null;
        }

        int num = locations.size();
        int totalWeight = 0;
        double latitude = 0;
        double longitude = 0;
        float accuracy = 0;
        int altitudes = 0;
        double altitude = 0;

        for (Location value : locations) {
            if (value != null) {
                // Create weight value based on accuracy. Higher accuracy
                // (lower tower radius/range) towers get higher weight.
                float thisAcc = value.getAccuracy();
                if (thisAcc < 1f)
                    thisAcc = 1f;

                int wgt = (int) (100000f / thisAcc);
                if (wgt < 1)
                    wgt = 1;

                latitude += (value.getLatitude() * wgt);
                longitude += (value.getLongitude() * wgt);
                accuracy += (value.getAccuracy() * wgt);
                totalWeight += wgt;

//                if (DEBUG) Log.i(TAG, "(lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy + ") / wgt=" + totalWeight );

                if (value.hasAltitude()) {
                    altitude += value.getAltitude();
                    altitudes++;
                }
            }
        }
        latitude = latitude / totalWeight;
        longitude = longitude / totalWeight;
        accuracy = accuracy / totalWeight;
        altitude = altitude / altitudes;
        Bundle extras = new Bundle();
        extras.putInt("AVERAGED_OF", num);
//        if (DEBUG) Log.i(TAG, "Location est (lat="+ latitude + ", lng=" + longitude + ", acc=" + accuracy);

        if (altitudes > 0) {
            rslt = LocationHelper.create("GSM",
                    latitude,
                    longitude,
                    altitude,
                    accuracy,
                    extras);
        } else {
            rslt = LocationHelper.create("GSM",
                    latitude,
                    longitude,
                    accuracy,
                    extras);
        }
        return rslt;
    }

    synchronized Location getLocationEstimate(List<android.telephony.CellInfo> allCells) {
        if (tm == null) 
            return null;
            
        db.checkForNewDatabase();
        
        if (allCells == null) try {
            allCells = tm.getAllCellInfo();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException: " + e.getMessage());
        }
        
        List<Location> rslt = null;

        if ((allCells != null) && !allCells.isEmpty()) {
            long l = allCells.get(0).getTimeStamp();
            if (l == lastTimeStamp) {
                Log.d(TAG, "Same timestamp: " + Long.toString(l));
                return null;
            }
            Log.d(TAG, "New timestamp: " + Long.toString(l));
            lastTimeStamp = l;
            
            rslt = getTowerLocations(allCells);
        }
        
        if (rslt == null || rslt.isEmpty()) {
            if (DEBUG)
                Log.i(TAG, "getTowerLocations() returned nothing, trying legacyGetCellTowers().");
            rslt = legacyGetCellTowers();
        }
        
        if ((rslt == null) || rslt.isEmpty()) {
            if (DEBUG) 
                Log.i(TAG, "getTowerLocations(): No tower information.");
            return null;
        }
            
        return weightedAverage(rslt);
    }
}


