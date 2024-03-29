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

package org.gateshipone.malp.mpdservice.mpdprotocol;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.gateshipone.malp.BuildConfig;
import org.gateshipone.malp.mpdservice.handlers.MPDConnectionStateChangeHandler;
import org.gateshipone.malp.mpdservice.handlers.MPDIdleChangeHandler;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDAlbum;
import org.gateshipone.malp.mpdservice.mpdprotocol.mpdobjects.MPDArtist;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

/**
 * This is the main MPDConnection class. It will connect to an MPD server via an java TCP socket.
 * If no action, query, or other command to the server is send, this connection will immediately
 * start to idle. This means that the connection is waiting for a response from the mpd server.
 * <p/>
 * For this this class spawns a new thread which is then blocked by the waiting read operation
 * on the reader of the socket.
 * <p/>
 * If a new command is requested by the handler thread the stopIdling function is called, which
 * will send the "noidle" command to the server and requests to deidle the connection. Only then the
 * server is ready again to receive commands. If this is not done properly the server will just
 * terminate the connection.
 * <p/>
 * This mpd connection needs to be run in a different thread than the UI otherwise the UI will block
 * (or android will just throw an exception).
 * <p/>
 * For more information check the protocol definition of the mpd server or contact me via mail.
 */

class MPDConnection {
    enum CONNECTION_STATES {
        /**
         * Obvious
         */
        CONNECTING,
        /**
         * During disconnecting
         */
        DISCONNECTING,
        /**
         * When disconnected
         */
        DISCONNECTED,
        /**
         * When IDLE state is prepared
         */
        GOING_IDLE,
        /**
         * AFTER "IDLE" command is sent to MPD
         */
        IDLE,
        /**
         * Directly when a command is requested to be sent
         */
        GOING_NOIDLE,
        /**
         * If a timeout occured during noidle procedure
         */
        GOING_NOIDLE_TIMEOUT,
        /**
         * State in which the connection to MPD is ready to receive commands (after noidle)
         */
        READY_FOR_COMMANDS,
        /**
         * After sending a command to MPD the connection waits for a response
         */
        WAITING_FOR_RESPONSE,
        /**
         * When receiving a response of any kind until either OK or ACK is received.
         */
        RECEIVING,
    }

    private static final String TAG = MPDConnection.class.getSimpleName();

    /**
     * State variable for this object. For coordinating actions on the socket
     */
    private CONNECTION_STATES mConnectionState = CONNECTION_STATES.DISCONNECTED;

    /**
     * Timeout to wait for socket operations (time in ms)
     */
    private static final int SOCKET_TIMEOUT = 5 * 1000;

    /**
     * Timeout to wait until deidle should be finished (time in ms)
     */
    private static final int DEIDLE_TIMEOUT = 5 * 1000;

    /**
     * Time to wait for response from server. If server is not answering this prevents a livelock
     * after 5 seconds. (time in ns)
     */
    private static final long RESPONSE_TIMEOUT = 5L * 1000L * 1000L * 1000L;

    /**
     * Time to sleep the process waiting for a server response. This reduces the busy-waiting to
     * a bit more efficent sleep/check pattern.
     */
    private static final int RESPONSE_WAIT_SLEEP_TIME = 100;

    private static final int IDLE_WAIT_TIME = 500;

    private static final int AUTO_DISCONNECT_TIME = 5 * 1000;

    /* Internal server parameters used for initiating the connection */
    private String mHostname;
    private String mPassword;
    private int mPort;

    private Socket mSocket;

    private MPDSocketInterface mSocketInterface;

    /* MPD server properties */
    private MPDCapabilities mServerCapabilities;

    private final Timer mIDLETimer;
    private TimerTask mIDLETask;

    /**
     * Timer to schedule a timeout cancellation of the noidle logic (no response from server, e.g.
     * disconnected during idle).
     */
    private final Timer mReadTimeoutTimer;

    /**
     * Task to handle the read timeout for the noidle command.
     */
    private ReadTimeoutTask mReadTimeoutTask;

    /**
     * Only get the server capabilities if server parameters changed
     */
    private boolean mCapabilitiesChanged;

    /**
     * One listener for the state of the connection (connected, disconnected)
     */
    private final ArrayList<MPDConnectionStateChangeHandler> mStateListeners;

    /**
     * One listener for the idle state of the connection. Can be used to react
     * to changes to the server from other clients. When the server is deidled (from outside)
     * it will notify this listener.
     */
    private final ArrayList<MPDIdleChangeHandler> mIdleListeners;


    private final Semaphore mConnectionLock;

