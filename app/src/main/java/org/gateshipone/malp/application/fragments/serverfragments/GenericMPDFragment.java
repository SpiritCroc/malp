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

package org.gateshipone.malp.application.fragments.serverfragments;


import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.gateshipone.malp.application.adapters.GenericSectionAdapter;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;

import java.util.List;

public abstract class GenericMPDFragment<T extends MPDGenericItem> extends BaseMPDFragment {
    private static final String TAG = GenericMPDFragment.class.getSimpleName();

    /**
     * The generic adapter for the view model
     */
    protected GenericSectionAdapter<T> mAdapter;

    /**
     * Holds if data is ready of has to be refetched (e.g. after memory trimming)
     */
    private boolean mDataReady;

    abstract GenericViewModel<T> getViewModel();

    void swapModel(List<T> model) {
        if (mAdapter == null) {
            return;
        }
        mAdapter.swapModel(model);
    }

    /**
     * Method to reload the data and start the refresh indicator if a refreshlayout exists.
     */
    public void refreshContent() {
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(true));
        }

        mDataReady = false;

        getViewModel().reloadData();
    }

    public void getContent() {
        // Check if data was fetched already or not (or removed because of trimming)
        if (!mDataReady) {
            if (mSwipeRefreshLayout != null) {
                mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(true));
            }

            getViewModel().reloadData();
        }
    }

    /**
     * Called when the observed {@link androidx.lifecycle.LiveData} is changed.
     * <p>
     * This method will update the related adapter and the {@link SwipeRefreshLayout} if present.
     *
     * @param model The data observed by the {@link androidx.lifecycle.LiveData}.
     */
    protected void onDataReady(List<T> model) {
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(false));
        }

        // Indicate that the data is ready now.
        mDataReady = model != null;

        swapModel(model);
    }
    @Override
    public void onResume() {
        super.onResume();
        getContent();
    }

    public void onConnected() {
        refreshContent();
    }

    public void onDisconnected() {
        refreshContent();

        if (!isDetached()) {
            finishedLoading();
        }
    }

    public void onDatabaseUpdated() {
        refreshContent();
    }

    private void finishedLoading() {
        if (null != mSwipeRefreshLayout) {
            mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(false));
        }
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
}
