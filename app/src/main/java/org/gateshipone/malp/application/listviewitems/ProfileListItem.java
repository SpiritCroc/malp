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
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import org.gateshipone.malp.R;

public class ProfileListItem extends LinearLayout {

    TextView mProfileNameView;

    TextView mHostnameAndPortView;

    RadioButton mRadioButton;

    public ProfileListItem(final Context context, final String profilename, final String hostname, final int port, final boolean checked) {
        super(context);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.listview_item_profile, this, true);

        mProfileNameView = findViewById(R.id.item_profile_name);
        mProfileNameView.setText(profilename);

        mHostnameAndPortView = findViewById(R.id.item_profile_hostname_port);
        mHostnameAndPortView.setText(getResources().getString(R.string.profile_host_port_template, hostname, port));

        mRadioButton = findViewById(R.id.item_profile_radiobtn);
        mRadioButton.setChecked(checked);
    }

    public void setProfileName(final String profilename) {
        mProfileNameView.setText(profilename);
    }

    public void setHostnameAndPort(final String hostname, final int port) {
        mHostnameAndPortView.setText(getResources().getString(R.string.profile_host_port_template, hostname, port));
    }

    public void setChecked(final boolean checked) {
        mRadioButton.setChecked(checked);
    }
}
