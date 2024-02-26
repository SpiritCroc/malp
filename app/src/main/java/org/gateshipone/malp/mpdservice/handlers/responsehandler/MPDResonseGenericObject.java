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

package org.gateshipone.malp.mpdservice.handlers.responsehandler;


import android.os.Message;

import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDStatistics;

public abstract class MPDResonseGenericObject extends MPDResponseHandler {

    public MPDResonseGenericObject() {

    }

    /**
     * Handle function for the server statistics. This only calls the abstract method
     * which needs to get implemented by the user of this class.
     * @param msg Message object containing a MPDStatistics object
     */
    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);


        /* Call album response handler */
        Object obj = (Object)msg.obj;
        handleObject(obj);
    }

    /**
     * Send object to the receiving handler
     * @param object Object to send
     */
    public void sendObject(java.lang.Object object) {
        Message responseMessage = this.obtainMessage();
        responseMessage.obj = object;
        sendMessage(responseMessage);
    }

    /**
     * Abstract method to be implemented by the user of the MPD implementation.
     * This should be a callback for the UI thread and run in the UI thread.
     * This can be used for updating lists of adapters and views.
     * @param object Current MPD object
     */
    abstract public void handleObject(Object object);
}
