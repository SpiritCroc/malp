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

package org.gateshipone.malp.application.fragments.serverfragments;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.TracksRecyclerViewAdapter;
import org.gateshipone.malp.application.artwork.ArtworkManager;
import org.gateshipone.malp.application.callbacks.AddPathToPlaylist;
import org.gateshipone.malp.application.listviewitems.GenericViewItemHolder;
import org.gateshipone.malp.application.utils.CoverBitmapLoader;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.AlbumTracksViewModel;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.views.MalpRecyclerView;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

public class AlbumTracksFragment extends GenericMPDRecyclerFragment<MPDFileEntry, GenericViewItemHolder> implements CoverBitmapLoader.CoverBitmapListener, ArtworkManager.onNewAlbumImageListener, MalpRecyclerView.OnItemClickListener {
    public final static String TAG = AlbumTracksFragment.class.getSimpleName();
    /**
     * Parameters for bundled extra arguments for this fragment. Necessary to define which album to
     * retrieve from the MPD server.
     */
    private static final String BUNDLE_STRING_EXTRA_ALBUM = "album";
    private static final String BUNDLE_STRING_EXTRA_BITMAP = "bitmap";

    /**
     * Album definition variables
     */
    private MPDAlbum mAlbum;

    private Bitmap mBitmap;

    private CoverBitmapLoader mBitmapLoader;

    private PreferenceHelper.LIBRARY_TRACK_CLICK_ACTION mClickAction;

    private boolean mUseArtistSort;

    public static AlbumTracksFragment newInstance(@NonNull final MPDAlbum album, @Nullable final Bitmap bitmap) {
        final Bundle args = new Bundle();
        args.putParcelable(BUNDLE_STRING_EXTRA_ALBUM, album);
        args.putParcelable(BUNDLE_STRING_EXTRA_BITMAP, bitmap);

        final AlbumTracksFragment fragment = new AlbumTracksFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.recycler_list_refresh, container, false);

        // Get the main ListView of this fragment
        mRecyclerView = rootView.findViewById(R.id.recycler_view);

        /* Check if an artistname/albumame was given in the extras */
        Bundle args = getArguments();
        if (null != args) {
            mAlbum = args.getParcelable(BUNDLE_STRING_EXTRA_ALBUM);

            mBitmap = args.getParcelable(BUNDLE_STRING_EXTRA_BITMAP);
        }

        // Create the needed adapter for the ListView
        mAdapter = new TracksRecyclerViewAdapter();

        // Combine the two to a happy couple
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnItemClicklistener(this);

        setLinearLayoutManagerAndDecoration();

        registerForContextMenu(mRecyclerView);

        // get swipe layout
        mSwipeRefreshLayout = rootView.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(getContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(getContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshContent);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());

        mClickAction = PreferenceHelper.getClickAction(sharedPref, getContext());

        mUseArtistSort = sharedPref.getBoolean(getString(R.string.pref_use_artist_sort_key), getResources().getBoolean(R.bool.pref_use_artist_sort_default));

        setHasOptionsMenu(true);

        mBitmapLoader = new CoverBitmapLoader(getContext(), this);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

