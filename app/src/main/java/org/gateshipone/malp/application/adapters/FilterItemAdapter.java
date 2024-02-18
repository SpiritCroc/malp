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

package org.gateshipone.malp.application.adapters;


import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.gateshipone.malp.application.listviewitems.ProfileListItem;
import org.gateshipone.malp.application.listviewitems.SimpleListItem;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFilterObject;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDServerProfile;

public class FilterItemAdapter extends GenericSectionAdapter<MPDFilterObject> {
    private final Context mContext;

    public FilterItemAdapter(Context context) {
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MPDFilterObject filterObject = (MPDFilterObject) getItem(position);

        if (convertView != null) {
            SimpleListItem listItem = (SimpleListItem) convertView;

            listItem.setText(filterObject.getName());
        } else {
            convertView = new SimpleListItem(mContext, filterObject.getName());
        }

        return convertView;
    }

}
