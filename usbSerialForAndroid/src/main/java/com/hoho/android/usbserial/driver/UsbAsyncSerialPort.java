package com.hoho.android.usbserial.driver;

import java.io.IOException;

/**
 * Interface representing an asynchronous USB serial port.
 */
public interface UsbAsyncSerialPort {

    /**
     * Puts data to the tail of the queue for asynchronous write operations and exits immediately.
     *
     * @param src the data to be written
     * @throws IOException if an I/O error occurs
     */
    void asyncWrite(final byte[] src) throws IOException;

    /**
     * Prepares the read queue with specified buffer size and count for asynchronous read operations.
     *
     * @param bufferSize the size of each buffer
     * @param bufferCount the number of buffers
     * @throws IOException if an I/O error occurs
     */
    void prepareAsyncReadQueue(final int bufferSize, final int bufferCount) throws IOException;

    /**
     * Peeks the ready buffer from the head of the queue for a read operation. If no buffer is ready, it blocks until data is available.
     *
     * @return the data read from the USB serial port
     * @throws IOException if an I/O error occurs
     */
    byte[] peekReadyReadBuffer() throws IOException;
}