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

package org.gateshipone.malp.mpdservice.mpdprotocol;

import android.util.Log;

import org.gateshipone.malp.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * Class to handle reads and writes to the socket connected to MPD.
 * This class can be used to read Strings and binary data from MPD.
 */
public class MPDSocketInterface {
    private static final String TAG = MPDSocketInterface.class.getSimpleName();
    /**
     * Buffered input stream to improve the performance
     */
    private final InputStream mInputStream;

    /**
     * Object to write to the socket
     */
    private final PrintWriter mWriter;

    private static final int READ_BUFFER_SIZE = 4 * 1024; // 4 kB

    private final byte[] mReadBuffer;

    private int mReadBufferWritePos;
    private int mReadBufferReadPos;

    private final ByteArrayOutputStream mLineBuffer;


    /**
     * Creates a new socket interface
     *
     * @param inputStream  Input stream from the socket to use
     * @param outputStream Output stream from the socket to use
     */
    public MPDSocketInterface(InputStream inputStream, OutputStream outputStream) {
        mInputStream = inputStream;

        mWriter = new PrintWriter(outputStream);
        mReadBuffer = new byte[READ_BUFFER_SIZE];

        mReadBufferReadPos = 0;
        mReadBufferWritePos = 0;

        mLineBuffer = new ByteArrayOutputStream();
    }


    /**
     * Reads as much as possible data from the socket into its buffer.
     * Both pointers are reset, ensure to only call this on an empty buffer or data will
     * be lost!
     *
     * @throws IOException
     */
    private void fillReadBuffer() throws IOException {
        mReadBufferWritePos = mInputStream.read(mReadBuffer, 0, READ_BUFFER_SIZE);
        mReadBufferReadPos = 0;
    }

    private int dataReady() {
        return mReadBufferWritePos - mReadBufferReadPos;
    }

    /**
     * Reads a line from the buffered input
     *
     * @return The read string without the newline
     * @throws IOException Exception during read
     */
    public String readLine() throws IOException {
        mLineBuffer.reset();

        int localReadPos = mReadBufferReadPos;
        // Read until newline
        while (true) {
            // End of buffer reached
            if (localReadPos == mReadBufferWritePos) {
                // Copy what we've read so far to the string buffer
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));

                fillReadBuffer();
                localReadPos = 0;
                continue;
            }

