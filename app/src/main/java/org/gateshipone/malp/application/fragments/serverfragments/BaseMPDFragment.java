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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.MPDIdleChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDGenericItem;

import java.lang.ref.WeakReference;
import java.util.List;

public abstract class BaseMPDFragment<T extends MPDGenericItem> extends DialogFragment {

    private ConnectionStateListener mConnectionStateListener;

    /**
     * Callback to setup toolbar and fab
     */
    protected FABFragmentCallback mFABCallback;

    /**
     * The reference to the possible refresh layout
     */
    protected SwipeRefreshLayout mSwipeRefreshLayout;

    /**
     * Holds if data is ready of has to be refetched (e.g. after memory trimming)
     */
    private boolean mDataReady;

    private StateUpdateHandler mStateHandler;

    abstract void swapModel(List<T> model);

    abstract GenericViewModel<T> getViewModel();

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mStateHandler = new StateUpdateHandler(this);
        MPDStateMonitoringHandler.getHandler().registerIdleListener(mStateHandler);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        MPDStateMonitoringHandler.getHandler().unRegisterIdleListener(mStateHandler);
        mStateHandler = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface, otherwise sets the callback to null.
        try {
            mFABCallback = (FABFragmentCallback) context;
        } catch (ClassCastException e) {
            mFABCallback = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getContent();
        Activity activity = getActivity();
        if (activity != null) {
            mConnectionStateListener = new ConnectionStateListener(this, activity.getMainLooper());
            MPDInterface.getGenericInstance().addMPDConnectionStateChangeListener(mConnectionStateListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        synchronized (this) {
            MPDInterface.getGenericInstance().removeMPDConnectionStateChangeListener(mConnectionStateListener);
            mConnectionStateListener = null;
        }
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

    /**
     * Checks if data is available or not. If not it will start getting the data.
     * This method should be called from onResume and if the fragment is part of an view pager,
     * every time the View is activated because the underlying data could be cleaned because
     * of memory pressure.
     */
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

    private void finishedLoading() {
        if (null != mSwipeRefreshLayout) {
            mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(false));
        }
    }

    private static class ConnectionStateListener extends MPDConnectionStateChangeHandler {
        private final WeakReference<BaseMPDFragment<?>> pFragment;

        ConnectionStateListener(BaseMPDFragment<?> fragment, Looper looper) {
            super(looper);
            pFragment = new WeakReference<>(fragment);
        }

        @Override
        public void onConnected() {
            pFragment.get().refreshContent();
        }

        @Override
        public void onDisconnected() {
            BaseMPDFragment<?> fragment = pFragment.get();
            if (fragment == null) {
                return;
            }
            fragment.refreshContent();
            synchronized (fragment) {
                if (!fragment.isDetached()) {
                    // TODO is this necessary?
                    fragment.finishedLoading();
                }
            }
        }
    }

    private static class StateUpdateHandler extends MPDIdleChangeHandler {
        private final WeakReference<BaseMPDFragment<?>> mFragment;

        public StateUpdateHandler(BaseMPDFragment<?> fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        protected void onIdle() {

        }

        @Override
        protected void onNoIdle(MPDChangedSubsystemsResponse response) {
            if (response.getSubsystemChanged(CHANGED_SUBSYSTEM.DATABASE) || response.getSubsystemChanged(CHANGED_SUBSYSTEM.UPDATE)) {
                BaseMPDFragment<?> fragment = mFragment.get();
                if (fragment == null) {
                    return;
                }

                fragment.refreshContent();
            }
        }
    }
}
