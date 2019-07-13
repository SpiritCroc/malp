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

package org.gateshipone.malp.application.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseServerStatistics;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;

import java.util.ArrayList;
import java.util.List;

public class StatisticsViewModel extends GenericViewModel<MPDStatistics> {

    private StatisticsViewModel(@NonNull final Application application) {
        super(application);
    }

    @Override
    void loadData() {
        MPDQueryHandler.getStatistics(new MPDResponseServerStatistics() {
            @Override
            public void handleStatistic(MPDStatistics statistics) {
                final List<MPDStatistics> mpdStatisticsList = new ArrayList<>();
                mpdStatisticsList.add(statistics);

                setData(mpdStatisticsList);
            }
        });
    }

    public static class StatisticsViewModelFactory extends ViewModelProvider.NewInstanceFactory {

        private final Application mApplication;

        public StatisticsViewModelFactory(final Application application) {
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new StatisticsViewModel(mApplication);
        }
    }
}
