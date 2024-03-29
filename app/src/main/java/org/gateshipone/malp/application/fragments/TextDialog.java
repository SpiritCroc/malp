/*
 *  Copyright (C) 2023 Team Gateship-One
 *  (Hendrik Borghorst & Frederik Luetkes)
 *
 *  The AUTHORS.md file contains a detailed contributors list:
 *  <https://gitlab.com/gateship-one/malp/blob/master/AUTHORS.md>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.gateshipone.malp.application.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.callbacks.TextDialogCallback;


public class TextDialog extends DialogFragment {
    private static final String EXTRA_DIALOG_TITLE = "dialog_title";
    private static final String EXTRA_DIALOG_TEXT = "dialog_text";

    TextDialogCallback mSaveCallback;

    private String mTitle;
    private String mText;

    private boolean mFirstClick;

    public static TextDialog newInstance(final String dialogTitle, final String dialogText) {
        final Bundle args = new Bundle();
        args.putString(EXTRA_DIALOG_TITLE, dialogTitle);
        args.putString(EXTRA_DIALOG_TEXT, dialogText);

        TextDialog fragment = new TextDialog();
        fragment.setArguments(args);
        return fragment;
    }

    public void setCallback(TextDialogCallback callback) {
        mSaveCallback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        if (null != args) {
            mTitle = args.getString(EXTRA_DIALOG_TITLE);
            mText = args.getString(EXTRA_DIALOG_TEXT);
        }

        // Use the Builder class for convenient dialog construction
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());


        // create edit text for title
        final EditText editTextTitle = new EditText(builder.getContext());
        editTextTitle.setText(mText);

        // Add a listener that just removes the text on first clicking
        editTextTitle.setOnClickListener(v -> {
            if (!mFirstClick) {
                editTextTitle.setText("");
                mFirstClick = true;
            }
        });
        builder.setView(editTextTitle);

        builder.setMessage(mTitle).setPositiveButton(R.string.dialog_action_save, (dialog, id) -> {
            // accept title and call callback method
            String objectTitle = editTextTitle.getText().toString();
            mSaveCallback.onFinished(objectTitle);
        }).setNegativeButton(R.string.dialog_action_cancel, (dialog, id) -> {
            // User cancelled the dialog don't save object
            requireDialog().cancel();
        });
        // Create the AlertDialog object and return it
        return builder.create();
    }
}
