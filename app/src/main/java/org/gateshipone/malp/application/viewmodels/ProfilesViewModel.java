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

package org.gateshipone.malp.application.viewmodels;

import android.app.Application;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.mpdservice.profilemanagement.MPDProfileManager;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDServerProfile;

import java.lang.ref.WeakReference;
import java.util.List;

public class ProfilesViewModel extends GenericViewModel<MPDServerProfile> {

    public ProfilesViewModel(@NonNull final Application application) {
        super((application));
    }

    @Override
    void loadData() {
        new ProfilesLoaderTask(this).execute();
    }

    private static class ProfilesLoaderTask extends AsyncTask<Void, Void, List<MPDServerProfile>> {

        private final WeakReference<ProfilesViewModel> mProfilesViewModel;

        ProfilesLoaderTask(final ProfilesViewModel profilesViewModel) {
            mProfilesViewModel = new WeakReference<>(profilesViewModel);
        }

        @Override
        protected List<MPDServerProfile> doInBackground(Void... voids) {
            final ProfilesViewModel profilesViewModel = mProfilesViewModel.get();

            if (profilesViewModel != null) {
                return MPDProfileManager.getInstance(profilesViewModel.getApplication()).getProfiles();
            }

            return null;
        }

        @Override
        protected void onPostExecute(final List<MPDServerProfile> mpdServerProfiles) {
            final ProfilesViewModel profilesViewModel = mProfilesViewModel.get();

            if (profilesViewModel != null) {
                profilesViewModel.setData(mpdServerProfiles);
            }
        }
    }

    public static class ProfilesViewModelFactory implements ViewModelProvider.Factory {

        private final Application mApplication;

        public ProfilesViewModelFactory(final Application application) {
            mApplication = application;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ProfilesViewModel(mApplication);
        }
    }
}
