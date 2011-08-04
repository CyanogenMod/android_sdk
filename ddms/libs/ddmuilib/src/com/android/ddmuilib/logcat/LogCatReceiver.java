/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmuilib.logcat;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * A class to monitor a device for logcat messages. It stores the received
 * log messages in a circular buffer.
 */
public final class LogCatReceiver {
    private static final String LOGCAT_COMMAND = "logcat -v long";
    private static final int DEVICE_POLL_INTERVAL_MSEC = 1000;

    private LogCatMessageList mLogMessages;
    private IDevice mCurrentDevice;
    private LogCatOutputReceiver mCurrentLogCatOutputReceiver;
    private List<ILogCatMessageEventListener> mLogCatMessageListeners;
    private LogCatMessageParser mLogCatMessageParser;

    /**
     * Construct a LogCat message receiver.
     */
    public LogCatReceiver() {
        mLogCatMessageListeners = new ArrayList<ILogCatMessageEventListener>();
        mLogCatMessageParser = new LogCatMessageParser();
    }

    /**
     * Stop receiving messages from currently active device.
     */
    public void stop() {
        if (mCurrentLogCatOutputReceiver != null) {
            /* stop the current logcat command */
            mCurrentLogCatOutputReceiver.mIsCancelled = true;
            mCurrentLogCatOutputReceiver = null;
        }

        mLogMessages = null;
        mCurrentDevice = null;
    }

    /**
     * Start monitoring a device for logcat messages. This will launch a
     * logcat command on the device, and monitor the output of that command in
     * a separate thread. All logcat messages are then stored in a circular
     * buffer, which can be retrieved using {@link LogCatReceiver#getMessages()}.
     * @param device device to monitor for logcat messages
     */
    public void start(IDevice device) {
        if (device == null) {
            return;
        }

        if (mCurrentDevice == device) {
            return;
        }

        /* stop currently active listeners before starting a new one. */
        if (mCurrentDevice != null) {
            stop();
        }

        mCurrentDevice = device;
        mLogMessages = new LogCatMessageList();

        mLogCatMessageParser.resetState();
        startReceiverThread();
    }

    private void startReceiverThread() {
        mCurrentLogCatOutputReceiver = new LogCatOutputReceiver();

        Thread t = new Thread(new Runnable() {
            public void run() {
                /* wait while the device comes online */
                while (!mCurrentDevice.isOnline()) {
                    try {
                        Thread.sleep(DEVICE_POLL_INTERVAL_MSEC);
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                try {
                    mCurrentDevice.executeShellCommand(LOGCAT_COMMAND,
                            mCurrentLogCatOutputReceiver, 0);
                } catch (Exception e) {
                    /* There are 4 possible exceptions: TimeoutException,
                     * AdbCommandRejectedException, ShellCommandUnresponsiveException and
                     * IOException. In case of any of them, the only recourse is to just
                     * log this unexpected situation and move on.
                     */
                    Log.e("Unexpected error while launching logcat. Try reselecting the device.",
                            e);
                }
            }
        });
        t.setName("LogCat output receiver for " + mCurrentDevice.getSerialNumber());
        t.start();
    }

    /**
     * LogCatOutputReceiver implements {@link MultiLineReceiver#processNewLines(String[])},
     * which is called whenever there is output from logcat. It simply redirects this output
     * to {@link LogCatReceiver#processLogLines(String[])}. This class is expected to be
     * used from a different thread, and the only way to stop that thread is by using the
     * {@link LogCatOutputReceiver#mIsCancelled} variable.
     * See {@link IDevice#executeShellCommand(String, IShellOutputReceiver, int)} for more
     * details.
     */
    private class LogCatOutputReceiver extends MultiLineReceiver {
        private boolean mIsCancelled;

        public LogCatOutputReceiver() {
            setTrimLine(false);
        }

        /** Implements {@link IShellOutputReceiver#isCancelled() }. */
        public boolean isCancelled() {
            return mIsCancelled;
        }

        @Override
        public void processNewLines(String[] lines) {
            if (!mIsCancelled) {
                processLogLines(lines);
            }
        }
    }

    private void processLogLines(String[] lines) {
        List<LogCatMessage> messages = mLogCatMessageParser.processLogLines(lines);

        if (messages.size() > 0) {
            for (LogCatMessage m: messages) {
                mLogMessages.appendMessage(m);
            }
            sendMessageReceivedEvent();
        }
    }

    /**
     * Get the list of logcat messages received from currently active device.
     * @return list of messages if currently listening, null otherwise
     */
    public LogCatMessageList getMessages() {
        return mLogMessages;
    }

    /**
     * Add to list of message event listeners.
     * @param l listener to notified when messages are received from the device
     */
    public void addMessageReceivedEventListener(ILogCatMessageEventListener l) {
        mLogCatMessageListeners.add(l);
    }

    private void sendMessageReceivedEvent() {
        for (ILogCatMessageEventListener l: mLogCatMessageListeners) {
            l.messageReceived();
        }
    }
}
