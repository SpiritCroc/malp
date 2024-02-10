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

package org.gateshipone.malp.mpdservice.handlers;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public abstract class MPDIdleChangeHandler extends Handler {
    public MPDIdleChangeHandler() {
        super();
    }
    public MPDIdleChangeHandler(Looper looper) {
        super(looper);
    }

    private enum IDLE_STATE {
        IDLE,
        NOIDLE,
    }

    public enum CHANGED_SUBSYSTEM {
        DATABASE,
        UPDATE,
        STORED_PLAYLIST,
        PLAYLIST,
        PLAYER,
        MIXER,
        OUTPUT,
        OPTIONS,
        PARTITION,
        STICKER,
        SUBSCRIPTION,
        MESSAGE,
        NEIGHBOR,
        MOUNT
    }

    public static class MPDChangedSubsystemsResponse {
        private final boolean[] mChangedSubsystems;

        public MPDChangedSubsystemsResponse() {
            mChangedSubsystems = new boolean[CHANGED_SUBSYSTEM.values().length];
        }

        public void setSubsystemChanged(CHANGED_SUBSYSTEM subsystem, boolean changed) {
            mChangedSubsystems[subsystem.ordinal()] = changed;
        }

        public boolean getSubsystemChanged(CHANGED_SUBSYSTEM subsystem) {
            return mChangedSubsystems[subsystem.ordinal()];
        }
    }

    public static class MPDIdleChangeHandlerAction {
        private final IDLE_STATE mIdleState;
        private MPDChangedSubsystemsResponse mChangedSubsystems;
        public MPDIdleChangeHandlerAction(IDLE_STATE state) {
            mIdleState = state;
        }

        public IDLE_STATE getIdleState() {
            return mIdleState;
        }

        public void setChangedSubsystems(MPDChangedSubsystemsResponse response) {
            mChangedSubsystems = response;
        }

        public MPDChangedSubsystemsResponse getChangedSubsystems() {
            return mChangedSubsystems;
        }
    }

    /**
     * Handles the change of the connection of the MPDConnection. Can be used
     * to get notified on connect & disconnect.
     * @param msg Message object
     */
    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);

        MPDIdleChangeHandlerAction action = (MPDIdleChangeHandlerAction) msg.obj;
        switch (action.getIdleState()) {
            case IDLE: {
                onIdle();
                break;
            }
            case NOIDLE: {
                onNoIdle(action.getChangedSubsystems());
                break;
            }
        }
    }

    public void idle() {
        Message msg = obtainMessage();

        MPDIdleChangeHandlerAction action = new MPDIdleChangeHandlerAction(IDLE_STATE.IDLE);

        msg.obj = action;
        sendMessage(msg);
    }

    public void noIdle(MPDChangedSubsystemsResponse response) {
        Message msg = obtainMessage();
        MPDIdleChangeHandlerAction action = new MPDIdleChangeHandlerAction(IDLE_STATE.NOIDLE);
        action.setChangedSubsystems(response);

        msg.obj = action;
        sendMessage(msg);
    }

    protected abstract void onIdle();
    protected abstract void onNoIdle(MPDChangedSubsystemsResponse response);
}
