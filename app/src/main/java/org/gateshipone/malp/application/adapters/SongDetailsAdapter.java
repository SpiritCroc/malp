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
import android.widget.BaseAdapter;

import androidx.annotation.Nullable;

import org.gateshipone.malp.application.listviewitems.SongDetailsListItem;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.List;

public class SongDetailsAdapter extends BaseAdapter {

    private final Context mContext;

    private final List<SongDetailsItem> mItems;

    public SongDetailsAdapter(final Context context, final List<SongDetailsItem> items) {
        super();

        mContext = context;
        mItems = items;
    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public SongDetailsItem getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final SongDetailsItem item = getItem(position);

        final String key = item.getDisplayName();
        final String value = item.getDisplayValue();

        if (convertView != null) {
            final SongDetailsListItem songDetailsListItem = (SongDetailsListItem) convertView;

            songDetailsListItem.setKey(key);
            songDetailsListItem.setValue(value);
        } else {
            convertView = new SongDetailsListItem(mContext, key, value);
        }

        return convertView;
    }

    public static class SongDetailsItem {

        private final MPDTrack.StringTagTypes mTagType;

        private final String mDisplayName;

        private final String mDisplayValue;

        public SongDetailsItem(@Nullable final MPDTrack.StringTagTypes tagType, final String displayName, final String displayValue) {
            mTagType = tagType;
            mDisplayName = displayName;
            mDisplayValue = displayValue;
        }

        public SongDetailsItem(final String displayName, final String displayValue) {
            this(null, displayName, displayValue);
        }

        @Nullable
        public MPDTrack.StringTagTypes getTagType() {
            return mTagType;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getDisplayValue() {
            return mDisplayValue;
        }
    }
}
