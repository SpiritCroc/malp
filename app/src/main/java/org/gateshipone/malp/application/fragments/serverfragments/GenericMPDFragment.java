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


import android.app.Activity;
import android.os.Looper;

import androidx.annotation.NonNull;

import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.gateshipone.malp.application.adapters.GenericSectionAdapter;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;

import java.lang.ref.WeakReference;
import java.util.List;

public abstract class GenericMPDFragment<T extends MPDGenericItem> extends DialogFragment {
    private static final String TAG = GenericMPDFragment.class.getSimpleName();

    private ConnectionStateListener mConnectionStateListener;

    SwipeRefreshLayout mSwipeRefreshLayout = null;

    /**
     * The generic adapter for the view model
     */
    protected GenericSectionAdapter<T> mAdapter;

    /**
     * Holds if data is ready of has to be refetched (e.g. after memory trimming)
     */
    private boolean mDataReady;

    abstract GenericViewModel<T> getViewModel();

    @Override
    public void onResume() {
        super.onResume();
        getContent();
        Activity activity = getActivity();
        if (activity != null) {
            mConnectionStateListener = new ConnectionStateListener(this, activity.getMainLooper());
            MPDInterface.mInstance.addMPDConnectionStateChangeListener(mConnectionStateListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (this) {
            MPDInterface.mInstance.removeMPDConnectionStateChangeListener(mConnectionStateListener);
            mConnectionStateListener = null;
        }
    }

    void refreshContent() {
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(true));
        }

        mDataReady = false;

        getViewModel().reloadData();
    }

    /**
     * Checks if data is available or not. If not it will start getting the data.
     * This method should be called from onResume and if the fragment is part of an view pager,
     * every time the View is activated because the underlying data could be cleaned because
     * of memory pressure.
     */
    void getContent() {
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

    private void swapModel(List<T> model) {
        // Transfer the data to the adapter so that the views can use it
        mAdapter.swapModel(model);
    }

    private static class ConnectionStateListener extends MPDConnectionStateChangeHandler {
        private WeakReference<GenericMPDFragment> pFragment;

        ConnectionStateListener(GenericMPDFragment fragment, Looper looper) {
            super(looper);
            pFragment = new WeakReference<>(fragment);
        }

        @Override
        public void onConnected() {
            pFragment.get().refreshContent();
        }

        @Override
        public void onDisconnected() {
            GenericMPDFragment fragment = pFragment.get();
            if (fragment == null) {
                return;
            }
            synchronized (fragment) {
                if (!fragment.isDetached()) {
                    // TODO is this necessary?
                    fragment.finishedLoading();
                }
            }
        }
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