    private final boolean mAutoDisconnect;

    /**
     * Creates disconnected MPDConnection with following parameters
     */
    MPDConnection(boolean autoDisconnect) {
        mSocket = null;
        mSocketInterface = null;
        mServerCapabilities = new MPDCapabilities("", null, null);
        mIdleListeners = new ArrayList<>();
        mStateListeners = new ArrayList<>();

        mConnectionLock = new Semaphore(1);

        mIDLETimer = new Timer();

        mReadTimeoutTimer = new Timer();

        mAutoDisconnect = autoDisconnect;

        changeState(CONNECTION_STATES.DISCONNECTED);
    }


    /**
     * Private function to handle read error. Try to disconnect and remove old sockets.
     * Clear up connection state variables.
     */
    private void handleSocketError() {
        changeState(CONNECTION_STATES.DISCONNECTING);

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Read error exception. Disconnecting and cleaning up");
        }
        new Exception().printStackTrace();
        try {
            /* Clear reader/writer up */
            if (null != mSocketInterface) {
                mSocketInterface = null;
            }

            /* Clear TCP-Socket up */
            if (null != mSocket && mSocket.isConnected()) {
                mSocket.setSoTimeout(500);
                mSocket.close();
            }
            mSocket = null;
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Error during read error handling");
            }
        }

        /* Clear up connection state variables */
        changeState(CONNECTION_STATES.DISCONNECTED);

        cancelIDLEWait();

        // Notify listener
        notifyDisconnect();
    }

    /**
     * Set the parameters to connect to. Should be called before the connection attempt
     * otherwise the connection object does not know where to put it.
     *
     * @param hostname Hostname to connect to. Can also be an ip.
     * @param password Password for the server to authenticate with. Can be left empty.
     * @param port     TCP port to connect to.
     */
    void setServerParameters(String hostname, String password, int port) {
        mHostname = hostname;
        mPassword = password;
        mPort = port;
        mCapabilitiesChanged = true;
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Connection parameters changed");
        }
    }

    /**
     * This is the actual start of the connection. It tries to resolve the hostname
     * and initiates the connection to the address and the configured tcp-port.
     */
    void connectToServer() throws MPDException {
        /* If a socket is already open, close it and destroy it. */
        if ((null != mSocket) && (mSocket.isConnected())) {
            disconnectFromServer();
        }

        try {
            mConnectionLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Connecting to: " + mHostname);
        }

        synchronized (this) {
            if (mConnectionState == CONNECTION_STATES.CONNECTING || (null == mHostname) || mHostname.isEmpty()) {
                mConnectionLock.release();
                return;
            }
            changeState(CONNECTION_STATES.CONNECTING);
        }

        /* Create a new socket used for the TCP-connection. */
        mSocket = new Socket();
        try {
            mSocket.connect(new InetSocketAddress(mHostname, mPort), SOCKET_TIMEOUT);
        } catch (IOException e) {
            handleSocketError();
            mConnectionLock.release();
            throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
        }

        /* Check if the socket is connected */
        if (mSocket.isConnected()) {
            /* Try reading from the stream */

            /* Create the reader used for reading from the socket. */
            if (mSocketInterface == null) {
                try {
                    mSocketInterface = new MPDSocketInterface(mSocket.getInputStream(), mSocket.getOutputStream());
                } catch (IOException e) {
                    handleSocketError();
                    mConnectionLock.release();
                    throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
                }
            }


            try {
                waitForResponse();
            } catch (IOException e) {
                handleSocketError();
                mConnectionLock.release();
                throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
            }

            /* If connected try to get MPDs version */
            String readString;

            String versionString = "";

            while (readyRead()) {
                readString = readLine();
                /* Look out for the greeting message */
                if (readString != null && readString.startsWith("OK MPD ")) {
                    versionString = readString.substring(7);

                    String[] versions = versionString.split("\\.");
                    if (versions.length == 3) {
                        // Check if server version changed and if, reread server capabilities later.
                        final int majorVersion = Integer.parseInt(versions[0]);
                        final int minorVersion = Integer.parseInt(versions[1]);

                        if (majorVersion != mServerCapabilities.getMajorVersion() ||
                                (majorVersion == mServerCapabilities.getMajorVersion() && minorVersion != mServerCapabilities.getMinorVersion())) {
                            mCapabilitiesChanged = true;
                        }
                    }
                }
            }

            if (mPassword != null && !mPassword.equals("")) {
                /* Authenticate with server because password is set. */
                authenticateMPDServer();
            }

            if (mCapabilitiesChanged) {
                // Get available commands
                writeLine(MPDCommands.MPD_COMMAND_GET_COMMANDS);

                try {
                    waitForResponse();
                } catch (IOException e) {
                    handleSocketError();
                    mConnectionLock.release();
                    throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
                }

                List<String> commands;
                try {
                    commands = MPDResponseParser.parseMPDCommands(this);
                } catch (IOException e) {
                    handleSocketError();
                    mConnectionLock.release();
                    throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
                }
                // Get list of supported tags
                writeLine(MPDCommands.MPD_COMMAND_GET_TAGS);
                try {
                    waitForResponse();
                } catch (IOException e) {
                    handleSocketError();
                    mConnectionLock.release();
                    throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
                }
                List<String> tags;
                tags = MPDResponseParser.parseMPDTagTypes(this);


                mServerCapabilities = new MPDCapabilities(versionString, commands, tags);
                mCapabilitiesChanged = false;
            }

            // Set the timeout to infinite again
            try {
                mSocket.setSoTimeout(SOCKET_TIMEOUT);
            } catch (SocketException e) {
                handleSocketError();
                mConnectionLock.release();
                throw new MPDException.MPDConnectionException(e.getLocalizedMessage());
            }

            changeState(CONNECTION_STATES.READY_FOR_COMMANDS);

            mConnectionLock.release();
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Connection successfully established");
            }

            // Notify listener
            notifyConnected();
        } else {
            // Connection not established
            mConnectionLock.release();
        }
    }


    /**
     * If the password for the MPDConnection is set then the client should
     * try to authenticate with the server
     */
    private void authenticateMPDServer() throws MPDException {
        writeLine(MPDCommands.MPD_COMMAND_PASSWORD(mPassword));

        try {
            waitForResponse();
        } catch (IOException e) {
            e.printStackTrace();
        }

        checkResponse();
    }

    /**
     * Requests to disconnect from server. This will close the conection and cleanup the socket.
     * After this call it should be safe to reconnect to another server. If this connection is
     * currently in idle state, then it will be deidled before.
     */
    void disconnectFromServer() {
        stopIDLE();

        try {
            mConnectionLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        changeState(CONNECTION_STATES.DISCONNECTING);

        // Close connection gracefully
        sendMPDRAWCommand(MPDCommands.MPD_COMMAND_CLOSE);

        /* Cleanup reader/writer */
        try {
            /* Clear reader/writer up */
            if (null != mSocketInterface) {
                mSocketInterface = null;
            }

            /* Clear TCP-Socket up */
            if (null != mSocket && mSocket.isConnected()) {
                mSocket.setSoTimeout(500);
                mSocket.close();
                mSocket = null;
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Error during disconnecting:" + e);
            }
        }

        /* Clear up connection state variables */
        changeState(CONNECTION_STATES.DISCONNECTED);

        // Notify listener
        notifyDisconnect();

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Disconnected");
        }

        cancelIDLEWait();

        mConnectionLock.release();
    }

    /**
     * Access to the currently server capabilities
     *
     * @return Returns the {@link MPDCapabilities} object of the current connected server
     * or a dummy object.
     */
    synchronized MPDCapabilities getServerCapabilities() {
        if (mAutoDisconnect && !isConnected()) {
            try {
                connectToServer();
            } catch (MPDException e) {
                Log.e(TAG, "Error to reconnect");
            }
        }
        if (isConnected()) {
            return mServerCapabilities;
        } else {
            return new MPDCapabilities("", null, null);
        }
    }

    /**
     * This functions sends the command to the MPD server.
     * If the server is currently idling then it will deidle it first.
     * <p>
     * CAUTION: After using this command it is important to clear the input buffer and read until
     * either "OK" or "ACK ..." is sent. Otherwise the connection will remain in an undefined state.
     * <p>
     * If you want to submit a simple command (without a response other than OK/ACK)
     * like pause/play use sendSimpleMPDCommand.
     *
     * @param command Command string to send to the MPD server
     */
    void sendMPDCommand(String command) {
        sendMPDCommand(command, RESPONSE_TIMEOUT);
    }

    /**
     * This functions sends the command to the MPD server.
     * If the server is currently idling then it will deidle it first.
     * <p>
     * CAUTION: After using this command it is important to clear the input buffer and read until
     * either "OK" or "ACK ..." is sent. Otherwise the connection will remain in an undefined state.
     * <p>
     * If you want to submit a simple command (without a response other than OK/ACK)
     * like pause/play use sendSimpleMPDCommand.
     *
     * @param command Command string to send to the MPD server
     */
    void sendMPDCommand(String command, long responseTimeout) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Send command: " + command);
        }

        // Ensures that the server is not idling before sending the command
        stopIDLE();

        // Acquire lock
        try {
            mConnectionLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Connection lock acquired: " + command);
        }

        synchronized (this) {
            if (mConnectionState != CONNECTION_STATES.READY_FOR_COMMANDS) {
                Log.w(TAG, "Trying to send a command to MPD in wrong state: " + mConnectionState);
                mConnectionLock.release();
                return;
            }
        }

        // Send the MPD command to the server
        writeLine(command);

        changeState(CONNECTION_STATES.WAITING_FOR_RESPONSE);


        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Sent command: " + command);
        }

        // This waits until the server sends a response (OK,ACK(failure) or the requested data)
        try {
            waitForResponse(responseTimeout);
        } catch (IOException e) {
            handleSocketError();
            mConnectionLock.release();
        }
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Sent command, got response: " + command);
        }

    }

    /**
     * Sends a simple command to the MPD server. This method automatically handles the response
     * from the server.
     *
     * @param command Command string to send to the server
     * @throws MPDException if an error during communication with the server occurs.
     */
    void sendSimpleMPDCommand(String command) throws MPDException {
        // Send the command to the server
        sendMPDCommand(command);

        // Read until either OK or ACK is received
        checkResponse();
    }

    /**
     * This functions sends the command to the MPD server.
     * This function is used between start command list and the end. It has no check if the
     * connection is currently idle.
     * Also it will not wait for a response because this would only deadlock, because the mpd server
     * waits until the end_command is received.
     *
     * @param command Command string to send to the server
     */
    void sendMPDRAWCommand(String command) {
        synchronized (this) {
            if (mConnectionState != CONNECTION_STATES.READY_FOR_COMMANDS) {
                return;
            }
        }

        /* Send the command to the server
         * FIXME Should be validated in the future.
         */
        writeLine(command);
    }

    /**
     * This will start a command list to the server. It can be used to speed up multiple requests
     * like adding songs to the current playlist. Make sure that the idle timeout is stopped
     * before starting a command list.
     */
    void startCommandList() {
        // Ensures that the server is not idling before sending the command
        stopIDLE();


        try {
            mConnectionLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        synchronized (this) {
            if (mConnectionState != CONNECTION_STATES.READY_FOR_COMMANDS) {
                Log.w(TAG, "Trying to send a command to MPD in wrong state: " + mConnectionState);
                mConnectionLock.release();
                return;
            }
        }

        // Start the command list by sending the first command to the server
        writeLine(MPDCommands.MPD_START_COMMAND_LIST);
    }

    /**
     * This command will end the command list. After this call it is important to call
     * checkResponse to clear the possible response in the read buffer. There should be at
     * least one "OK" or "ACK" from the mpd server.
     */
    void endCommandList() throws MPDException {
        synchronized (this) {
            if (mConnectionState != CONNECTION_STATES.READY_FOR_COMMANDS) {
                return;
            }
        }

        /*
         * Send the command to the server
         * FIXME Should be validated in the future.
         */
        writeLine(MPDCommands.MPD_END_COMMAND_LIST);
        changeState(CONNECTION_STATES.WAITING_FOR_RESPONSE);
        try {
            waitForResponse();
        } catch (IOException e) {
            handleSocketError();
            mConnectionLock.release();
        }


        // Commandlist is finished. Check for servers response
        checkResponse();
    }


    /**
     * This method needs to be called before a new MPD command is sent to
     * the server to correctly unidle. Otherwise the mpd server will disconnect
     * the disobeying client.
     */
    private synchronized void stopIDLE() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Stop Idling");
        }

        if (mAutoDisconnect && !isConnected()) {
            Log.v(TAG, "Auto reconnect after disconnected");
            try {
                connectToServer();
            } catch (MPDException e) {
                Log.e(TAG, "Error to reconnect to server");
            }
            return;
        }

        cancelIDLEWait();
        // Check if state is idle, otherwise nothing to do
        if (mConnectionState != CONNECTION_STATES.IDLE) {
            // Abort
            return;
        } else {
            changeState(CONNECTION_STATES.GOING_NOIDLE);
        }

        try {
            mSocket.setSoTimeout(SOCKET_TIMEOUT);
        } catch (SocketException e) {
            handleSocketError();
        }

        // Start timeout task
        synchronized (mReadTimeoutTimer) {
            if (mReadTimeoutTask != null) {
                // Another deidling is already running, abort
                return;
            }
            mReadTimeoutTask = new ReadTimeoutTask();
            mReadTimeoutTimer.schedule(mReadTimeoutTask, DEIDLE_TIMEOUT);
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "noidle read timeout scheduled");
            }
        }

        /* Send the "noidle" command to the server to initiate noidle */
        writeLine(MPDCommands.MPD_COMMAND_STOP_IDLE);

        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Sent deidle request");
        }
    }

    /**
     * Initiates the idling procedure. A separate thread is started to wait (blocked)
     * for a deidle from the MPD host. Otherwise it is impossible to get notified on changes
     * from other mpd clients (eg. volume change)
     * <p>
     * Important: This method should only be called with the mConnectionLock acquired. Otherwise
     * undefined behaviour will occur.
     */
    private synchronized void startIDLE() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Start IDLE mode");
        }

        synchronized (this) {
            if (mConnectionState != CONNECTION_STATES.READY_FOR_COMMANDS) {
                // This shouldn't happen, print warning
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "startIDLE called from wrong state:" + mConnectionState);
                }
            } else {
                changeState(CONNECTION_STATES.GOING_IDLE);
            }
        }

        // Set the timeout to zero to block when no data is available
        try {
            if (mSocket != null) {
                mSocket.setSoTimeout(0);
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }


        // This will send the idle command to the server. From there on we need to deidle before
        // sending new requests.
        writeLine(MPDCommands.MPD_COMMAND_START_IDLE);

        synchronized (this) {
            changeState(CONNECTION_STATES.IDLE);
        }

        new IdleThread().start();

        // Notify idle listeners
        for (MPDIdleChangeHandler listener : mIdleListeners) {
            listener.idle();
        }
    }

    private void waitForResponse() throws IOException {
        waitForResponse(RESPONSE_TIMEOUT);
    }

    /**
     * Function only actively waits for reader to get ready for
     * the response.
     */
    private void waitForResponse(long timeOut) throws IOException {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Waiting for response");
        }
        if (null != mSocketInterface) {
            long currentTime = System.nanoTime();

            while (!readyRead()) {
                long compareTime = System.nanoTime() - currentTime;
                // Terminate waiting after waiting to long. This indicates that the server is not responding
                if (compareTime > timeOut) {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "Stuck waiting for server response");
                    }
                    printStackTrace();
                    throw new IOException();
                }
                if (compareTime > 500L * 1000L * 1000L) {
                    SystemClock.sleep(RESPONSE_WAIT_SLEEP_TIME);
                }
            }
        } else {
            throw new IOException();
        }
        if (mConnectionState != CONNECTION_STATES.CONNECTING) {
            changeState(CONNECTION_STATES.RECEIVING);
        }
    }

    /**
     * Checks if a simple command was successful or not (OK vs. ACK)
     * <p>
     * This should only be used for simple commands like play,pause, setVolume, ...
     */
    private void checkResponse() throws MPDException {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Check response");
        }

        // Wait for data to be available to read. MPD communication could take some time.
        while (readyRead()) {
            // It is enough to flush the buffer. readLine will parse the line for OK/ACK and throw an
            // exception.
            readLine();
        }
    }

    /**
     * @return True if connected to MPD server, false otherwise
     */
    synchronized boolean isConnected() {
        return null != mSocket && mSocket.isConnected();
    }


    /**
     * Checks if the socket is ready for read operations
     *
     * @return True if ready
     */
    private boolean readyRead() {
        try {
            return (null != mSocket) && (null != mSocketInterface) && mSocket.isConnected() && mSocketInterface.readReady();
        } catch (IOException e) {
            handleSocketError();
            return false;
        }
    }

    /**
     * Will notify a connected listener that the connection is now ready to be used.
     */
    private void notifyConnected() {
        synchronized (mStateListeners) {
            for (MPDConnectionStateChangeHandler listener : mStateListeners) {
                listener.connected();
            }
        }
    }

    /**
     * Will notify a connected listener that the connection is disconnect and not ready for use.
     */
    private void notifyDisconnect() {
        synchronized (mStateListeners) {
            for (MPDConnectionStateChangeHandler listener : mStateListeners) {
                listener.disconnected();
            }
        }
    }

    /**
     * Registers a listener to be notified about connection state changes
     *
     * @param listener Listener to be connected
     */
    void addConnectionStateChangeHandler(MPDConnectionStateChangeHandler listener) {
        synchronized (mStateListeners) {
            mStateListeners.add(listener);
        }
    }

    /**
     * Unregisters a listener to be notified about connection state changes
     *
     * @param listener Listener to be connected
     */
    void removeConnectionStateChangeHandler(MPDConnectionStateChangeHandler listener) {
        synchronized (mStateListeners) {
            mStateListeners.remove(listener);
        }
    }

    /**
     * Registers a listener to be notified about changes in idle state of this connection.
     *
     * @param listener Listener to register to this connection
     */
    void setIdleListener(MPDIdleChangeHandler listener) {
        synchronized (mIdleListeners) {
            mIdleListeners.add(listener);
        }
    }

    /**
     * This method should only be used by the idling mechanism.
     * It buffers the read line so that the deidle method can check if deidling was successful.
     * To guarantee predictable execution order, the buffer is secured by a semaphore. This ensures,
     * that the read of this waiting thread is always finished before the other handler thread tries
     * to read it.
     *
     * @return String that was sent by the MPD server after idling is over
     */
    private String waitForIdleResponse() throws IOException {
        if (null != mSocketInterface) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Listening for server-side changes");
            }
            // Set thread to sleep, because there should be no line available to read.
            String response = null;
            try {
                response = readLineInternal();
            } catch (MPDException e) {
                handleSocketError();
            }
            return response;
        }
        return "";
    }

    /**
     * Simple private thread class used for handling the idling of MPD.
     * If no line is ready to read, it will suspend itself (blocking readLine() call).
     * If suddenly a line is ready to read it can mean two things:
     * 1. A deidling request notified the server to quit idling.
     * 2. A change in the MPDs internal state changed and the status of this client needs updating.
     */
    private class IdleThread extends Thread {
        @Override
        public void run() {
            String response;
            // Wait for noidle. This should block until the server is ready for commands again
            try {
                response = waitForIdleResponse();
            } catch (IOException e) {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "IOException on waitforIdleResponse!: " + e.getMessage());
                }
                handleSocketError();
                return;
            }

            // Cancel the timeout task
            cancelReadTimeoutWait();

            synchronized (MPDConnection.this) {
                if (mConnectionState != CONNECTION_STATES.GOING_NOIDLE && mConnectionState != CONNECTION_STATES.IDLE) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Timeout during deidle, releasing connection");
                    }

                    // Timeout, abort!
                    mConnectionLock.release();
                    return;
                }
            }


            // Check if noidle was sent or if server changed externally
            if (response.startsWith(MPDResponses.MPD_RESPONSE_CHANGED)) {
                // External change
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "External changes");
                }

                while (!response.equals("OK")) {
                    try {
                        response = readLineInternal();
                    } catch (MPDException e) {
                        e.printStackTrace();
                    }
                }

                changeState(CONNECTION_STATES.READY_FOR_COMMANDS);

                mConnectionLock.release();
                notifyIdleListener();

                scheduleIDLE();
            } else if (response.isEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error during idling");
                }
                handleSocketError();
                mConnectionLock.release();
            } else {
                // Noidle sent
                // Release connection
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "No external change, response: " + response);
                }
                changeState(CONNECTION_STATES.READY_FOR_COMMANDS);
                mConnectionLock.release();
            }
        }
    }

    /**
     * Central method to read a line from the sockets reader
     *
     * @return The read string. null if no data is available.
     */
    @Nullable
    String readLine() throws MPDException {
        if (mSocketInterface != null) {
            String line;
            try {
                line = mSocketInterface.readLine();
            } catch (IOException e) {
                handleSocketError();
                mConnectionLock.release();
                return "";
            }

            if (line.startsWith("ACK")) {
                // Probably detected mopidy. Enable workaround
                if (line.contains(MPDResponses.MPD_PARSE_ARGS_LIST_ERROR) || line.contains(MPDResponses.MPD_UNKNOWN_FILTER_TYPE_ERROR)) {
                    mConnectionLock.release();
                    enableMopidyWorkaround();
                    return null;
                }
                mConnectionLock.release();
                changeState(CONNECTION_STATES.READY_FOR_COMMANDS);
                scheduleIDLE();
                throw new MPDException.MPDServerException(line);
            } else if (line.startsWith("OK")) {
                if (mConnectionState == CONNECTION_STATES.RECEIVING) {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "OK read, releasing connection");
                    }
                    changeState(CONNECTION_STATES.READY_FOR_COMMANDS);
                    mConnectionLock.release();
                    scheduleIDLE();
                }
            }
            return line;
        }
        return null;
    }

    /**
     * Central method to read a key or value from the sockets reader
     *
     * @return The read string. null if no data is available.
     */
    @NonNull
    String readValue() throws MPDException, MPDSocketInterface.NoKeyReadException {
        if (mSocketInterface != null) {
            String keyValue;
            try {
                keyValue = mSocketInterface.readValue();
            } catch (IOException e) {
                handleSocketError();
                mConnectionLock.release();
                return "";
            }

            return keyValue;
        }
        return "";
    }

    @Nullable
    MPDResponses.MPD_RESPONSE_KEY readKey() throws MPDException {
        if (mSocketInterface != null) {
            MPDResponses.MPD_RESPONSE_KEY key;
            try {
                key = mSocketInterface.readKey();
            } catch (IOException e) {
                handleSocketError();
                mConnectionLock.release();
                return null;
            } catch (MPDException mpdException) {
                String errorLine = mpdException.getError();

                // Check for mopidy grouping error signature
                if (errorLine != null && (errorLine.contains(MPDResponses.MPD_PARSE_ARGS_LIST_ERROR) || errorLine.contains(MPDResponses.MPD_UNKNOWN_FILTER_TYPE_ERROR))) {
                    mConnectionLock.release();
                    enableMopidyWorkaround();
                    return null;
                }

                mConnectionLock.release();
                changeState(CONNECTION_STATES.READY_FOR_COMMANDS);
                scheduleIDLE();

                // Forward the error
                throw mpdException;
            }

            if (key == MPDResponses.MPD_RESPONSE_KEY.RESPONSE_OK) {
                if (mConnectionState == CONNECTION_STATES.RECEIVING) {
                    if (BuildConfig.DEBUG) {
                        Log.v(TAG, "OK read, releasing connection");
                    }
                    changeState(CONNECTION_STATES.READY_FOR_COMMANDS);
                    mConnectionLock.release();
                    scheduleIDLE();
                    return null;
                }
            }
            return key;
        }
        return null;
    }


    byte[] readBinary(int size) throws MPDException {
        if (mSocketInterface != null) {
            try {
                return mSocketInterface.readBinary(size);
            } catch (IOException e) {
                handleSocketError();
                return null;
            }
        }
        return new byte[0];
    }

    /**
     * Internal readLine without unlocking of the connection or state changes
     *
     * @return Line that was read from the server
     * @throws MPDException on server-side errors
     */
    private String readLineInternal() throws MPDException {
        if (mSocketInterface != null) {
            String line;
            try {
                line = mSocketInterface.readLine();
            } catch (IOException e) {
                handleSocketError();
                return "";
            }
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Read line internal: " + line);
            }
            if (line == null) {
                return "";
            } else if (line.startsWith("ACK")) {
                throw new MPDException.MPDServerException(line);
            }
            return line;
        }
        return "";
    }

    /**
     * Central method to write a line to the sockets writer. Socket will be flushed afterwards
     * to ensure that the string is sent.
     *
     * @param line String to write to the socket.
     */
    private void writeLine(String line) {
        if (mSocketInterface != null) {
            mSocketInterface.writeLine(line);
            if (BuildConfig.DEBUG) {
                Log.v(TAG, "Write line: " + line);
            }
        }
    }

    /**
     * Debug method to print a stack trace
     */
    private void printStackTrace() {
        StackTraceElement[] st = new Exception().getStackTrace();
        for (StackTraceElement el : st) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, el.toString());
            }
        }
    }

    private void notifyIdleListener() {
        synchronized (mIdleListeners) {
            for (MPDIdleChangeHandler listener : mIdleListeners) {
                listener.noIdle();
            }
        }
    }

    /**
     * This is called if an parse list args error occurs during the parsing
     * of {@link MPDAlbum} or {@link MPDArtist} objects. This probably indicates
     * that this client is connected to Mopidy so we enable a workaround and reconnect
     * to force the GUI to reload the contents.
     */
    private void enableMopidyWorkaround() {
        // Enable the workaround in the capabilities object
        mServerCapabilities.enableMopidyWorkaround();

        // Reconnect to server
        disconnectFromServer();
        try {
            connectToServer();
        } catch (MPDException e) {
            // FIXME what to do?
        }
    }

    /**
     * Schedule to go IDLE after IDLE_WAIT_TIME. This is necessary to hold the TCP connection
     * to MPD and to react to server-side changes.
     */
    private void scheduleIDLE() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Schedule IDLE");
        }

        synchronized (mIDLETimer) {
            if (mIDLETask != null) {
                mIDLETask.cancel();
            }
            int timeout;
            if (!mAutoDisconnect) {
                mIDLETask = new StartIDLETask();
                timeout = IDLE_WAIT_TIME;
            } else {
                mIDLETask = new StartDisconnectTask();
                timeout = AUTO_DISCONNECT_TIME;
            }
            mIDLETimer.schedule(mIDLETask, timeout);
        }
    }

    /**
     * Cancels the scheduled IDLE command after IDLE_WAIT_TIME
     */
    private void cancelIDLEWait() {
        synchronized (mIDLETimer) {
            if (mIDLETask != null) {
                if (BuildConfig.DEBUG) {
                    Log.v(TAG, "Cancel IDLE wait");
                }
                mIDLETask.cancel();
                mIDLETask = null;
            }
        }
    }

    /**
     * Cancels the timeout mechanism for the noidle command
     */
    private void cancelReadTimeoutWait() {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Cancel Read timeout");
        }
        synchronized (mReadTimeoutTimer) {
            if (mReadTimeoutTask != null) {
                mReadTimeoutTask.cancel();
                mReadTimeoutTask = null;
            }
        }
    }

    /**
     * Helper method to debug state changes. This will print an error on state changes
     * that should not occur.
     *
     * @param newState State to set as current
     */
    private synchronized void changeState(CONNECTION_STATES newState) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, "Changing state: " + mConnectionState.name() + " to " + newState.name());

            // Sanity checks
            switch (mConnectionState) {
                case CONNECTING:
                    if (newState != CONNECTION_STATES.DISCONNECTED && newState != CONNECTION_STATES.READY_FOR_COMMANDS && newState != CONNECTION_STATES.DISCONNECTING) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case DISCONNECTING:
                    if (newState != CONNECTION_STATES.DISCONNECTED) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case DISCONNECTED:
                    if (newState != CONNECTION_STATES.CONNECTING && newState != CONNECTION_STATES.DISCONNECTED && newState != CONNECTION_STATES.DISCONNECTING) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case GOING_IDLE:
                    if (newState != CONNECTION_STATES.IDLE) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case IDLE:
                    if (newState != CONNECTION_STATES.READY_FOR_COMMANDS && newState != CONNECTION_STATES.GOING_NOIDLE && newState != CONNECTION_STATES.DISCONNECTING) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case GOING_NOIDLE:
                    if (newState != CONNECTION_STATES.READY_FOR_COMMANDS && newState != CONNECTION_STATES.DISCONNECTED && newState != CONNECTION_STATES.GOING_NOIDLE_TIMEOUT) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case READY_FOR_COMMANDS:
                    if (newState != CONNECTION_STATES.WAITING_FOR_RESPONSE && newState != CONNECTION_STATES.DISCONNECTING && newState != CONNECTION_STATES.GOING_IDLE) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case WAITING_FOR_RESPONSE:
                    if (newState != CONNECTION_STATES.RECEIVING && newState != CONNECTION_STATES.DISCONNECTED) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
                case RECEIVING:
                    if (newState != CONNECTION_STATES.READY_FOR_COMMANDS && newState != CONNECTION_STATES.DISCONNECTED) {
                        Log.e(TAG, "Invalid transition from " + mConnectionState + " to " + newState);
                    }
                    break;
            }
        }
        mConnectionState = newState;
    }

    /**
     * Task to go idle if connection is available. If not the task will be rescheduled
     * when the connection is ready again
     */
    private class StartIDLETask extends TimerTask {
        @Override
        public void run() {
            synchronized (mIDLETimer) {
                if (mIDLETask == null) {
                    // Wait was cancelled.
                    return;
                }
            }

            boolean locked = mConnectionLock.tryAcquire();
            if (locked) {
                startIDLE();
            } else {
                // FIXME Should not be necessary
                //scheduleIDLE();
            }
        }
    }

    private class StartDisconnectTask extends TimerTask {
        @Override
        public void run() {
            synchronized (mIDLETimer) {
                if (mIDLETask == null) {
                    // Wait was cancelled.
                    return;
                }
            }
            Log.v(TAG, "Auto disconnect after time out");
            disconnectFromServer();

        }
    }

    /**
     * Helper class for a task that terminates the noidle command if no response is received from
     * the MPD server. This is necessary as the socket is set to an indefinite timeout before
     * blocking on the readLine method.
     */
    private class ReadTimeoutTask extends TimerTask {

        @Override
        public void run() {
            synchronized (MPDConnection.this) {
                if (mConnectionState != CONNECTION_STATES.GOING_NOIDLE) {
                    // Abort as this is not relevant anymore
                    mReadTimeoutTask = null;
                    return;
                }
                changeState(CONNECTION_STATES.GOING_NOIDLE_TIMEOUT);
            }


            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Timeout on noidle");
            }
            mReadTimeoutTask = null;

            handleSocketError();
        }
    }
}
