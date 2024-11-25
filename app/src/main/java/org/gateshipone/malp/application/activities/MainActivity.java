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

package org.gateshipone.malp.application.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.CurrentPlaylistAdapter;
import org.gateshipone.malp.application.callbacks.AddPathToPlaylist;
import org.gateshipone.malp.application.callbacks.AlbumCallback;
import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.application.callbacks.PlaylistCallback;
import org.gateshipone.malp.application.callbacks.ProfileManageCallbacks;
import org.gateshipone.malp.application.fragments.ArtworkSettingsFragment;
import org.gateshipone.malp.application.fragments.EditProfileFragment;
import org.gateshipone.malp.application.fragments.InformationSettingsFragment;
import org.gateshipone.malp.application.fragments.ProfilesFragment;
import org.gateshipone.malp.application.fragments.SettingsFragment;
import org.gateshipone.malp.application.fragments.serverfragments.AlbumTracksFragment;
import org.gateshipone.malp.application.fragments.serverfragments.AlbumsFragment;
import org.gateshipone.malp.application.fragments.serverfragments.ArtistAlbumsFragment;
import org.gateshipone.malp.application.fragments.serverfragments.ArtistsFragment;
import org.gateshipone.malp.application.fragments.serverfragments.ChoosePlaylistDialog;
import org.gateshipone.malp.application.fragments.serverfragments.FilesFragment;
import org.gateshipone.malp.application.fragments.serverfragments.MyMusicTabsFragment;
import org.gateshipone.malp.application.fragments.serverfragments.PlaylistTracksFragment;
import org.gateshipone.malp.application.fragments.serverfragments.SavedPlaylistsFragment;
import org.gateshipone.malp.application.fragments.serverfragments.SearchFragment;
import org.gateshipone.malp.application.fragments.serverfragments.ServerPropertiesFragment;
import org.gateshipone.malp.application.fragments.serverfragments.SongDetailsDialog;
import org.gateshipone.malp.application.utils.PermissionHelper;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.views.CurrentPlaylistView;
import org.gateshipone.malp.application.views.NowPlayingView;
import org.gateshipone.malp.mpdservice.ConnectionManager;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDStateMonitoringHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDException;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDCurrentStatus;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDTrack;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDProfileManager;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDServerProfile;

import java.util.List;

