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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.FileAdapter;
import org.gateshipone.malp.application.adapters.TagFilterFileAdapter;
import org.gateshipone.malp.application.callbacks.AddPathToPlaylist;
import org.gateshipone.malp.application.utils.PreferenceHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.FilesViewModel;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDCapabilities;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDDirectory;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDFileEntry;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPlaylist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;

import java.util.List;

public class TagFilterSongsFragment extends BaseMPDFragment implements AbsListView.OnItemClickListener {

    public static final String TAG = TagFilterSongsFragment.class.getSimpleName();

    private static final String REQUESTED_TAG_NAME = "ARG_TAG_NAME";
    private static final String REQUESTED_TAG_VALUE = "ARG_TAG_VALUE";

    /**
     * Main ListView of this fragment
     */
    private ListView mListView;

    /**
     * Save the last position here. Gets reused when the user returns to this view after selecting sme
     * albums.
     */
    private int mLastPosition = -1;

    /**
     * Saved search string when user rotates devices
     */
    private String mSearchString;

    private String mTagName;
    private String mTagValue;

    private TagFilterFileAdapter mAdapter;

    private SwipeRefreshLayout mSwipeRefreshLayout;

    /**
     * Constant for state saving
     */
    public static final String FILESFRAGMENT_SAVED_INSTANCE_SEARCH_STRING = "FilesFragment.SearchString";

    private PreferenceHelper.LIBRARY_TRACK_CLICK_ACTION mClickAction;

    public static TagFilterSongsFragment newInstance(@NonNull final Pair<String, String> tagFilter){
        final Bundle args = new Bundle();
        args.putString(REQUESTED_TAG_NAME, tagFilter.first);
        args.putString(REQUESTED_TAG_VALUE, tagFilter.second);

        final TagFilterSongsFragment fragment = new TagFilterSongsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.listview_layout_refreshable, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());

        boolean useTags = sharedPref.getBoolean(getString(R.string.pref_use_tags_in_filebrowser_key), getResources().getBoolean(R.bool.pref_use_tags_in_filebrowser_default));
        mClickAction = PreferenceHelper.getClickAction(sharedPref, requireContext());

        // Get the main ListView of this fragment
        mListView = view.findViewById(R.id.main_listview);

        Bundle args = requireArguments();
        if (null != args) {
            mTagName = args.getString(REQUESTED_TAG_NAME);
            mTagValue = args.getString(REQUESTED_TAG_VALUE);
        }

        // Create the needed adapter for the ListView
        mAdapter = new TagFilterFileAdapter(getActivity());
        mAdapter.setTagFilter(new Pair<>(mTagName, mTagValue));

        // Combine the two to a happy couple
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);

        // get swipe layout
        mSwipeRefreshLayout = view.findViewById(R.id.refresh_layout);
        // set swipe colors
        mSwipeRefreshLayout.setColorSchemeColors(ThemeUtils.getThemeColor(requireContext(), R.attr.colorAccent),
                ThemeUtils.getThemeColor(requireContext(), R.attr.colorPrimary));
        // set swipe refresh listener
        mSwipeRefreshLayout.setOnRefreshListener(() -> mAdapter.refresh());


        setHasOptionsMenu(false);
    }

    /**
     * Starts the loader to make sure the data is up-to-date after resuming the fragment (from background)
     */
    @Override
    public void onResume() {
        super.onResume();

        if (null != mFABCallback) {

        }
    }

    @Override
    void onConnected() {

    }

    @Override
    void onDisconnected() {

    }

    @Override
    void onDatabaseUpdated() {

    }


    /**
     * Called when the fragment is first attached to its context.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // save the already typed search string (or null if nothing is entered)
        outState.putString(FILESFRAGMENT_SAVED_INSTANCE_SEARCH_STRING, mSearchString);
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        MenuInflater inflater = requireActivity().getMenuInflater();
        int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;

        MPDFileEntry file = (MPDFileEntry) mAdapter.getItem(position);

        if (file instanceof MPDTrack) {
            inflater.inflate(R.menu.context_menu_track, menu);
        }
    }

    /**
     * Hook called when an menu item in the context menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        if (info == null) {
            return super.onContextItemSelected(item);
        }

        final int itemId = item.getItemId();

        if (itemId == R.id.action_song_enqueue) {
            MPDQueryHandler.addPath(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_song_enqueue_at_start) {
            MPDQueryHandler.addPathAtStart(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_song_play) {
            MPDQueryHandler.playSong(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_song_play_next) {
            MPDQueryHandler.playSongNext(((MPDFileEntry) mAdapter.getItem(info.position)).getPath());
            return true;
        } else if (itemId == R.id.action_add_to_saved_playlist) {
            // open dialog in order to save the current playlist as a playlist in the mediastore
            ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);

            choosePlaylistDialog.setCallback(new AddPathToPlaylist((MPDFileEntry) mAdapter.getItem(info.position), getActivity()));
            choosePlaylistDialog.show(requireActivity().getSupportFragmentManager(), "ChoosePlaylistDialog");
            return true;
        } else if (itemId == R.id.action_show_details) {
            // Open song details dialog
            SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(info.position), false);
            songDetailsDialog.show(requireActivity().getSupportFragmentManager(), "SongDetails");
            return true;
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        mLastPosition = position;

        MPDFileEntry file = (MPDFileEntry) mAdapter.getItem(position);

        if (file instanceof MPDTrack) {
            switch (mClickAction) {
                case ACTION_SHOW_DETAILS: {
                    // Open song details dialog
                    SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog((MPDTrack) mAdapter.getItem(position), false);
                    songDetailsDialog.show(requireActivity().getSupportFragmentManager(), "SongDetails");
                    break;
                }
                case ACTION_ADD_SONG: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.addPath(track.getPath());
                    break;
                }
                case ACTION_ADD_SONG_AT_START: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.addPathAtStart(track.getPath());
                    break;
                }
                case ACTION_PLAY_SONG: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.playSong(track.getPath());
                    break;
                }
                case ACTION_PLAY_SONG_NEXT: {
                    MPDTrack track = (MPDTrack) mAdapter.getItem(position);

                    MPDQueryHandler.playSongNext(track.getPath());
                    break;
                }
            }
        }
    }

    private class FABListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            MPDCommandHandler.setRandom(false);
            MPDCommandHandler.setRepeat(false);
        }
    }

}
