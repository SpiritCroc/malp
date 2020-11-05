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

package org.gateshipone.malp.application.fragments.serverfragments;

import android.view.ViewTreeObserver;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.GenericRecyclerViewAdapter;
import org.gateshipone.malp.application.utils.GridItemDecoration;
import org.gateshipone.malp.application.views.MalpRecyclerView;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;

import java.util.List;

abstract public class GenericMPDRecyclerFragment<T extends MPDGenericItem, VH extends RecyclerView.ViewHolder> extends BaseMPDFragment<T> {

    /**
     * The reference to the possible recyclerview
     */
    protected MalpRecyclerView mRecyclerView;

    /**
     * The generic adapter for the view model
     */
    protected GenericRecyclerViewAdapter<T, VH> mAdapter;

    @Override
    void swapModel(List<T> model) {
        // Transfer the data to the adapter so that the views can use it
        mAdapter.swapModel(model);
    }

    /**
     * Method to setup the recyclerview with a linear layout manager and a default item decoration.
     * Make sure to call this method after the recyclerview was set.
     */
    protected void setLinearLayoutManagerAndDecoration() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        final DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
        mRecyclerView.addItemDecoration(dividerItemDecoration);
    }

    /**
     * Method to setup the recyclerview with a grid layout manager and a spacing item decoration.
     * Make sure to call this method after the recyclerview was set and has
     * a valid {@link GenericRecyclerViewAdapter} adapter.
     * <p>
     * This method will also add an observer to adjust the spancount of the grid after an orientation change.
     */
    protected void setGridLayoutManagerAndDecoration() {
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));

        final int halfSpacingOffsetPX = getResources().getDimensionPixelSize(R.dimen.grid_half_spacing);
        final int spacingOffsetPX = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        final GridItemDecoration gridItemDecoration = new GridItemDecoration(spacingOffsetPX, halfSpacingOffsetPX);
        mRecyclerView.addItemDecoration(gridItemDecoration);

        // add an observer to set the spancount after the layout was inflated in order to get a dynamic spancount related to the available space.
        mRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final int recyclerViewWidth = mRecyclerView.getWidth();

                if (recyclerViewWidth > 0) {
                    // layout finished so remove observer
                    mRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    final float gridItemWidth = getResources().getDimensionPixelSize(R.dimen.grid_item_height);

                    // the minimum spancount should always be 2
                    final int newSpanCount = Math.max((int) Math.floor(recyclerViewWidth / gridItemWidth), 2);

                    final GridLayoutManager layoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
                    layoutManager.setSpanCount(newSpanCount);

                    mRecyclerView.requestLayout();

                    // pass the columnWidth to the adapter to adjust the size of the griditems
                    final int columnWidth = recyclerViewWidth / newSpanCount;
                    ((GenericRecyclerViewAdapter<?, ?>) mRecyclerView.getAdapter()).setItemSize(columnWidth);
                }
            }
        });
    }
}
