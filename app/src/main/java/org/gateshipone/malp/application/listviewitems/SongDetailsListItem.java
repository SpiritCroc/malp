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
import android.widget.TextView;

import org.gateshipone.malp.R;

public class SongDetailsListItem extends LinearLayout {

    private final TextView mItemKeyView;

    private final TextView mItemValueView;

    public SongDetailsListItem(final Context context, final String key, final String value) {
        super(context);

        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.listview_item_song_details, this, true);

        mItemKeyView = findViewById(R.id.song_details_item_key);
        mItemKeyView.setText(key);

        mItemValueView = findViewById(R.id.song_details_item_value);
        mItemValueView.setText(value);
    }

    public void setKey(final String key) {
        mItemKeyView.setText(key);
    }

    public void setValue(final String value) {
        mItemValueView.setText(value);
    }
}