public class MainActivity extends GenericActivity
        implements NavigationView.OnNavigationItemSelectedListener, AlbumCallback, ArtistsFragment.ArtistSelectedCallback,
        ProfileManageCallbacks, PlaylistCallback,
        NowPlayingView.NowPlayingDragStatusReceiver, FilesFragment.FilesCallback,
        FABFragmentCallback, SettingsFragment.OnArtworkSettingsRequestedCallback {

    private static final String TAG = "MainActivity";

    public static final String MAINACTIVITY_INTENT_EXTRA_REQUESTEDVIEW = "org.malp.requestedview";

    private static final String MAINACTIVITY_SAVED_INSTANCE_NOW_PLAYING_DRAG_STATUS = "MainActivity.NowPlayingDragStatus";
    private static final String MAINACTIVITY_SAVED_INSTANCE_NOW_PLAYING_VIEW_SWITCHER_CURRENT_VIEW = "MainActivity.NowPlayingViewSwitcherCurrentView";

    private static final boolean TOAST_INSTEAD_OF_SNACK = true;

    private DRAG_STATUS mNowPlayingDragStatus;
    private DRAG_STATUS mSavedNowPlayingDragStatus = null;

    public enum REQUESTEDVIEW {
        NONE,
        NOWPLAYING,
        SETTINGS
    }

    private ActionBarDrawerToggle mDrawerToggle;

    private VIEW_SWITCHER_STATUS mNowPlayingViewSwitcherStatus;
    private VIEW_SWITCHER_STATUS mSavedNowPlayingViewSwitcherStatus;

    private boolean mHeaderImageActive;

    private boolean mUseArtistSort;

    private FloatingActionButton mFAB;

    private boolean mShowNPV = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean switchToSettings = false;

        // restore drag state
        if (savedInstanceState != null) {
            mSavedNowPlayingDragStatus = DRAG_STATUS.values()[savedInstanceState.getInt(MAINACTIVITY_SAVED_INSTANCE_NOW_PLAYING_DRAG_STATUS)];
            mSavedNowPlayingViewSwitcherStatus = VIEW_SWITCHER_STATUS.values()[savedInstanceState.getInt(MAINACTIVITY_SAVED_INSTANCE_NOW_PLAYING_VIEW_SWITCHER_CURRENT_VIEW)];
        } else {
            // if no savedInstanceState is present the activity is started for the first time so check the intent
            final Intent intent = getIntent();

            if (intent != null) {
                // odyssey was opened by widget or notification
                final Bundle extras = intent.getExtras();

                if (extras != null) {
                    REQUESTEDVIEW requestedView = REQUESTEDVIEW.values()[extras.getInt(MAINACTIVITY_INTENT_EXTRA_REQUESTEDVIEW, REQUESTEDVIEW.NONE.ordinal())];
                    switch (requestedView) {
                        case NONE:
                            break;
                        case NOWPLAYING:
                            mShowNPV = true;
                            break;
                        case SETTINGS:
                            switchToSettings = true;
                            break;
                    }
                }
            }
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        // restore elevation behaviour as pre 24 support lib
        AppBarLayout layout = findViewById(R.id.appbar);
        layout.setStateListAnimator(null);
        ViewCompat.setElevation(layout, 0);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // enable back navigation
        final androidx.appcompat.app.ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer != null) {
            mDrawerToggle = new ActionBarDrawerToggle(this, drawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
            drawer.addDrawerListener(mDrawerToggle);
            mDrawerToggle.syncState();
        }

        int navId = switchToSettings ? R.id.nav_app_settings : getDefaultViewID();

        NavigationView navigationView = findViewById(R.id.nav_view);
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this);
            navigationView.setCheckedItem(navId);
        }


        mFAB = findViewById(R.id.andrompd_play_button);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mUseArtistSort = sharedPref.getBoolean(getString(R.string.pref_use_artist_sort_key), getResources().getBoolean(R.bool.pref_use_artist_sort_default));


        registerForContextMenu(findViewById(R.id.main_listview));

        if (MPDProfileManager.getInstance(this).getProfiles().size() == 0) {
            navId = R.id.nav_profiles;

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(getResources().getString(R.string.welcome_dialog_title));
            builder.setMessage(getResources().getString(R.string.welcome_dialog_text));


            builder.setPositiveButton(R.string.dialog_action_ok, (dialog, id) -> {
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }

        if (findViewById(R.id.fragment_container) != null) {
            if (savedInstanceState != null) {
                return;
            }

            Fragment fragment;
            String fragmentTag;

            if (navId == R.id.nav_saved_playlists) {
                fragment = SavedPlaylistsFragment.newInstance();
                fragmentTag = SavedPlaylistsFragment.TAG;
            } else if (navId == R.id.nav_files) {
                fragment = FilesFragment.newInstance("");
                fragmentTag = FilesFragment.TAG;
            } else if (navId == R.id.nav_profiles) {
                fragment = ProfilesFragment.newInstance();
                fragmentTag = ProfilesFragment.TAG;
            } else if (navId == R.id.nav_app_settings) {
                fragment = SettingsFragment.newInstance();
                fragmentTag = SettingsFragment.TAG;
            } else if (navId == R.id.nav_search) {
                fragment = SearchFragment.newInstance();
                fragmentTag = SearchFragment.TAG;
            } else if (navId == R.id.nav_library) {
                fragment = MyMusicTabsFragment.newInstance(getDefaultTab());
                fragmentTag = MyMusicTabsFragment.TAG;
            } else {
                fragment = MyMusicTabsFragment.newInstance(getDefaultTab());
                fragmentTag = MyMusicTabsFragment.TAG;
            }

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment, fragmentTag);
            transaction.commit();
        }

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        FragmentManager fragmentManager = getSupportFragmentManager();

        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {
            NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
            if (nowPlayingView != null) {
                View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
                coordinatorLayout.setVisibility(View.VISIBLE);
                nowPlayingView.minimize();
            }
        } else {
            super.onBackPressed();

            // enable navigation bar when backstack empty
            if (fragmentManager.getBackStackEntryCount() == 0) {
                mDrawerToggle.setDrawerIndicatorEnabled(true);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        inflateProfileMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        FragmentManager fragmentManager = getSupportFragmentManager();

        if (item.getItemId() == android.R.id.home) {
            if (fragmentManager.getBackStackEntryCount() > 0) {
                onBackPressed();
            } else {
                // back stack empty so enable navigation drawer

                mDrawerToggle.setDrawerIndicatorEnabled(true);

                if (mDrawerToggle.onOptionsItemSelected(item)) {
                    return true;
                }
            }
        } else if (item.getItemId() == R.id.nav_clear_playlist) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_delete_playlist)
                    .setMessage(R.string.dialog_message_delete_current_playlist)
                    .setPositiveButton(R.string.dialog_action_yes, (dialog, which) -> MPDQueryHandler.clearPlaylist())
                    .setNegativeButton(R.string.dialog_action_no, (dialog, which) -> {})
                    .create().show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.main_listview && mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {
            int position = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.context_menu_current_playlist_track, menu);

            // Check if the menu is created for the currently playing song. If this is the case, do not show play as next item.
            MPDCurrentStatus status = MPDStateMonitoringHandler.getHandler().getLastStatus();
            if (status != null && position == status.getCurrentSongIndex()) {
                menu.findItem(R.id.action_song_play_next).setVisible(false);
            }


            CurrentPlaylistView currentPlaylistView = findViewById(R.id.now_playing_playlist);
            if (currentPlaylistView.getItemViewType(position) == CurrentPlaylistAdapter.VIEW_TYPES.TYPE_SECTION_TRACK_ITEM) {
                menu.findItem(R.id.action_remove_album).setVisible(true);
            }
        }
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final ContextMenu.ContextMenuInfo menuInfo = item.getMenuInfo();

        if (menuInfo == null) {
            return super.onContextItemSelected(item);
        }

        // we have two types of adapter context menuinfo classes so we have to make sure the current item contains the correct type of menuinfo
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

            CurrentPlaylistView currentPlaylistView = findViewById(R.id.now_playing_playlist);

            if (currentPlaylistView != null && mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {

                final MPDTrack track = (MPDTrack) currentPlaylistView.getItem(info.position);

                final int itemId = item.getItemId();

                if (itemId == R.id.action_song_play_next) {
                    MPDQueryHandler.playIndexAsNext(info.position);
                    return true;
                } else if (itemId == R.id.action_add_to_saved_playlist) {
                    // open dialog in order to save the current playlist as a playlist in the mediastore
                    ChoosePlaylistDialog choosePlaylistDialog = ChoosePlaylistDialog.newInstance(true);
                    choosePlaylistDialog.setCallback(new AddPathToPlaylist(track, this));
                    choosePlaylistDialog.show(getSupportFragmentManager(), "ChoosePlaylistDialog");
                    return true;
                } else if (itemId == R.id.action_remove_song) {
                    MPDQueryHandler.removeSongFromCurrentPlaylist(info.position);
                    return true;
                } else if (itemId == R.id.action_remove_album) {
                    currentPlaylistView.removeAlbumFrom(info.position);
                    return true;
                } else if (itemId == R.id.action_show_artist) {
                    if (mUseArtistSort) {
                        onArtistSelected(new MPDArtist(track.getStringTag(MPDTrack.StringTagTypes.ARTISTSORT)), null);
                    } else {
                        onArtistSelected(new MPDArtist(track.getStringTag(MPDTrack.StringTagTypes.ARTIST)), null);
                    }
                    return true;
                } else if (itemId == R.id.action_show_album) {
                    MPDAlbum tmpAlbum = track.getAlbum();
                    onAlbumSelected(tmpAlbum, null);
                    return true;
                } else if (itemId == R.id.action_show_details) {
                    // Open song details dialog
                    SongDetailsDialog songDetailsDialog = SongDetailsDialog.createDialog(track, true);
                    songDetailsDialog.show(getSupportFragmentManager(), "SongDetails");
                    return true;
                }
            }
        }

        return super.onContextItemSelected(item);
    }


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        final int id = item.getItemId();
        final View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
        coordinatorLayout.setVisibility(View.VISIBLE);

        final NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
        if (nowPlayingView != null) {
            nowPlayingView.minimize();
        }

        final FragmentManager fragmentManager = getSupportFragmentManager();

        // clear backstack
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        Fragment fragment = null;
        String fragmentTag = "";

        if (id == R.id.nav_library) {
            fragment = MyMusicTabsFragment.newInstance(getDefaultTab());
            fragmentTag = MyMusicTabsFragment.TAG;
        } else if (id == R.id.nav_saved_playlists) {
            fragment = SavedPlaylistsFragment.newInstance();
            fragmentTag = SavedPlaylistsFragment.TAG;
        } else if (id == R.id.nav_files) {
            fragment = FilesFragment.newInstance("");
            fragmentTag = FilesFragment.TAG;
        } else if (id == R.id.nav_search) {
            fragment = SearchFragment.newInstance();
            fragmentTag = SearchFragment.TAG;
        } else if (id == R.id.nav_profiles) {
            fragment = ProfilesFragment.newInstance();
            fragmentTag = ProfilesFragment.TAG;
        } else if (id == R.id.nav_app_settings) {
            fragment = SettingsFragment.newInstance();
            fragmentTag = SettingsFragment.TAG;
        } else if (id == R.id.nav_server_properties) {
            fragment = ServerPropertiesFragment.newInstance();
            fragmentTag = ServerPropertiesFragment.TAG;
        } else if (id == R.id.nav_information) {
            fragment = InformationSettingsFragment.newInstance();
            fragmentTag = InformationSettingsFragment.class.getSimpleName();
        }

        final DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        // Do the actual fragment transaction
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment, fragmentTag);
        transaction.commit();

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        final NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
        if (nowPlayingView != null) {

            nowPlayingView.registerDragStatusReceiver(this);

            /*
             * Check if the activity got an extra in its intend to show the nowplayingview directly.
             * If yes then pre set the dragoffset of the draggable helper.
             */
            if (mShowNPV) {
                nowPlayingView.setDragOffset(0.0f);

                // check preferences if the playlist should be shown
                final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

                final boolean showPlaylist = sharedPref.getBoolean(getString(R.string.pref_npv_show_playlist_key), getResources().getBoolean(R.bool.pref_npv_show_playlist_default));

                if (showPlaylist) {
                    mNowPlayingViewSwitcherStatus = VIEW_SWITCHER_STATUS.PLAYLIST_VIEW;
                    nowPlayingView.setViewSwitcherStatus(mNowPlayingViewSwitcherStatus);
                }

                mShowNPV = false;
            } else {
                // set drag status
                if (mSavedNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {
                    nowPlayingView.setDragOffset(0.0f);
                } else if (mSavedNowPlayingDragStatus == DRAG_STATUS.DRAGGED_DOWN) {
                    nowPlayingView.setDragOffset(1.0f);
                }
                mSavedNowPlayingDragStatus = null;

                // set view switcher status
                if (mSavedNowPlayingViewSwitcherStatus != null) {
                    nowPlayingView.setViewSwitcherStatus(mSavedNowPlayingViewSwitcherStatus);
                    mNowPlayingViewSwitcherStatus = mSavedNowPlayingViewSwitcherStatus;
                }
                mSavedNowPlayingViewSwitcherStatus = null;
            }
            nowPlayingView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
        if (nowPlayingView != null) {
            nowPlayingView.registerDragStatusReceiver(null);

            nowPlayingView.onPause();
        }
    }

    @Override
    protected void onConnected() {
        setNavbarHeader(ConnectionManager.getInstance(getApplicationContext()).getProfileName());
    }

    @Override
    protected void onDisconnected() {
        setNavbarHeader(getString(R.string.app_name_nice));
    }

    @Override
    protected void onMPDError(MPDException.MPDServerException e) {
        e.printStackTrace();
        View layout = findViewById(R.id.drawer_layout);
        String errorText = getString(R.string.snackbar_mpd_server_error_format, e.getErrorCode(), e.getCommandOffset(), e.getServerMessage());
        if (layout != null && !TOAST_INSTEAD_OF_SNACK) {
            Snackbar sb = Snackbar.make(layout, errorText, Snackbar.LENGTH_SHORT);

            // style the snackbar text
            TextView sbText = sb.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            sbText.setTextColor(ThemeUtils.getThemeColor(this, R.attr.malp_color_text_accent));
            sb.show();
        } else {
            Toast.makeText(this, errorText, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onMPDConnectionError(MPDException.MPDConnectionException e) {
        View layout = findViewById(R.id.drawer_layout);
        String errorText = getString(R.string.snackbar_mpd_connection_error_format, e.getError());
        if (layout != null && !TOAST_INSTEAD_OF_SNACK) {

            Snackbar sb = Snackbar.make(layout, errorText, Snackbar.LENGTH_SHORT);

            // style the snackbar text
            TextView sbText = sb.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            sbText.setTextColor(ThemeUtils.getThemeColor(this, R.attr.malp_color_text_accent));
            sb.show();
        } else {
            Toast.makeText(this, errorText, Toast.LENGTH_SHORT).show();
        }
    }

    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);

        // save drag status of the nowplayingview
        savedInstanceState.putInt(MAINACTIVITY_SAVED_INSTANCE_NOW_PLAYING_DRAG_STATUS, mNowPlayingDragStatus.ordinal());

        // save the cover/playlist view status of the nowplayingview
        savedInstanceState.putInt(MAINACTIVITY_SAVED_INSTANCE_NOW_PLAYING_VIEW_SWITCHER_CURRENT_VIEW, mNowPlayingViewSwitcherStatus.ordinal());
    }

    @Override
    public void onAlbumSelected(MPDAlbum album, Bitmap bitmap) {

        if (mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {
            NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
            if (nowPlayingView != null) {
                View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
                coordinatorLayout.setVisibility(View.VISIBLE);
                nowPlayingView.minimize();
            }
        }

        // Create fragment and give it an argument for the selected article
        AlbumTracksFragment newFragment = AlbumTracksFragment.newInstance(album, bitmap);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        // Replace whatever is in the fragment_container view with this
        // fragment,
        // and add the transaction to the back stack so the user can navigate
        // back
        //newFragment.setEnterTransition(new Slide(Gravity.BOTTOM));
        //newFragment.setExitTransition(new Slide(Gravity.TOP));
        transaction.replace(R.id.fragment_container, newFragment, AlbumTracksFragment.TAG);
        transaction.addToBackStack("AlbumTracksFragment");

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setCheckedItem(R.id.nav_library);

        // Commit the transaction
        transaction.commit();
    }

    @Override
    public void onArtistSelected(MPDArtist artist, Bitmap bitmap) {
        if (mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {
            NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
            if (nowPlayingView != null) {
                View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
                coordinatorLayout.setVisibility(View.VISIBLE);
                nowPlayingView.minimize();
            }
        }

        // Create fragment and give it an argument for the selected article
        ArtistAlbumsFragment newFragment = ArtistAlbumsFragment.newInstance(artist, bitmap);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        //newFragment.setEnterTransition(new Slide(Gravity.BOTTOM));
        //newFragment.setExitTransition(new Slide(Gravity.TOP));
        // Replace whatever is in the fragment_container view with this
        // fragment,
        // and add the transaction to the back stack so the user can navigate
        // back
        transaction.replace(R.id.fragment_container, newFragment, ArtistAlbumsFragment.TAG);
        transaction.addToBackStack("ArtistAlbumsFragment");

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setCheckedItem(R.id.nav_library);

        // Commit the transaction
        transaction.commit();
    }

    @Override
    public void onStatusChanged(DRAG_STATUS status) {
        mNowPlayingDragStatus = status;
        if (status == DRAG_STATUS.DRAGGED_UP) {
            View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
            coordinatorLayout.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onDragPositionChanged(float pos) {
        if (mHeaderImageActive) {
            // Get the primary color of the active theme from the helper.
            int newColor = ThemeUtils.getThemeColor(this, R.attr.colorPrimaryDark);

            // Calculate the offset depending on the floating point position (0.0-1.0 of the view)
            // Shift by 24 bit to set it as the A from ARGB and set all remaining 24 bits to 1 to
            int alphaOffset = (((255 - (int) (255.0 * pos)) << 24) | 0xFFFFFF);
            // and with this mask to set the new alpha value.
            newColor &= (alphaOffset);
            getWindow().setStatusBarColor(newColor);
        }
    }

    @Override
    public void onSwitchedViews(VIEW_SWITCHER_STATUS view) {
        mNowPlayingViewSwitcherStatus = view;
    }

    @Override
    public void onStartDrag() {
        View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
        coordinatorLayout.setVisibility(View.VISIBLE);
    }


    @Override
    public void editProfile(MPDServerProfile profile) {
        if (null == profile) {
            profile = new MPDServerProfile(getString(R.string.fragment_profile_default_name), true);
            ConnectionManager.getInstance(getApplicationContext()
            ).addProfile(profile, this);
        }

        // Create fragment and give it an argument for the selected article
        EditProfileFragment newFragment = EditProfileFragment.newInstance(profile);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        //newFragment.setEnterTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.START, getResources().getConfiguration().getLayoutDirection())));
        //newFragment.setExitTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.END, getResources().getConfiguration().getLayoutDirection())));
        // Replace whatever is in the fragment_container view with this
        // fragment,
        // and add the transaction to the back stack so the user can navigate
        // back
        transaction.replace(R.id.fragment_container, newFragment, EditProfileFragment.TAG);
        transaction.addToBackStack("EditProfileFragment");


        // Commit the transaction
        transaction.commit();
    }


    @Override
    public void openPlaylist(String name) {
        // Create fragment and give it an argument for the selected article
        PlaylistTracksFragment newFragment = PlaylistTracksFragment.newInstance(name);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        //newFragment.setEnterTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.START, getResources().getConfiguration().getLayoutDirection())));
        //newFragment.setExitTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.END, getResources().getConfiguration().getLayoutDirection())));
        // Replace whatever is in the fragment_container view with this
        // fragment,
        // and add the transaction to the back stack so the user can navigate
        // back
        transaction.replace(R.id.fragment_container, newFragment);
        transaction.addToBackStack("PlaylistTracksFragment");

        // Commit the transaction
        transaction.commit();

    }


    @Override
    public void setupFAB(boolean active, View.OnClickListener listener) {
        mFAB = findViewById(R.id.andrompd_play_button);
        if (null == mFAB) {
            return;
        }
        if (active) {
            mFAB.show();
        } else {
            mFAB.hide();
        }
        mFAB.setOnClickListener(listener);
    }

    private boolean titleIsProfileName = false;
    @Override
    public void setupToolbar(String title, boolean scrollingEnabled,
                             boolean drawerIndicatorEnabled, boolean showImage) {
        // SC: overwrite files header with profile name
        if (title.equals(getString(R.string.menu_files))) {
            title = ConnectionManager.getInstance(getApplicationContext()).getProfileName();
            titleIsProfileName = true;
        } else {
            titleIsProfileName = false;
        }

        // set drawer state
        mDrawerToggle.setDrawerIndicatorEnabled(drawerIndicatorEnabled);

        RelativeLayout collapsingImageLayout = findViewById(R.id.appbar_image_layout);

        ImageView collapsingImage = findViewById(R.id.collapsing_image);

        if (collapsingImage != null) {
            if (showImage) {
                collapsingImageLayout.setVisibility(View.VISIBLE);
                mHeaderImageActive = true;

                // Get the primary color of the active theme from the helper.
                int newColor = ThemeUtils.getThemeColor(this, R.attr.colorPrimaryDark);

                // Calculate the offset depending on the floating point position (0.0-1.0 of the view)
                // Shift by 24 bit to set it as the A from ARGB and set all remaining 24 bits to 1 to
                int alphaOffset = (((255 - (int) (255.0 * (mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP ? 0.0 : 1.0))) << 24) | 0xFFFFFF);
                // and with this mask to set the new alpha value.
                newColor &= (alphaOffset);
                getWindow().setStatusBarColor(newColor);
            } else {
                collapsingImageLayout.setVisibility(View.GONE);
                mHeaderImageActive = false;

                // Get the primary color of the active theme from the helper.
                getWindow().setStatusBarColor(ThemeUtils.getThemeColor(this, R.attr.colorPrimaryDark));
            }
        } else {
            // If in portrait mode (no collapsing image exists), the status bar also needs dark coloring
            mHeaderImageActive = false;

            // Get the primary color of the active theme from the helper.
            getWindow().setStatusBarColor(ThemeUtils.getThemeColor(this, R.attr.colorPrimaryDark));
        }
        // set scrolling behaviour
        CollapsingToolbarLayout toolbar = findViewById(R.id.collapsing_toolbar);
        AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) toolbar.getLayoutParams();
        params.height = -1;

        if (scrollingEnabled && !showImage) {
            toolbar.setTitleEnabled(false);
            setTitle(title);

            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL + AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED + AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
        } else if (!scrollingEnabled && showImage && collapsingImage != null) {
            toolbar.setTitleEnabled(true);
            toolbar.setTitle(title);


            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED + AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL);
        } else {
            toolbar.setTitleEnabled(false);
            setTitle(title);
            params.setScrollFlags(0);
        }
    }

    @Override
    public DRAG_STATUS getNowPlayingDragStatus() {
        return mNowPlayingDragStatus;
    }

    @Override
    public void setupToolbarImage(Bitmap bm) {
        ImageView collapsingImage = findViewById(R.id.collapsing_image);
        if (collapsingImage != null) {
            collapsingImage.setImageBitmap(bm);

            // FIXME DIRTY HACK: Manually fix the toolbar size to the screen width
            CollapsingToolbarLayout toolbar = findViewById(R.id.collapsing_toolbar);
            AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) toolbar.getLayoutParams();

            params.height = getWindow().getDecorView().getMeasuredWidth();

            // Always expand the toolbar to show the complete image
            AppBarLayout appbar = findViewById(R.id.appbar);
            appbar.setExpanded(true, false);
        }
    }


    @Override
    public void openPath(String path) {
        // Create fragment and give it an argument for the selected directory
        FilesFragment newFragment = FilesFragment.newInstance(path);

        FragmentManager fragmentManager = getSupportFragmentManager();

        FragmentTransaction transaction = fragmentManager.beginTransaction();

        //newFragment.setEnterTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.START, getResources().getConfiguration().getLayoutDirection())));
        //newFragment.setExitTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.END, getResources().getConfiguration().getLayoutDirection())));

        transaction.addToBackStack("FilesFragment" + path);
        transaction.replace(R.id.fragment_container, newFragment);

        // Commit the transaction
        transaction.commit();

    }

    @Override
    public void showAlbumsForPath(String path) {
        if (mNowPlayingDragStatus == DRAG_STATUS.DRAGGED_UP) {
            NowPlayingView nowPlayingView = findViewById(R.id.now_playing_layout);
            if (nowPlayingView != null) {
                View coordinatorLayout = findViewById(R.id.main_coordinator_layout);
                coordinatorLayout.setVisibility(View.VISIBLE);
                nowPlayingView.minimize();
            }
        }
        // Create fragment and give it an argument for the selected article
        AlbumsFragment newFragment = AlbumsFragment.newInstance(path);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        //newFragment.setEnterTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.START, getResources().getConfiguration().getLayoutDirection())));
        //newFragment.setExitTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.END, getResources().getConfiguration().getLayoutDirection())));
        // Replace whatever is in the fragment_container view with this
        // fragment,
        // and add the transaction to the back stack so the user can navigate
        // back
        transaction.replace(R.id.fragment_container, newFragment, AlbumsFragment.TAG);
        transaction.addToBackStack("DirectoryAlbumsFragment");

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setCheckedItem(R.id.nav_library);

        // Commit the transaction
        transaction.commit();
    }

    public void setNavbarHeader(String text) {
        TextView header = findViewById(R.id.navdrawer_header_text);
        if (header == null) {
            return;
        }

        if (text == null) {
            header.setText("");
        }
        header.setText(text);
    }

    @Override
    public void openArtworkSettings() {
        // Create fragment and give it an argument for the selected directory
        ArtworkSettingsFragment newFragment = ArtworkSettingsFragment.newInstance();

        FragmentManager fragmentManager = getSupportFragmentManager();

        FragmentTransaction transaction = fragmentManager.beginTransaction();

        //newFragment.setEnterTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.START, getResources().getConfiguration().getLayoutDirection())));
        //newFragment.setExitTransition(new Slide(GravityCompat.getAbsoluteGravity(GravityCompat.END, getResources().getConfiguration().getLayoutDirection())));

        transaction.addToBackStack("ArtworkSettingsFragment");
        transaction.replace(R.id.fragment_container, newFragment);

        // Commit the transaction
        transaction.commit();
    }

    /**
     * This method asks for permission to show notifications.
     * A permission request will only executed if the device is using android 13 or newer.
     */
    public void requestPermissionShowNotifications() {
        // request only works for android 13 or newer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, PermissionHelper.NOTIFICATION_PERMISSION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                View layout = findViewById(R.id.drawer_layout);
                if (layout != null) {
                    Snackbar sb = Snackbar.make(layout, PermissionHelper.NOTIFICATION_PERMISSION_RATIONALE_TEXT, Snackbar.LENGTH_INDEFINITE);
                    sb.setAction(R.string.permission_request_snackbar_button, view -> ActivityCompat.requestPermissions(this,
                            new String[]{PermissionHelper.NOTIFICATION_PERMISSION},
                            PermissionHelper.MY_PERMISSIONS_REQUEST_NOTIFICATIONS));
                    // style the snackbar text
                    TextView sbText = sb.getView().findViewById(com.google.android.material.R.id.snackbar_text);
                    sbText.setTextColor(ThemeUtils.getThemeColor(this, R.attr.malp_color_text_accent));
                    sb.show();
                }
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{PermissionHelper.NOTIFICATION_PERMISSION},
                        PermissionHelper.MY_PERMISSIONS_REQUEST_NOTIFICATIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionHelper.MY_PERMISSIONS_REQUEST_NOTIFICATIONS) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // permission was granted, yay!
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);

                if (fragment instanceof ArtworkSettingsFragment) {
                    // notification was requested for the Bulkdownloader
                    ((ArtworkSettingsFragment) fragment).startBulkdownload();
                }
            }
        }
    }

    private MyMusicTabsFragment.DEFAULTTAB getDefaultTab() {
        // Read default view preference
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultView = sharedPref.getString(getString(R.string.pref_start_view_key), getString(R.string.pref_view_default));

        // the default tab for mymusic
        MyMusicTabsFragment.DEFAULTTAB defaultTab = MyMusicTabsFragment.DEFAULTTAB.ALBUMS;

        if (defaultView.equals(getString(R.string.pref_view_my_music_artists_key))) {
            defaultTab = MyMusicTabsFragment.DEFAULTTAB.ARTISTS;
        } else if (defaultView.equals(getString(R.string.pref_view_my_music_albums_key))) {
            defaultTab = MyMusicTabsFragment.DEFAULTTAB.ALBUMS;
        }

        return defaultTab;
    }

    private int getDefaultViewID() {
        // Read default view preference
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String defaultView = sharedPref.getString(getString(R.string.pref_start_view_key), getString(R.string.pref_view_default));

        // the nav resource id to mark the right item in the nav drawer
        int navId = -1;

        if (defaultView.equals(getString(R.string.pref_view_my_music_artists_key))) {
            navId = R.id.nav_library;
        } else if (defaultView.equals(getString(R.string.pref_view_my_music_albums_key))) {
            navId = R.id.nav_library;
        } else if (defaultView.equals(getString(R.string.pref_view_playlists_key))) {
            navId = R.id.nav_saved_playlists;
        } else if (defaultView.equals(getString(R.string.pref_view_files_key))) {
            navId = R.id.nav_files;
        } else if (defaultView.equals(getString(R.string.pref_view_search_key))) {
            navId = R.id.nav_search;
        }

        return navId;
    }
    private void inflateProfileMenu(Menu menu) { // TODO call on profile edited/added/removed
        final List<MPDServerProfile> profiles = MPDProfileManager.getInstance(this).getProfiles();
        final NavigationView navigationView = findViewById(R.id.nav_view);
        final DrawerLayout drawer = findViewById(R.id.drawer_layout);
        final String selectedProfileName = ConnectionManager.getInstance(getApplicationContext()).getProfileName();
        if (navigationView != null) {
            MenuItem profilesItem = menu.findItem(R.id.nav_profile_section);
            if (profilesItem == null) {
                return;
            }
            SubMenu profilesMenu = profilesItem.getSubMenu();
            profilesMenu.clear();
            int groupId = 1;
            for (MPDServerProfile profile: profiles) {
                int id = View.generateViewId();
                MenuItem profileItem = profilesMenu.add(groupId, id, 0, profile.getProfileName()).setOnMenuItemClickListener(
                        item -> {
                            ConnectionManager.getInstance(getApplicationContext()).connectProfile(profile, getApplicationContext());
                            drawer.closeDrawer(GravityCompat.START);
                            item.setChecked(true);
                            if (titleIsProfileName) {
                                setTitle(item.getTitle());
                            }
                            return true;
                        }
                );
                profileItem.setChecked(selectedProfileName.equals(profile.getProfileName()));
            }
            profilesMenu.setGroupCheckable(groupId, true, true);
        }
    }
}
