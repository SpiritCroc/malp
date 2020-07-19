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
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ListAdapter;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.GenericSectionAdapter;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;

import java.util.List;

public abstract class GenericMPDFragment<T extends MPDGenericItem> extends BaseMPDFragment<T> {
    private static final String TAG = GenericMPDFragment.class.getSimpleName();

    /**
     * The reference to the possible abstract list view
     */
    protected AbsListView mListView;

    /**
     * The generic adapter for the view model
     */
    protected GenericSectionAdapter<T> mAdapter;

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // clear view references because the fragment itself won't take care of it
        mListView = null;
        mAdapter = null;
    }

    @Override
    void swapModel(List<T> model) {
        mAdapter.swapModel(model);
    }

    /**
     * Method to apply a filter to the view model of the fragment.
     * <p/>
     * This method must be overridden by the subclass.
     */
    public void applyFilter(String filter) {
        throw new IllegalStateException("filterView hasn't been implemented in the subclass");
    }

    /**
     * Method to remove a previous set filter.
     * <p/>
     * This method must be overridden by the subclass.
     */
    public void removeFilter() {
        throw new IllegalStateException("removeFilter hasn't been implemented in the subclass");
    }

    /**
     * This method will add an observer to adjust the spancount of the grid after an orientation change.
     * <p>
     * You should only call this method if the mListView was initialized as a {@link GridView} and has a valid adapter.
     */
    protected void observeGridLayoutSize() {
        mListView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final int listViewWidth = mListView.getWidth();

                if (listViewWidth > 0) {
                    // layout finished so remove observer
                    mListView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    final float gridItemWidth = getResources().getDimensionPixelSize(R.dimen.grid_item_height);

                    // the minimum spancount should always be 2
                    final int newSpanCount = Math.max((int) Math.floor(listViewWidth / gridItemWidth), 2);

                    if (mListView instanceof GridView) {
                        ((GridView) mListView).setNumColumns(newSpanCount);
                    }

                    mListView.requestLayout();

                    // pass the columnWidth to the adapter to adjust the size of the griditems
                    final int columnWidth = listViewWidth / newSpanCount;
                    final ListAdapter adapter = mListView.getAdapter();

                    if (adapter != null) {
                        ((GenericSectionAdapter<?>) adapter).setItemSize(columnWidth);
                    }
                }
            }
        });
    }
}
