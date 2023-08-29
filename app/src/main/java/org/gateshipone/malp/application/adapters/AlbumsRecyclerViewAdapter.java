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
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.listviewitems.AbsImageListViewItem;
import org.gateshipone.malp.application.listviewitems.FileListItem;
import org.gateshipone.malp.application.listviewitems.GenericGridItem;
import org.gateshipone.malp.application.listviewitems.GenericViewItemHolder;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;

public class AlbumsRecyclerViewAdapter extends GenericRecyclerViewAdapter<MPDAlbum, GenericViewItemHolder> implements ArtworkManager.onNewAlbumImageListener {

    private final boolean mUseList;

    private int mItemSize;

    private final ArtworkManager mArtworkManager;

    public AlbumsRecyclerViewAdapter(final Context context, final boolean useList) {
        super();

        mArtworkManager = ArtworkManager.getInstance(context.getApplicationContext());

        mUseList = useList;
        if (mUseList) {
            mItemSize = (int) context.getResources().getDimension(R.dimen.material_list_item_height);
        } else {
            mItemSize = (int) context.getResources().getDimension(R.dimen.grid_item_height);
        }
    }

    @NonNull
    @Override
    public GenericViewItemHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AbsImageListViewItem view;

        if (mUseList) {
            view = new FileListItem(parent.getContext(), false, this);

            // set a selectable background manually
            view.setBackgroundResource(ThemeUtils.getThemeResourceId(parent.getContext(), R.attr.selectableItemBackground));
        } else {
            view = new GenericGridItem(parent.getContext(), "", this);

            // apply custom layout params to ensure that the griditems have equal size
            ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mItemSize);
            view.setLayoutParams(layoutParams);
        }

        return new GenericViewItemHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GenericViewItemHolder holder, int position) {
        final MPDAlbum album = getItem(position);

        holder.setTitle(album.getName());

        if (!mUseList) {
            // for griditems adjust the height each time data is set
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            layoutParams.height = mItemSize;
            holder.itemView.setLayoutParams(layoutParams);
        }

        // This will prepare the view for fetching the image from the internet if not already saved in local database.
        holder.prepareArtworkFetching(mArtworkManager, album);

        // Check if the scroll speed currently is already 0, then start the image task right away.
        if (mScrollSpeed == 0) {
            holder.setImageDimensions(mItemSize, mItemSize);
            holder.startCoverImageTask();
        }

        // We have to set this to make the context menu working with recycler views.
        holder.itemView.setLongClickable(true);
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

    @Override
    public long getItemId(int position) {
        return getItem(position).getAlbumId();
    }

    @Override
    public void newAlbumImage(MPDAlbum album) {
        notifyDataSetChanged();
    }
}
