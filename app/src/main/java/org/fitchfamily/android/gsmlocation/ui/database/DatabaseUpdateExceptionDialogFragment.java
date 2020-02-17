package org.fitchfamily.android.gsmlocation.ui.database;

import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.fitchfamily.android.gsmlocation.R;

@EFragment
public class DatabaseUpdateExceptionDialogFragment extends DialogFragment {
    public static final String TAG = "DatabaseUpdateExceptionDialogFragment";

    @FragmentArg
    protected String log;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.fragment_update_database_exception_title)
                .setMessage(log)
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }
}
