/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Log {@link Handler} that stores the log records into a ring buffer.
 *
 * @author Kohsuke Kawaguchi
 */
public class RingBufferLogHandler extends Handler {

    private static final int DEFAULT_RING_BUFFER_SIZE = Integer.getInteger(RingBufferLogHandler.class.getName() + ".defaultSize", 256);

    private static final class LogRecordRef extends SoftReference<LogRecord> {
        LogRecordRef(LogRecord referent) {
            super(referent);
        }
    }

    private int start = 0;
    private final LogRecordRef[] records;
    private int size;

    /**
     * This constructor is deprecated. It can't access system properties with {@link jenkins.util.SystemProperties}
     * as it's not legal to use it on remoting agents.
     * @deprecated use {@link #RingBufferLogHandler(int)}
     */
    @Deprecated
    public RingBufferLogHandler() {
        this(DEFAULT_RING_BUFFER_SIZE);
    }

    public RingBufferLogHandler(int ringSize) {
        records = new LogRecordRef[ringSize];
    }

    /**
     * @return int DEFAULT_RING_BUFFER_SIZE
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-50669">JENKINS-50669</a>
     * @since 2.259
     */
    public static int getDefaultRingBufferSize() {
        return DEFAULT_RING_BUFFER_SIZE;
    }

    @Override
    public synchronized void publish(LogRecord record) {
        int len = records.length;
        records[(start + size) % len] = new LogRecordRef(record);
        if (size == len) {
            start = (start+1)%len;
        } else {
            size++;
        }
    }

    public synchronized void clear() {
        size = 0;
        start = 0;
    }

    /**
     * Returns the list view of {@link LogRecord}s in the ring buffer.
     *
     * <p>
     * New records are always placed early in the list.
     */
    public synchronized List<LogRecord> getView() {
        List<LogRecord> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            LogRecord lr = records[(start + i) % records.length].get();
            if (lr != null) {
                result.add(lr);
            }
        }
        Collections.reverse(result);
        return result;
    }

    // noop
    @Override
    public void flush() {}
    @Override
    public void close() throws SecurityException {}
}
