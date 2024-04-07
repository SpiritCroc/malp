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

package org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects;


import androidx.annotation.NonNull;

public class MPDPartition implements MPDGenericItem {

    @NonNull
    private final String mPartitionName;
    private boolean mActive;

    public MPDPartition(@NonNull String name, boolean enabled) {
        mPartitionName = name;
        mActive = enabled;
    }

    @NonNull
    public String getPartitionName() {
        return mPartitionName;
    }

    public boolean getPartitionState() {
        return mActive;
    }

    public void setPartitionState(boolean active) {
        mActive = active;
    }


    @Override
    @NonNull
    public String getSectionTitle() {
        return mPartitionName;
    }
}
