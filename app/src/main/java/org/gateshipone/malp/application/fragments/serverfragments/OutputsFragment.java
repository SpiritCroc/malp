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


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import org.gateshipone.malp.R;
import org.gateshipone.malp.application.adapters.OutputAdapter;
import org.gateshipone.malp.application.viewmodels.GenericViewModel;
import org.gateshipone.malp.application.viewmodels.OutputsViewModel;
import org.gateshipone.malp.mpdservice.handlers.serverhandler.MPDCommandHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDOutput;

public class OutputsFragment extends GenericMPDFragment<MPDOutput> implements AbsListView.OnItemClickListener {
    public final static String TAG = OutputsFragment.class.getSimpleName();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.listview_layout, container, false);

        // Get the main ListView of this fragment
        mListView = rootView.findViewById(R.id.main_listview);

        // Create the needed adapter for the ListView
        mAdapter = new OutputAdapter(getActivity());

        // Combine the two to a happy couple
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        registerForContextMenu(mListView);

        setHasOptionsMenu(true);

        getViewModel().getData().observe(getViewLifecycleOwner(), this::onDataReady);

        // Return the ready inflated and configured fragment view.
        return rootView;
    }

    @Override
    GenericViewModel<MPDOutput> getViewModel() {
        return new ViewModelProvider(this, new OutputsViewModel.OutputsViewModelFactory(getActivity().getApplication())).get(OutputsViewModel.class);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MPDOutput output = (MPDOutput) mAdapter.getItem(position);
        MPDCommandHandler.toggleOutput(output.getID());
        ((OutputAdapter) mAdapter).setOutputActive(position, !output.getOutputState());
    }
}
