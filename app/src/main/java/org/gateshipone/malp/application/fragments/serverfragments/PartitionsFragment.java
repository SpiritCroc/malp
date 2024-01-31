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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.OutputAdapter;
import org.gateshipone.malp.application.adapters.PartitionAdapter;
import org.gateshipone.malp.application.fragments.TextDialog;
import org.gateshipone.malp.application.utils.ThemeUtils;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.OutputsViewModel;
import org.gateshipone.malp.application.viewmodels.PartitionsViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;

public class PartitionsFragment extends GenericMPDFragment<MPDPartition> implements AbsListView.OnItemClickListener {
    public static final String TAG = PartitionsFragment.class.getSimpleName();

    public static PartitionsFragment newInstance() {
        return new PartitionsFragment();
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
        mAdapter = new PartitionAdapter(getActivity());

        // Combine the two to a happy couple
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);
        registerForContextMenu(listView);

        setHasOptionsMenu(true);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);
    }

    @Override
    GenericViewModel<MPDPartition> getViewModel() {
        return new ViewModelProvider(this, new PartitionsViewModel.PartitionsViewModelFactory(requireActivity().getApplication())).get(PartitionsViewModel.class);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MPDPartition partition = (MPDPartition) mAdapter.getItem(position);
        MPDQueryHandler.switchPartition(partition.getPartitionName());
        ((PartitionAdapter) mAdapter).setActive(position, true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater menuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.fragment_menu_partition, menu);

        // get tint color
        final int tintColor = ThemeUtils.getThemeColor(requireContext(), R.attr.app_color_on_surface);

        Drawable drawable = menu.findItem(R.id.action_add).getIcon();
        drawable = DrawableCompat.wrap(drawable);
        DrawableCompat.setTint(drawable, tintColor);
        menu.findItem(R.id.action_add).setIcon(drawable);


        super.onCreateOptionsMenu(menu, menuInflater);
    }

    /**
     * Hook called when an menu item in the context menu is selected.
     *
     * @param item The menu item that was selected.
     * @return True if the hook was consumed here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.action_add) {
            // open dialog in order to save the current playlist as a playlist in the mediastore
            TextDialog textDialog = TextDialog.newInstance(getResources().getString(R.string.create_new_partition),
                    getResources().getString(R.string.default_partition_title));

            textDialog.setCallback(text -> {
                MPDQueryHandler.newPartition(text);
                refreshContent();
            });
            textDialog.show(((AppCompatActivity) getContext()).getSupportFragmentManager(), "NewPartitionDialog");
        }
        return true;
    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = requireActivity().getMenuInflater();
        inflater.inflate(R.menu.context_menu_partition, menu);
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

        if (itemId == R.id.action_partition_remove) {
            MPDQueryHandler.deletePartition(mAdapter.getItem(info.position).getPartitionName());
            refreshContent();
            return true;
        }

        return super.onContextItemSelected(item);
    }
}
