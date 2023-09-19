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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.utils.FormatHelper;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.StatisticsViewModel;
import org.gateshipone.malp.mpdservice.handlers.MPDStatusChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.lang.ref.WeakReference;
import java.util.List;

public class ServerStatisticFragment extends GenericMPDFragment<MPDStatistics> {
    public static final String TAG = ServerStatisticFragment.class.getSimpleName();

    private TextView mArtistCount;
    private TextView mAlbumsCount;
    private TextView mSongsCount;

    private TextView mUptime;
    private TextView mPlaytime;
    private TextView mLastUpdate;
    private TextView mDBLength;

    private TextView mDBUpdating;

    private TextView mServerFeatures;

    private MPDCurrentStatus mLastStatus;
    private ServerStatusHandler mServerStatusHandler;

    public static ServerStatisticFragment newInstance() {
        return new ServerStatisticFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_server_statistic, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mArtistCount = view.findViewById(R.id.server_statistic_artist_count);
        mAlbumsCount = view.findViewById(R.id.server_statistic_albums_count);
        mSongsCount = view.findViewById(R.id.server_statistic_songs_count);

        mUptime = view.findViewById(R.id.server_statistic_server_uptime);
        mPlaytime = view.findViewById(R.id.server_statistic_server_playtime);
        mLastUpdate = view.findViewById(R.id.server_statistic_db_update);
        mDBLength = view.findViewById(R.id.server_statistic_db_playtime);

        mDBUpdating = view.findViewById(R.id.server_statistic_updateing_db);

        mServerFeatures = view.findViewById(R.id.server_statistic_malp_server_information);

        view.findViewById(R.id.server_statistic_update_db_btn).setOnClickListener(v -> {
            // Update the whole database => no path
            MPDQueryHandler.updateDatabase("");
        });

        mServerStatusHandler = new ServerStatusHandler(this);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);
    }

    /**
     * Attaches callbacks
     */
    @Override
    public void onResume() {
        super.onResume();

        MPDStateMonitoringHandler.getHandler().registerStatusListener(mServerStatusHandler);

        mServerStatusHandler.onNewStatusReady(MPDStateMonitoringHandler.getHandler().getLastStatus());
    }

    @Override
    public synchronized void onPause() {
        super.onPause();

        MPDStateMonitoringHandler.getHandler().unregisterStatusListener(mServerStatusHandler);
    }

    @Override
    GenericViewModel<MPDStatistics> getViewModel() {
        return new ViewModelProvider(this, new StatisticsViewModel.StatisticsViewModelFactory(requireActivity().getApplication())).get(StatisticsViewModel.class);
    }

    @Override
    protected void onDataReady(List<MPDStatistics> model) {
        if (model != null && !model.isEmpty()) {
            final MPDStatistics statistics = model.get(0);

            mArtistCount.setText(String.valueOf(statistics.getArtistsCount()));
            mAlbumsCount.setText(String.valueOf(statistics.getAlbumCount()));
            mSongsCount.setText(String.valueOf(statistics.getSongCount()));

            mUptime.setText(FormatHelper.formatTracktimeFromSWithDays(statistics.getServerUptime(), getContext()));
            mPlaytime.setText(FormatHelper.formatTracktimeFromSWithDays(statistics.getPlayDuration(), getContext()));
            mDBLength.setText(FormatHelper.formatTracktimeFromSWithDays(statistics.getAllSongDuration(), getContext()));

            if (statistics.getLastDBUpdate() != 0) {
                mLastUpdate.setText(FormatHelper.formatTimeStampToString(statistics.getLastDBUpdate() * 1000));
            }

            final MPDCapabilities capabilities = MPDInterface.getGenericInstance().getServerCapabilities();
            if (capabilities != null) {
                mServerFeatures.setText(capabilities.getServerFeatures());
            }
        }
    }

    private synchronized void showDatabaseUpdating(final boolean show) {
        Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        activity.runOnUiThread(() -> {
            // If state is changed, update statistics to show current timestamp
            getViewModel().reloadData();

            if (show) {
                mDBUpdating.setVisibility(View.VISIBLE);
            } else {
                mDBUpdating.setVisibility(View.GONE);
            }
        });
    }

    private static class ServerStatusHandler extends MPDStatusChangeHandler {
        WeakReference<ServerStatisticFragment> mFragment;

        ServerStatusHandler(ServerStatisticFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        protected void onNewStatusReady(MPDCurrentStatus status) {
            if (mFragment.get().mLastStatus == null) {
                mFragment.get().showDatabaseUpdating(status.getUpdateDBJob() >= 0);
            } else if (mFragment.get().mLastStatus != null && mFragment.get().mLastStatus.getUpdateDBJob() != status.getUpdateDBJob()) {
                mFragment.get().showDatabaseUpdating(status.getUpdateDBJob() >= 0);
            }
            mFragment.get().mLastStatus = status;
        }

        @Override
        protected void onNewTrackReady(MPDTrack track) {
            // unused
        }
    }
}
