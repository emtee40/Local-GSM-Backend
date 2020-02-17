package org.fitchfamily.android.gsmlocation.ui.settings.mcc;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AlertDialog;

import org.androidannotations.annotations.EFragment;
import org.androidannotations.annotations.FragmentArg;
import org.fitchfamily.android.gsmlocation.util.LocaleUtil;

import java.util.TreeSet;

@EFragment
public class AreaDialogFragment extends DialogFragment {
    public static final String TAG = "AreaDialogFragment";

    @FragmentArg
    protected TreeSet<Integer> numbers;

    @FragmentArg
    protected String code;

    private Listener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        listener = (Listener) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final String[] choices = new String[numbers.size()];
        final boolean[] checked = new boolean[choices.length];
        final int[] numbers = new int[choices.length];

        int i = 0;
        for (int number : this.numbers) {
            choices[i] = String.valueOf(number);
            checked[i] = listener.isMccEnabled(number);
            numbers[i] = number;

            i++;
        }

        return new AlertDialog.Builder(getActivity())
                .setTitle(LocaleUtil.getCountryName(code))
                .setMultiChoiceItems(choices, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        listener.setMccEnabled(numbers[which], isChecked);
                    }
                })
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, TAG);
    }

    public interface Listener {
        boolean isMccEnabled(int number);

        void setMccEnabled(int number, boolean enabled);
    }
}
