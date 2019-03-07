/*
 *  Copyright (C) 2019 Team Gateship-One
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
import androidx.fragment.app.DialogFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.lang.ref.WeakReference;

import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;

public abstract class GenericMPDFragment<T extends Object> extends DialogFragment implements LoaderManager.LoaderCallbacks<T> {
    private static final String TAG = GenericMPDFragment.class.getSimpleName();

    protected ConnectionStateListener mConnectionStateListener;

    protected SwipeRefreshLayout mSwipeRefreshLayout = null;

    protected GenericMPDFragment() {
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContent();
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
            LoaderManager.getInstance(this).destroyLoader(0);
            MPDInterface.mInstance.removeMPDConnectionStateChangeListener(mConnectionStateListener);
            mConnectionStateListener = null;
        }
    }


    protected void refreshContent() {
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.post(() -> mSwipeRefreshLayout.setRefreshing(true));
        }
        if ( !isDetached()) {
            LoaderManager.getInstance(this).restartLoader(0, getArguments(), this);
        }
    }


    private static class ConnectionStateListener extends MPDConnectionStateChangeHandler {
        private WeakReference<GenericMPDFragment> pFragment;

        public ConnectionStateListener(GenericMPDFragment fragment, Looper looper) {
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
            if(fragment == null) {
                    return;
            }
            synchronized (fragment) {
                if (!fragment.isDetached()) {
                    if(LoaderManager.getInstance(fragment).hasRunningLoaders()) {
                        LoaderManager.getInstance(fragment).destroyLoader(0);
                        fragment.finishedLoading();
                    }
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
     * Called when the loader finished loading its data.
     * <p/>
     * The refresh indicator will be stopped if a refreshlayout exists.
     *
     * @param loader The used loader itself
     * @param model  Data of the loader
     */
    @Override
    public void onLoadFinished(Loader<T> loader, T model) {
        finishedLoading();
    }

    @Override
    public void onLoaderReset(Loader<T> loader) {
        finishedLoading();
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
