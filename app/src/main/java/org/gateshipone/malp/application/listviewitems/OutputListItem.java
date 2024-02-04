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

package org.gateshipone.malp.application.listviewitems;


import android.content.Context;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.gateshipone.malp.R;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;

public class OutputListItem extends LinearLayout {

    CheckBox mCheckbox;
    TextView mMainTextview;
    TextView mSecondaryTextview;

    public OutputListItem(Context context, MPDOutput output){
        super(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.listview_item_output, this, true);

        mMainTextview = findViewById(R.id.item_text_primary);
        mSecondaryTextview = findViewById(R.id.item_text_secondary);
        mCheckbox = findViewById(R.id.item_output_checkbox);

        setOutput(output);
    }

    public void setOutput(MPDOutput output) {
        String partition = output.getPartitionName();
        mMainTextview.setText(output.getOutputName());
        if (partition != null && !partition.isEmpty()) {
            mSecondaryTextview.setText(output.getPartitionName());
        } else {
            mSecondaryTextview.setVisibility(GONE);
        }
        mCheckbox.setChecked(output.getOutputState());
    }

}
