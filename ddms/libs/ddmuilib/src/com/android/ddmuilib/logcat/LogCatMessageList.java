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

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Container for a list of log messages. The list of messages are
 * maintained in a circular buffer (FIFO).
 */
public final class LogCatMessageList {
    /** Size of the FIFO.
     * FIXME: this should be a user preference.
     */
    private static final int MAX_MESSAGES = 1000;

    private Queue<LogCatMessage> mQ;
    private LogCatMessage[] mQArray;

    /**
     * Construct an empty message list.
     */
    public LogCatMessageList() {
        mQ = new ArrayBlockingQueue<LogCatMessage>(MAX_MESSAGES);
        mQArray = new LogCatMessage[MAX_MESSAGES];
    }

    /**
     * Append a message to the list. If the list is full, the first
     * message will be popped off of it.
     * @param m log to be inserted
     */
    public synchronized void appendMessage(final LogCatMessage m) {
        if (mQ.size() == MAX_MESSAGES) {
            /* make space by removing the first entry */
            mQ.poll();
        }
        mQ.offer(m);
    }

    /**
     * Obtain all the messages currently present in the list.
     * @return array containing all the log messages
     */
    public Object[] toArray() {
        if (mQ.size() == MAX_MESSAGES) {
            /*
             * Once the queue is full, it stays full until the user explicitly clears
             * all the logs. Optimize for this case by not reallocating the array.
             */
            return mQ.toArray(mQArray);
        }
        return mQ.toArray();
    }
}
