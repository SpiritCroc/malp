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

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.listviewitems.FileListItem;
import org.gateshipone.malp.application.listviewitems.GenericViewItemHolder;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class TracksRecyclerViewAdapter extends GenericRecyclerViewAdapter<MPDFileEntry, GenericViewItemHolder> {

    public TracksRecyclerViewAdapter() {
        super();
    }

    @NonNull
    @Override
    public GenericViewItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FileListItem view = new FileListItem(parent.getContext(), false, this);

        // set a selectable background manually
        view.setBackgroundResource(ThemeUtils.getThemeResourceId(parent.getContext(), R.attr.selectableItemBackground));
        return new GenericViewItemHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GenericViewItemHolder holder, int position) {
        final MPDTrack track = (MPDTrack) getItem(position);

        holder.setTrack(track);

        // We have to set this to make the context menu working with recycler views.
        holder.itemView.setLongClickable(true);
    }

    @Override
    public long getItemId(int position) {
        return ((MPDTrack) getItem(position)).getTrackId();
    }

    @Override
    public void setItemSize(int size) {
        // method only needed if adapter supports grid view
    }
}
