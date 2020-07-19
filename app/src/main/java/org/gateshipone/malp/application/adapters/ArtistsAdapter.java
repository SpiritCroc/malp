/*
 *  Copyright (C) 2020 Team Gateship-One
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

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.listviewitems.GenericGridItem;
import org.gateshipone.malp.application.listviewitems.ImageListItem;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

public class ArtistsAdapter extends GenericSectionAdapter<MPDArtist> implements ArtworkManager.onNewArtistImageListener {

    private final Context mContext;

    private boolean mUseList;

    private ArtworkManager mArtworkManager;

    /**
     * the size of the item in pixel
     * this will be used to adjust griditems and select a proper dimension for the image loading process
     */
    private int mItemSize;

    public ArtistsAdapter(final Context context, final boolean useList) {
        super();

        mContext = context;

        mUseList = useList;
        if (mUseList) {
            mItemSize = (int) context.getResources().getDimension(R.dimen.material_list_item_height);
        } else {
            mItemSize = (int) context.getResources().getDimension(R.dimen.grid_item_height);
        }
        mArtworkManager = ArtworkManager.getInstance(context.getApplicationContext());

    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MPDArtist artist = (MPDArtist) getItem(position);
        String label = artist.getArtistName();

        if (label.isEmpty()) {
            label = mContext.getResources().getString(R.string.no_artist_tag);
        }

        if (mUseList) {
            // Check if a view can be recycled
            ImageListItem listItem;
            if (convertView != null) {
                listItem = (ImageListItem) convertView;
                // Make sure to reset the layoutParams in case of change (rotation for example)
                listItem.setText(label);
            } else {
                // Create new view if no reusable is available
                listItem = new ImageListItem(mContext, label, null, this);
            }

            // This will prepare the view for fetching the image from the internet if not already saved in local database.
            listItem.prepareArtworkFetching(mArtworkManager, artist);
            // Check if the scroll speed currently is already 0, then start the image task right away.
            if (mScrollSpeed == 0) {
                listItem.setImageDimension(mItemSize, mItemSize);
                listItem.startCoverImageTask();
            }
            return listItem;
        } else {
            GenericGridItem gridItem;
            ViewGroup.LayoutParams layoutParams;

            // Check if a view can be recycled
            if (convertView != null) {
                gridItem = (GenericGridItem) convertView;
                gridItem.setTitle(label);
                layoutParams = gridItem.getLayoutParams();
                layoutParams.height = mItemSize;
            } else {
                // Create new view if no reusable is available
                gridItem = new GenericGridItem(mContext, label, this);
                layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mItemSize);
            }

            // Make sure to reset the layoutParams in case of change (rotation for example)
            gridItem.setLayoutParams(layoutParams);

            // This will prepare the view for fetching the image from the internet if not already saved in local database.
            gridItem.prepareArtworkFetching(mArtworkManager, artist);

            // Check if the scroll speed currently is already 0, then start the image task right away.
            if (mScrollSpeed == 0) {
                gridItem.setImageDimension(mItemSize, mItemSize);
                gridItem.startCoverImageTask();
            }
            return gridItem;
        }
    }

    @Override
    public void newArtistImage(MPDArtist artist) {
        notifyDataSetChanged();
    }

    /**
     * Sets the itemsize for each item.
     * This value will adjust the height of a griditem and will be used for image loading.
     * Calling this method will notify any registered observers that the data set has changed.
     *
     * @param size The new size in pixel.
     */
    @Override
    public void setItemSize(int size) {
        mItemSize = size;

        notifyDataSetChanged();
    }
}
