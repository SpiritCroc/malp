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

package org.gateshipone.malp.application.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponsePartitionList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;

import java.lang.ref.WeakReference;
import java.util.List;

public class PartitionsViewModel extends GenericViewModel<MPDPartition> {

    private final PartitionHandler mPartitionHandler;

    private PartitionsViewModel(@NonNull final Application application) {
        super(application);

        mPartitionHandler = new PartitionHandler(this);
    }

    @Override
    void loadData() {
        MPDQueryHandler.getPartitions(mPartitionHandler);
    }

    private static class PartitionHandler extends MPDResponsePartitionList {

        private final WeakReference<PartitionsViewModel> mOutputsViewModel;

        PartitionHandler(final PartitionsViewModel outputsViewModel) {
            mOutputsViewModel = new WeakReference<>(outputsViewModel);
        }

        @Override
        public void handlePartitions(List<MPDPartition> partitionList) {
            final PartitionsViewModel viewModel = mOutputsViewModel.get();

            if (viewModel != null) {
                viewModel.setData(partitionList);
            }
        }
    }

    public static class PartitionsViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        public PartitionsViewModelFactory(final Application application) {
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new PartitionsViewModel(mApplication);
        }
    }
}
