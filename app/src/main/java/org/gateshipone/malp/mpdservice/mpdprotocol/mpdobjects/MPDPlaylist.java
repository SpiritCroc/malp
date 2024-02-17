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

public class MPDPlaylist extends MPDFileEntry implements MPDGenericItem {
    public static int NOT_SET = -1;
    private int mLength = NOT_SET;
    private int mTitleCount = NOT_SET;

    public MPDPlaylist(@NonNull String path) {
        super(path);
    }

    @Override
    public String getSectionTitle() {
        return getFilename();
    }

    public int compareTo(@NonNull MPDPlaylist another) {
        String title = getFilename();
        String anotherTitle = another.getFilename();

        return title.toLowerCase().compareTo(anotherTitle.toLowerCase());
    }

    public void setLength(int seconds) {
        mLength = seconds;
    }

    public int getLength() {
        return mLength;
    }

    public void setTitleCount(int count) {
        mTitleCount = count;
    }

    public int getTitleCount() {
        return mTitleCount;
    }
}
