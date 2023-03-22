package org.fitchfamily.android.gsmlocation.ui.settings;

import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.octo.android.robospice.persistence.DurationInMillis;
import com.octo.android.robospice.persistence.exception.SpiceException;
import com.octo.android.robospice.request.listener.PendingRequestListener;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.CheckedChange;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;
import org.fitchfamily.android.gsmlocation.R;
import org.fitchfamily.android.gsmlocation.Settings;
import org.fitchfamily.android.gsmlocation.async.DownloadSpiceRequest;
import org.fitchfamily.android.gsmlocation.data.MobileCountryCodes;
import org.fitchfamily.android.gsmlocation.ui.base.BaseFragment;
import org.fitchfamily.android.gsmlocation.ui.settings.mcc.AreaListActivity_;
import org.fitchfamily.android.gsmlocation.util.LocaleUtil;

import java.util.Set;

@EFragment(R.layout.fragment_settings)
public class SettingsFragment extends BaseFragment {
    private static final int PAGE_BLOCKED = 0;

    private static final int PAGE_SETTINGS = 1;

    private static final int REQUEST_EDIT_AREAS = 1;

    @ViewById
    protected ViewSwitcher switcher;

    private final PendingRequestListener<DownloadSpiceRequest.Result> databaseListener = new PendingRequestListener<DownloadSpiceRequest.Result>() {
        @Override
        public void onRequestNotFound() {
            switcher.setDisplayedChild(PAGE_SETTINGS);
        }

        @Override
        public void onRequestFailure(SpiceException spiceException) {
            switcher.setDisplayedChild(PAGE_SETTINGS);
        }

        @Override
        public void onRequestSuccess(DownloadSpiceRequest.Result result) {
            switcher.setDisplayedChild(PAGE_SETTINGS);
        }
    };

    @ViewById
    protected CheckBox openCellId;

    @ViewById
    protected CheckBox mozillaLocationServices;
    
    @ViewById
    protected CheckBox calculateAreaRange;

    @ViewById
    protected CheckBox rememberLastKnownLocation;

    @ViewById
    protected RadioButton filterOnPhone;

    @ViewById
    protected RadioButton filterRemote;

    @ViewById
    protected TextView areaList;

    @Override
    public void onStart() {
        super.onStart();
        switcher.setDisplayedChild(PAGE_BLOCKED);
        getSpiceManager().addListenerIfPending(DownloadSpiceRequest.Result.class, DownloadSpiceRequest.CACHE_KEY, databaseListener);
    }

    @AfterViews
    protected void init() {
        openCellId.setChecked(Settings.with(this).useOpenCellId());
        mozillaLocationServices.setChecked(Settings.with(this).useMozillaLocationService());
        calculateAreaRange.setChecked(Settings.with(this).calculateAreaRange());
        rememberLastKnownLocation.setChecked(Settings.with(this).rememberLastKnownLocation());

        if(Settings.with(this).useLacells()) {
            filterRemote.setChecked(true);
        } else {
            filterOnPhone.setChecked(true);
        }

        updateSourcesVisibility();
    }

    private void updateSourcesVisibility() {
        openCellId.setVisibility(filterOnPhone.isChecked() ? View.VISIBLE : View.GONE);
        mozillaLocationServices.setVisibility(filterOnPhone.isChecked() ? View.VISIBLE : View.GONE);
    }

    @CheckedChange
    protected void openCellId(boolean checked) {
        if (checked && TextUtils.isEmpty(Settings.with(this).openCellIdApiKey())) {
            openCellId.setChecked(false);
            OpenCellIdExceptionDialogFragment_.builder()
                    .reason(OpenCellIdExceptionDialogFragment.Reason.no_token)
                    .build()
                    .show(getFragmentManager());
                    
        } else {
            Settings.with(this).useOpenCellId(checked);
        }
    }

    @CheckedChange
    protected void mozillaLocationServices(boolean checked) {
        Settings.with(this).useMozillaLocationService(checked);
    }
    
    @CheckedChange
    protected void calculateAreaRange(boolean checked) {
        Settings.with(this).calculateAreaRange(checked);
    }

    @CheckedChange
    protected void rememberLastKnownLocation(boolean checked) {
        Settings.with(this).rememberLastKnownLocation(checked);
    }

    @CheckedChange
    protected void filterRemote(boolean enabled) {
        Settings.with(this).useLacells(enabled);
        updateSourcesVisibility();
    }

    @CheckedChange
    protected void filterOnPhone(boolean enabled) {
        filterRemote(!enabled);
    }

    @Click
    protected void editAreas() {
        AreaListActivity_.intent(this).startForResult(REQUEST_EDIT_AREAS);
    }

    @AfterViews
    @OnActivityResult(REQUEST_EDIT_AREAS)
    protected void updateAreaView() {
        final Set<Integer> set = Settings.with(this).mccFilterSet();

        if (set.isEmpty()) {
            areaList.setText(R.string.fragment_settings_card_areas_nothing_chosen);
        } else {
            final StringBuilder builder = new StringBuilder();
            final MobileCountryCodes.Regions regions = MobileCountryCodes.with(getContext()).getAreas(set);

            for (String area : LocaleUtil.getCountryNames(regions.areas())) {
                builder.append(area).append('\n');
            }

            if (regions.containsUnresolved()) {
                builder.append(getString(R.string.fragment_settings_card_areas_other)).append('\n');
            }

            String text = builder.toString();

            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }

            areaList.setText(text);
        }
    }
}