        // Return the ready inflated and configured fragment view.
        return rootView;
    }

    @Override
    GenericViewModel<MPDFileEntry> getViewModel() {
        return new ViewModelProvider(this, new AlbumTracksViewModel.AlbumTracksModelFactory(getActivity().getApplication(), mAlbum, mUseArtistSort)).get(AlbumTracksViewModel.class);
    }

    /**
     * Starts the loader to make sure the data is up-to-date after resuming the fragment (from background)
     */
    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(true, new FABOnClickListener());
            mFABCallback.setupToolbar(mAlbum.getName(), false, false, false);
        }

        if (mAlbum != null && mBitmap == null) {
            final View rootView = getView();
            if (rootView != null) {
                getView().post(() -> {
                    final int size = rootView.getWidth();
                    mBitmapLoader.getAlbumImage(mAlbum, false, size, size);
                });
            }
        } else if (mAlbum != null) {
            // Reuse the image passed from the previous fragment
            mFABCallback.setupToolbar(mAlbum.getName(), false, false, true);
            mFABCallback.setupToolbarImage(mBitmap);
            final View rootView = getView();
            if (rootView != null) {
                getView().post(() -> {
                    final int size = rootView.getWidth();
                    // Image too small
                    if (mBitmap.getWidth() < size) {
                        mBitmapLoader.getAlbumImage(mAlbum, false, size, size);
                    }
                });
            }
        }

        ArtworkManager.getInstance(getContext()).registerOnNewAlbumImageListener(this);
    }

    /**
     * Called when the fragment is hidden. This unregisters the listener for a new album image
     */
    @Override
    public void onPause() {
        super.onPause();
        ArtworkManager.getInstance(getContext()).unregisterOnNewAlbumImageListener(this);
    }

    @Override
    public void onItemClick(int position) {
        switch (mClickAction) {
            case ACTION_SHOW_DETAILS: {
                // Open song details dialog
                SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(position), false);
                songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
                break;
            }
            case ACTION_ADD_SONG: {
                enqueueTrack(position);
                break;
            }
            case ACTION_ADD_SONG_AT_START: {
                prependTrack(position);
                break;
            }
            case ACTION_PLAY_SONG: {
                play(position);
                break;
            }
            case ACTION_PLAY_SONG_NEXT: {
                playNext(position);
                break;
            }
        }
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_track, menu);
    }

    /**
     * Hook called when an menu item in the context menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        MalpRecyclerView.RecyclerViewContextMenuInfo info =
                (MalpRecyclerView.RecyclerViewContextMenuInfo) item.getMenuInfo();

        if (info == null) {
            return super.onContextItemSelected(item);
        }

        final int itemId = item.getItemId();

        if (itemId == R.id.action_song_enqueue) {
            enqueueTrack(info.position);
            return true;
        } else if (itemId == R.id.action_song_play) {
            play(info.position);
            return true;
        } else if (itemId == R.id.action_song_play_next) {
            playNext(info.position);
            return true;
        } else if (itemId == R.id.action_song_enqueue_at_start) {
            prependTrack(info.position);
            return true;
        } else if (itemId == R.id.action_add_to_saved_playlist) {
            // open dialog in order to save the current playlist as a playlist in the mediastore
            ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);

            choosePlaylistDialog.setCallback(new AddPathToPlaylist((MPDFileEntry) mAdapter.getItem(info.position), getActivity()));
            choosePlaylistDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "ChoosePlaylistDialog");
            return true;
        } else if (itemId == R.id.action_show_details) {
            // Open song details dialog
            SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(info.position), false);
            songDetailsDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "SongDetails");
            return true;
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Initialize the options menu.
     * Be sure to call {@link #setHasOptionsMenu} before.
     *
     * @param menu         The container for the custom options menu.
     * @param menuInflater The inflater to instantiate the layout.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.fragment_menu_album_tracks, menu);

        // get tint color
        int tintColor = ThemeUtils.getThemeColor(getContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_add_album).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_add_album).setIcon(drawable);

        if (!mAlbum.getMBID().isEmpty()) {
            // Disable legacy feature to remove filter criteria for album tracks if an MBID is
            // available. Albums tagged with a MBID can be considered shown correctly.
            menu.findItem(R.id.action_show_all_tracks).setVisible(false);
        }

        super.onCreateOptionsMenu(menu, menuInflater);
    }

    /**
     * Hook called when an menu item in the options menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.action_reset_artwork) {
            if (null != mFABCallback) {
                mFABCallback.setupToolbar(mAlbum.getName(), false, false, false);
            }
            ArtworkManager.getInstance(getContext()).resetAlbumImage(mAlbum);
            return true;
        } else if (itemId == R.id.action_add_album) {
            enqueueAlbum();
            return true;
        } else if (itemId == R.id.action_show_all_tracks) {
            mAlbum.setMBID("");
            mAlbum.setArtistName("");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        // Do not save the bitmap for later use (too big for binder)
        Bundle args = getArguments();
        if (args != null) {
            getArguments().remove(BUNDLE_STRING_EXTRA_BITMAP);
        }
        super.onSaveInstanceState(savedInstanceState);
    }


    private void enqueueTrack(int index) {
        MPDFileEntry entry = mAdapter.getItem(index);

        MPDQueryHandler.addPath(entry.getPath());
    }

    private void prependTrack(int index) {
        MPDFileEntry entry = mAdapter.getItem(index);

        MPDQueryHandler.addPathAtStart(entry.getPath());
    }

    private void play(int index) {
        MPDTrack track = (MPDTrack) mAdapter.getItem(index);

        MPDQueryHandler.playSong(track.getPath());
    }


    private void playNext(int index) {
        MPDTrack track = (MPDTrack) mAdapter.getItem(index);

        MPDQueryHandler.playSongNext(track.getPath());
    }

    private void enqueueAlbum() {
        if (mUseArtistSort) {
            MPDQueryHandler.addArtistSortAlbum(mAlbum.getName(), mAlbum.getArtistSortName(), mAlbum.getMBID());
        } else {
            MPDQueryHandler.addArtistAlbum(mAlbum.getName(), mAlbum.getArtistName(), mAlbum.getMBID());
        }
    }

    @Override
    public void receiveBitmap(final Bitmap bm, final CoverBitmapLoader.IMAGE_TYPE type) {
        if (type == CoverBitmapLoader.IMAGE_TYPE.ALBUM_IMAGE && null != mFABCallback && bm != null) {
            FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    mFABCallback.setupToolbar(mAlbum.getName(), false, false, true);
                    mFABCallback.setupToolbarImage(bm);
                    getArguments().putParcelable(BUNDLE_STRING_EXTRA_BITMAP, bm);
                });
            }
        }
    }

    @Override
    public void newAlbumImage(MPDAlbum album) {
        if (album.equals(mAlbum)) {
            int width = getView().getMeasuredWidth();
            mBitmapLoader.getAlbumImage(mAlbum, false, width, width);
        }
    }

    private class FABOnClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            MPDCommandHandler.setRandom(false);
            MPDCommandHandler.setRepeat(false);
            if (mUseArtistSort) {
                MPDQueryHandler.playArtistSortAlbum(mAlbum.getName(), mAlbum.getArtistSortName(), mAlbum.getMBID());
            } else {
                MPDQueryHandler.playArtistAlbum(mAlbum.getName(), mAlbum.getArtistName(), mAlbum.getMBID());
            }
        }
    }

}
