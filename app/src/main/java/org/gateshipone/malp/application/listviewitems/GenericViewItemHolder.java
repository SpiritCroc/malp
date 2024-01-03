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

package org.gateshipone.malp.application.listviewitems;

import androidx.recyclerview.widget.RecyclerView;

import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class GenericViewItemHolder extends RecyclerView.ViewHolder {

    public GenericViewItemHolder(AbsImageListViewItem itemView) {
        super(itemView);
    }

    public void setTitle(final String title) {
        if (itemView instanceof FileListItem) {
            ((FileListItem) itemView).setTitle(title);
        } else if (itemView instanceof GenericGridItem) {
            ((GenericGridItem) itemView).setTitle(title);
        } else if (itemView instanceof ImageListItem) {
            ((ImageListItem) itemView).setText(title);
        }
    }

    public void prepareArtworkFetching(final ArtworkManager artworkManager, final MPDAlbum album) {
        ((AbsImageListViewItem) itemView).prepareArtworkFetching(artworkManager, album);
    }

    public void startCoverImageTask() {
        ((AbsImageListViewItem) itemView).startCoverImageTask();
    }

    public void setImageDimensions(final int width, final int height) {
        ((AbsImageListViewItem) itemView).setImageDimension(width, height);
    }

    public void setTrack(final MPDTrack track) {
        ((FileListItem) itemView).setTrack(track, true);
    }
}
