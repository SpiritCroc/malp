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

import org.gateshipone.malp.application.listviewitems.PartitionListItem;
import org.gateshipone.malp.application.listviewitems.ProfileListItem;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDServerProfile;

public class PartitionAdapter extends GenericSectionAdapter<MPDPartition> {
    private final Context mContext;

    public PartitionAdapter(Context context) {
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MPDPartition partition = (MPDPartition) getItem(position);

        // Profile name
        String partitionName = partition.getPartitionName();

        boolean checked = partition.getPartitionState();

        if (convertView != null) {
            PartitionListItem partitionListItem = (PartitionListItem) convertView;

            partitionListItem.setPartitionName(partitionName);
            partitionListItem.setChecked(checked);
        } else {
            convertView = new PartitionListItem(mContext, partitionName, checked);
        }

        return convertView;
    }

    public void setActive(int position, boolean active) {
        for (MPDPartition partition : mModelData) {
            partition.setPartitionState(false);
        }
        ((MPDPartition) getItem(position)).setPartitionState(active);
        notifyDataSetChanged();
    }
}
