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

package org.gateshipone.malp.mpdservice.handlers.serverhandler;

import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseAlbumArt;
import org.gateshipone.malp.mpdservice.handlers.responsehandler.MPDResponseHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDException;
import org.gateshipone.malp.mpdservice.mpdprotocol.MPDInterface;

public class MPDArtworkHandler extends MPDGenericHandler {
    private static final String TAG = "MPDQueryHandler";
    /**
     * Name of the thread created for the Looper.
     */
    private static final String THREAD_NAME = "AndroMPD-QueryHandler";

    /**
     * HandlerThread that is used by the looper. This ensures that all requests to this handler
     * are done multi-threaded and do not block the UI.
     */
    private static HandlerThread mHandlerThread = null;
    private static MPDArtworkHandler mHandlerSingleton = null;

    /**
     * Private constructor for use in singleton. Called by the static singleton retrieval method.
     *
     * @param looper Looper of a HandlerThread (that is NOT the UI thread)
     */
    protected MPDArtworkHandler(Looper looper) {
        super(looper);
    }

    /**
     * Private method to ensure that the singleton runs in a separate thread.
     * Otherwise android will deny network access because of UI blocks.
     *
     * @return Singleton instance
     */
    public synchronized static MPDArtworkHandler getHandler() {
        // Check if handler was accessed before. If not create the singleton object for the first
        // time.
        if (null == mHandlerSingleton) {
            // Create a new thread used as a looper for this handler.
            // This is the thread in which all messages sent to this handler are handled.
            mHandlerThread = new HandlerThread(THREAD_NAME);
            // It is important to start the thread before using it as a thread for the Handler.
            // Otherwise the handler will cause a crash.
            mHandlerThread.start();
            // Create the actual singleton instance.
            mHandlerSingleton = new MPDArtworkHandler(mHandlerThread.getLooper());
        }
        return mHandlerSingleton;
    }


    /**
     * This is the main entry point of messages.
     * Here all possible messages types need to be handled with the MPDConnection.
     * Have a look into the baseclass MPDGenericHandler for more information about the handling.
     *
     * @param msg Message to process.
     */
    @Override
    public void handleMessage(Message msg) {
        // Call the baseclass handleMessage method here to ensure that the messages handled
        // by the baseclass are handled in subclasses as well.
        super.handleMessage(msg);

        // Type checking
        if (!(msg.obj instanceof MPDHandlerAction)) {
            /* Check if the message object is of correct type. Otherwise just abort here. */
            return;
        }

        MPDHandlerAction mpdAction = (MPDHandlerAction) msg.obj;

        // ResponseHandler used to return the requested items to the caller
        MPDResponseHandler responseHandler;

        /*
         * All messages are handled the same way:
         *  * Check which action was requested
         *  * Check if a ResponseHandler is necessary and also provided. (If not just abort here)
         *  * Request the list of data objects from the MPDConnection (and therefor from the server)
         *  * Pack the response in a Message requested from the given ResponseHandler.
         *  * Send the message to the ResponseHandler
         */
        MPDHandlerAction.NET_HANDLER_ACTION action = mpdAction.getAction();
            if (action == MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUM_ART) {
                responseHandler = mpdAction.getResponseHandler();
                if (!(responseHandler instanceof MPDResponseAlbumArt)) {
                    return;
                }
                String url = mpdAction.getStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH);
                byte[] imageData = new byte[0];
                try {
                    imageData = MPDInterface.mInstance.getAlbumArt(url);
                } catch (MPDException e) {
                    handleMPDError(e);
                }

                ((MPDResponseAlbumArt) responseHandler).sendAlbumArtwork(imageData, url);
            }
    }


        /**
         * Serializes an action into a message and sends it.
         *
         * @param action to be sent out.
         */
    private static void sendMsg(MPDHandlerAction action) {
        Message msg = Message.obtain();
        if (null == msg) {
            return;
        }

        msg.obj = action;

        MPDArtworkHandler.getHandler().sendMessage(msg);
    }

    public static void getAlbumArtwork(String url, MPDResponseAlbumArt responseHandler) {
        MPDHandlerAction action = new MPDHandlerAction(MPDHandlerAction.NET_HANDLER_ACTION.ACTION_GET_ALBUM_ART);

        action.setResponseHandler(responseHandler);
        action.setStringExtra(MPDHandlerAction.NET_HANDLER_EXTRA_STRING.EXTRA_PATH, url);

        sendMsg(action);
    }
}