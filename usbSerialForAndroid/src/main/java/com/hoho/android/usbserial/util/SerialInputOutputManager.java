/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.util;

import android.os.Process;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbAsyncSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class which services a {@link UsbSerialPort} in its  {@link #runRead()} ()} ()} methods.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialInputOutputManager {

    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING
    }

    public static boolean DEBUG = false;

    private static final String TAG = SerialInputOutputManager.class.getSimpleName();

    private int mReadBufferSize; // default size = getReadEndpoint().getMaxPacketSize()
    private int mReadBufferCount = 4;

    private int mThreadPriority = Process.THREAD_PRIORITY_URGENT_AUDIO;
    private final AtomicReference<State> mState = new AtomicReference<>(State.STOPPED);
    private CountDownLatch mStartuplatch = new CountDownLatch(2);
    private CountDownLatch mShutdownlatch = new CountDownLatch(2);
    private Listener mListener; // Synchronized by 'this'
    private final UsbAsyncSerialPort mSerialPort;

    public interface Listener {
        /**
         * Called when new incoming data is available.
         */
        void onNewData(byte[] data);

        /**
         * Called when {@link SerialInputOutputManager#runRead()} ()}  aborts due to an error.
         */
        void onRunError(Exception e);
    }

    public SerialInputOutputManager(UsbSerialPort serialPort) {
        mSerialPort = serialPort.asAsync();
        mReadBufferSize = serialPort.getReadEndpoint().getMaxPacketSize();
    }

    public SerialInputOutputManager(UsbSerialPort serialPort, Listener listener) {
        mSerialPort = serialPort.asAsync();
        mListener = listener;
        mReadBufferSize = serialPort.getReadEndpoint().getMaxPacketSize();
    }

    public synchronized void setListener(Listener listener) {
        mListener = listener;
    }

    public synchronized Listener getListener() {
        return mListener;
    }

    /**
     * setThreadPriority. By default a higher priority than UI thread is used to prevent data loss
     *
     * @param threadPriority  see {@link Process#setThreadPriority(int)}
     * */
    public void setThreadPriority(int threadPriority) {
        if (!mState.compareAndSet(State.STOPPED, State.STOPPED)) {
            throw new IllegalStateException("threadPriority only configurable before SerialInputOutputManager is started");
        }
        mThreadPriority = threadPriority;
    }

    /**
     * read buffer size
     */
    public void setReadBufferSize(int bufferSize) {
        mReadBufferSize = bufferSize;
    }

    public int getReadBufferSize() {
        return mReadBufferSize;
    }

    public int getReadBufferCount() {
        return mReadBufferCount;
    }

    public void setReadBufferCount(int readBufferCount) {
        this.mReadBufferCount = readBufferCount;
    }

    /**
     * Write data to the serial port
     *
     * @param data the data to write
     * @throws IOException on error writing data
     */
    public void writeAsync(byte[] data) throws IOException {
        mSerialPort.asyncWrite(data);
    }

    /**
     * start SerialInputOutputManager in separate threads
     */
    public void start() {
        if(mState.compareAndSet(State.STOPPED, State.STARTING)) {
            mStartuplatch = new CountDownLatch(1);
            mShutdownlatch = new CountDownLatch(1);
            new Thread(this::runRead, this.getClass().getSimpleName() + "_read").start();
            try {
                mStartuplatch.await();
                mState.set(State.RUNNING);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            throw new IllegalStateException("already started");
        }
    }

    /**
     * stop SerialInputOutputManager threads
     *
     * when using readTimeout == 0 (default), additionally use usbSerialPort.close() to
     * interrupt blocking read
     */
    public void stop() {
        if(mState.compareAndSet(State.RUNNING, State.STOPPING)) {
            Log.i(TAG, "Stop requested");
        }
    }

    public State getState() {
        return mState.get();
    }

    /**
     * @return true if the thread is still running
     */
    private boolean isStillRunning() {
        State state = mState.get();
        return ((state == State.RUNNING) || (state == State.STARTING))
            && (mShutdownlatch.getCount() == 1)
            && !Thread.currentThread().isInterrupted();
    }

    /**
     * Notify listener of an error
     *
     * @param e the exception
     */
    private void notifyErrorListener(Throwable e) {
        Listener listener = getListener();
        if (listener != null) {
            try {
                listener.onRunError(e instanceof Exception ? (Exception) e : new Exception(e));
            } catch (Throwable t) {
                Log.w(TAG, "Exception in onRunError: " + t.getMessage(), t);
            }
        }
    }

    /**
     * Set the thread priority
     */
    private void setThreadPriority() {
        if (mThreadPriority != Process.THREAD_PRIORITY_DEFAULT) {
            Process.setThreadPriority(mThreadPriority);
        }
    }

    /**
     * Continuously services the read buffers until {@link #stop()} is called, or until a driver exception is
     * raised.
     */
    public void runRead() {
        Log.i(TAG, "runRead running ...");
        try {
            setThreadPriority();
            mStartuplatch.countDown();
            mSerialPort.prepareAsyncReadQueue(mReadBufferSize, mReadBufferCount);
            do {
                stepRead();
            } while (isStillRunning());
            Log.i(TAG, "runRead: Stopping mState=" + getState());
        } catch (Throwable e) {
            if (Thread.currentThread().isInterrupted()) {
                Log.w(TAG, "Thread interrupted, stopping runRead.");
            } else {
                Log.w(TAG, "runRead ending due to exception: " + e.getMessage(), e);
                notifyErrorListener(e);
            }
        } finally {
            if (!mState.compareAndSet(State.RUNNING, State.STOPPING)) {
                if (mState.compareAndSet(State.STOPPING, State.STOPPED)) {
                    Log.i(TAG, "runRead: Stopped mState=" + getState());
                }
            }
            mShutdownlatch.countDown();
        }
    }

    private void stepRead() throws IOException {
        byte[] buffer = mSerialPort.peekReadyReadBuffer();
        if (buffer.length > 0) {
            if (DEBUG) {
                Log.d(TAG, "Read data len=" + buffer.length);
            }
            final Listener listener = getListener();
            if (listener != null) {
                listener.onNewData(buffer);
            }
        }
    }

}
