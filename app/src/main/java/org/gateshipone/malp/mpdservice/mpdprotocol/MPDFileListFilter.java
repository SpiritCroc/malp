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

package org.gateshipone.malp.mpdservice.mpdprotocol;


import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.Iterator;
import java.util.List;

public class MPDFileListFilter {
    public static void filterAlbumMBID(List<MPDFileEntry> list, String albumMBID) {
        filterMPDTrack(list, track -> albumMBID.equalsIgnoreCase(track.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID)));
    }

    public static void filterAlbumMBIDandAlbumArtist(List<MPDFileEntry> list, String albumMBID, String albumArtist) {
        filterMPDTrack(list, track ->
                // Check if MBID matches (or is empty in both cases (tag missing))
                (albumMBID.equalsIgnoreCase(track.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID)))
                        // Check if artist tag matches
                        && ((!albumArtist.isEmpty() && !track.getStringTag(MPDTrack.StringTagTypes.ARTIST).isEmpty() && track.getStringTag(MPDTrack.StringTagTypes.ARTIST).equalsIgnoreCase(albumArtist))
                        ||
                        // OR if albumartist tag matches
                        (!albumArtist.isEmpty() && !track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST).isEmpty() && track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTIST).equalsIgnoreCase(albumArtist)))
        );
    }

    public static void filterAlbumMBIDandAlbumArtistSort(List<MPDFileEntry> list, String albumMBID, String albumArtist) {
        filterMPDTrack(list, track ->
                // Check if MBID matches (or is empty in both cases (tag missing))
                (albumMBID.equalsIgnoreCase(track.getStringTag(MPDTrack.StringTagTypes.ALBUM_MBID)))
                        // Check if artist tag matches
                        && ((!albumArtist.isEmpty() && !track.getStringTag(MPDTrack.StringTagTypes.ARTISTSORT).isEmpty() && track.getStringTag(MPDTrack.StringTagTypes.ARTISTSORT).equalsIgnoreCase(albumArtist))
                        ||
                        // OR if albumartist tag matches
                        (!albumArtist.isEmpty() && !track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTISTSORT).isEmpty() && track.getStringTag(MPDTrack.StringTagTypes.ALBUMARTISTSORT).equalsIgnoreCase(albumArtist)))
        );
    }


    private static void filterMPDTrack(List<MPDFileEntry> list, MPDFileFilter filter) {
        Iterator<MPDFileEntry> iterator = list.iterator();

        while (iterator.hasNext()) {
            MPDFileEntry item = iterator.next();
            if (item instanceof MPDTrack) {
                if (!filter.accept((MPDTrack) item)) {
                    // If filter does not match, remove element
                    iterator.remove();
                }
            }
        }
    }

    private interface MPDFileFilter {
        boolean accept(MPDTrack track);
    }
}
