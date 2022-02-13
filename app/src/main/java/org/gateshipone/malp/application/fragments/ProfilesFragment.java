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

package org.gateshipone.malp.application.fragments;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.ProfileAdapter;
import org.gateshipone.malp.application.callbacks.FABFragmentCallback;
import org.gateshipone.malp.application.callbacks.ProfileManageCallbacks;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.ProfilesViewModel;
import org.gateshipone.malp.mpdservice.ConnectionManager;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDProfileManager;
import org.gateshipone.malp.mpdservice.profilemanagement.MPDServerProfile;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class ProfilesFragment extends Fragment implements AbsListView.OnItemClickListener, Observer {
    public static final String TAG = ProfilesFragment.class.getSimpleName();

    private ProfileAdapter mAdapter;

    private ProfileManageCallbacks mCallback;

    private FABFragmentCallback mFABCallback = null;

    public static ProfilesFragment newInstance() {
        return new ProfilesFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.listview_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get the main ListView of this fragment
        ListView listView = view.findViewById(R.id.main_listview);

        // Create the needed adapter for the ListView
        mAdapter = new ProfileAdapter(getActivity());

        // Combine the two to a happy couple
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);
        registerForContextMenu(listView);

        setHasOptionsMenu(true);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = requireActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_profile, menu);
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

        if (itemId == R.id.action_profile_connect) {
            connectProfile(info.position);
            return true;
        } else if (itemId == R.id.action_profile_edit) {
            editProfile(info.position);
            return true;
        } else if (itemId == R.id.action_profile_remove) {
            removeProfile(info.position);
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
        menuInflater.inflate(R.menu.fragment_menu_profiles, menu);

        // get tint color
        int tintColor = ThemeUtils.getThemeColor(requireContext(), R.attr.malp_color_text_accent);

        Drawable drawable = menu.findItem(R.id.action_add).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_add).setIcon(drawable);

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
        if (item.getItemId() == R.id.action_add) {
            mCallback.editProfile(null);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when the fragment is first attached to its context.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mCallback = (ProfileManageCallbacks) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement OnArtistSelectedListener");
        }

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            mFABCallback = (FABFragmentCallback) context;
        } catch (ClassCastException e) {
            mFABCallback = null;
        }
    }

    /**
     * Called when the fragment resumes.
     * Reload the data, setup the toolbar and create the PBS connection.
     */
    @Override
    public void onResume() {
        super.onResume();
        MPDProfileManager.getInstance(getActivity()).addObserver(this);

        getViewModel().reloadData();

        if (null != mFABCallback) {
            mFABCallback.setupFAB(false, null);
            mFABCallback.setupToolbar(getString(R.string.menu_profiles), false, true, false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        MPDProfileManager.getInstance(getActivity()).deleteObserver(this);
    }

    private ProfilesViewModel getViewModel() {
        return new ViewModelProvider(this, new ProfilesViewModel.ProfilesViewModelFactory(requireActivity().getApplication())).get(ProfilesViewModel.class);
    }

    private void onDataReady(List<MPDServerProfile> model) {
        mAdapter.swapModel(model);
    }

    private void connectProfile(int index) {
        if (null != mCallback) {
            ConnectionManager.getInstance(requireContext().getApplicationContext()).connectProfile((MPDServerProfile) mAdapter.getItem(index), getContext());
        }
    }

    private void editProfile(int index) {
        if (null != mCallback) {
            mCallback.editProfile((MPDServerProfile) mAdapter.getItem(index));
        }
    }

    private void removeProfile(int index) {
        if (null != mCallback) {
            ConnectionManager.getInstance(requireContext().getApplicationContext()).removeProfile((MPDServerProfile) mAdapter.getItem(index), getContext());
            getViewModel().reloadData();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (null != mCallback) {
            ConnectionManager.getInstance(requireContext().getApplicationContext()).connectProfile((MPDServerProfile) mAdapter.getItem(position), getContext());
            mAdapter.setActive(position, true);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        getViewModel().reloadData();
    }
}