            // Newline found, write buffer and break loop here
            if (mReadBuffer[localReadPos] == '\n') {
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));
                mReadBufferReadPos = localReadPos + 1;
                break;
            }

            localReadPos++;
        }

        // Return the string data from MPD as UTF-8 (default charset on android) strings
        return mLineBuffer.toString("UTF-8");
    }

    private boolean mKeyRead = false;
    private boolean mValueRead = true;

    /**
     * Method reads the key from MPDS "key: value" response.
     * On "ACK [..." which is signalled on MPD's errors it aborts.
     *
     * @return
     * @throws IOException  If a general IO error such as time out occurs
     * @throws MPDException Thrown when an ACK is received
     */
    public MPDResponses.MPD_RESPONSE_KEY readKey() throws IOException, MPDException {
        // Previous value was not read, read to next newline
        if (!mValueRead) {
            Log.e(TAG, "Key read without fetching value first. Data may be lost");
            readLine();
        }
        mValueRead = false;
        mLineBuffer.reset();

        int localReadPos = mReadBufferReadPos;
        // Read until newline
        while (true) {
            // End of buffer reached
            if (localReadPos == mReadBufferWritePos) {
                // Copy what we've read so far to the string buffer
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));

                fillReadBuffer();
                localReadPos = 0;
                continue;
            }

            if (mReadBuffer[localReadPos] == ':') {
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));
                mReadBufferReadPos = localReadPos;
                break;
            }
            // Newline found, write buffer and break loop here
            if (mReadBuffer[localReadPos] == '\n') {
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));
                mReadBufferReadPos = localReadPos + 1;
                break;
            }

            localReadPos++;
        }

        mKeyRead = true;
        String key = mLineBuffer.toString("UTF-8");

        if (key.startsWith("ACK")) {
            mValueRead = true;
            // MPD error occurred, prepare MPDException here
            throw new MPDException(key);
        }

        MPDResponses.MPD_RESPONSE_KEY keyEnum = MPDResponses.RESPONSE_KEYMAP.get(key);
        if (keyEnum == null) {
            keyEnum = MPDResponses.MPD_RESPONSE_KEY.RESPONSE_UNKNOWN;
        }

        // If we read OK no key follows.
        if (key.equals("OK")) {
            mValueRead = true;
            mKeyRead = false;
        }
        // Return the string data from MPD as UTF-8 (default charset on android) strings
        return keyEnum;
    }

    /**
     * This function reads the value that is behind a key, that should have been read before calling
     * this function. Otherwise a NoKeyReadException will be thrown.
     *
     * @return
     * @throws IOException        If a general IO error such as time out occurs
     * @throws NoKeyReadException If Value is tried to be read without reading the key before
     */
    public String readValue() throws IOException, NoKeyReadException {
        if (!mKeyRead) {
            throw new NoKeyReadException();
        }
        boolean whiteSpacesHandled = false;
        mKeyRead = false;
        mLineBuffer.reset();

        int skipChars = 0;

        int localReadPos = mReadBufferReadPos;
        // Read until newline
        while (true) {
            // Skip initial spaces as separators
            if (!whiteSpacesHandled && localReadPos == mReadBufferWritePos) {
                // Skip data and refresh buffer
                fillReadBuffer();
                localReadPos = 0;
                skipChars = 0;
                continue;
            } else if (!whiteSpacesHandled && ((mReadBuffer[localReadPos] == ' ') || (mReadBuffer[localReadPos] == ':'))) {
                skipChars++;
            } else if (!whiteSpacesHandled && mReadBuffer[localReadPos] != ' ') {
                mReadBufferReadPos += skipChars;
                skipChars = 0;
                whiteSpacesHandled = true;
            }

            // End of buffer reached
            if (localReadPos == mReadBufferWritePos) {
                // Copy what we've read so far to the string buffer
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));

                fillReadBuffer();
                localReadPos = 0;
                continue;
            }

            // Newline found, write buffer and break loop here
            if (mReadBuffer[localReadPos] == '\n') {
                mLineBuffer.write(mReadBuffer, mReadBufferReadPos, (localReadPos - mReadBufferReadPos));
                mReadBufferReadPos = localReadPos + 1;
                break;
            }

            localReadPos++;
        }

        mValueRead = true;
        // Return the string data from MPD as UTF-8 (default charset on android) strings
        String value = mLineBuffer.toString("UTF-8");
        // Return the string data from MPD as UTF-8 (default charset on android) strings
        return value;
    }

    /**
     * @return True if data is ready to be read, false otherwise
     * @throws IOException Exception during read
     */
    public boolean readReady() throws IOException {
        return dataReady() > 0 || mInputStream.available() > 0;
    }

    /**
     * Reads binary data from the socket
     *
     * @param size size to read from the socket in bytes
     * @return byte array if data is correctly read
     * @throws IOException Exception during read
     */
    public byte[] readBinary(int size) throws IOException {
        byte[] data = new byte[size];

        int dataRead = 0;

        int dataToRead = 0;
        int readyData = 0;
        while (dataRead < size) {
            readyData = dataReady();

            // Check how much data is necessary to read (do not read more data than requested!)
            dataToRead = Math.min(readyData, (size - dataRead));

            // Read data that is ready or requested
            System.arraycopy(mReadBuffer, mReadBufferReadPos, data, dataRead, dataToRead);
            dataRead += dataToRead;
            mReadBufferReadPos += dataToRead;

            // Check if the data buffer is depleted
            if (dataReady() == 0 && dataRead != size) {
                fillReadBuffer();
            }
        }

        // Skip one byte to catch last newline
        mReadBufferReadPos++;

        // Read last newline from MPD (s. https://www.musicpd.org/doc/protocol/database.html - command
        // albumart)
        return data;
    }

    /**
     * Writes a line to the socket.
     *
     * @param line String to write to the socket. No newline required.
     */
    public void writeLine(String line) {
        mWriter.println(line);
        mWriter.flush();
    }

    public static class NoKeyReadException extends Exception {

    }
}
