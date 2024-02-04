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


import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.OutputAdapter;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.OutputsViewModel;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponsePartitionList;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDQueryHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDPartition;

import java.util.List;

public class OutputsFragment extends GenericMPDFragment<MPDOutput> implements AbsListView.OnItemClickListener {
    public static final String TAG = OutputsFragment.class.getSimpleName();

    private List<MPDPartition> mPartitions;

    private PartitionResponseHandler mPartitionHandler;

    private boolean mPartitionSupport;

    public static OutputsFragment newInstance() {
        return new OutputsFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mPartitionSupport = MPDInterface.getGenericInstance().getServerCapabilities().hasPartitions();
        return inflater.inflate(R.layout.listview_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get the main ListView of this fragment
        ListView listView = view.findViewById(R.id.main_listview);

        // Create the needed adapter for the ListView
        mAdapter = new OutputAdapter(getActivity());

        // Combine the two to a happy couple
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);
        registerForContextMenu(listView);

        setHasOptionsMenu(true);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);
        mPartitionHandler = new PartitionResponseHandler(this);
        if (mPartitionSupport) {
            MPDQueryHandler.getPartitions(mPartitionHandler);
        }
    }

    @Override
    GenericViewModel<MPDOutput> getViewModel() {
        return new ViewModelProvider(this, new OutputsViewModel.OutputsViewModelFactory(requireActivity().getApplication())).get(OutputsViewModel.class);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MPDOutput output = (MPDOutput) mAdapter.getItem(position);
        MPDQueryHandler.toggleOutputPartition(output);
        ((OutputAdapter) mAdapter).setOutputActive(position, !output.getOutputState());
        refreshContent();
    }

    private static class PartitionResponseHandler extends MPDResponsePartitionList {
        OutputsFragment mFragment;

        ContextMenu mMenu;
        public PartitionResponseHandler(OutputsFragment fragment) {
            mFragment = fragment;
        }

        @Override
        public void handlePartitions(List<MPDPartition> partitionList) {
            mFragment.mPartitions = partitionList;
        }

    }

    /**
     * Create the context menu.
     */
    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (!mPartitionSupport) {
            return;
        }

        SubMenu submenu = menu.addSubMenu(getString(R.string.menu_move_output));
        for (MPDPartition partition : mPartitions) {
            MenuItem item = submenu.add(partition.getPartitionName());
            item.setOnMenuItemClickListener(menuItem -> {
                AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

                if (info == null) {
                    return false;
                }
                MPDQueryHandler.moveOutputToPartition(mAdapter.getItem(info.position),partition);
                refreshContent();
                return false;
            });
        }
    }
}
