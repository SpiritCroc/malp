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

package org.gateshipone.malp.application.loaders;


import android.content.Context;
import androidx.loader.content.AsyncTaskLoader;

import java.util.List;

import org.gateshipone.malp.mpdservice.profilemanagement.MPDProfileManager;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDServerProfile;

public class ProfilesLoader extends AsyncTaskLoader<List<MPDServerProfile>> {
    public ProfilesLoader(Context context) {
        super(context);
    }

    @Override
    public List<MPDServerProfile> loadInBackground() {
        return MPDProfileManager.getInstance(getContext()).getProfiles();
    }

    /**
     * Start loading the data.
     * A previous load dataset will be ignored
     */
    @Override
    protected void onStartLoading() {
        forceLoad();
    }

    /**
     * Stop the loader and cancel the current task.
     */
    @Override
    protected void onStopLoading() {
        cancelLoad();
    }
}
