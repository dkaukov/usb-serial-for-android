package com.hoho.android.usbserial.driver;

import java.io.IOException;

/**
 * Interface representing an asynchronous USB serial port.
 */
public interface UsbAsyncSerialPort {

    /**
     * Writes data asynchronously to the USB serial port.
     *
     * @param src the data to be written
     * @throws IOException if an I/O error occurs
     */
    void asyncWrite(final byte[] src) throws IOException;

    /**
     * Begins an asynchronous read operation with specified buffer size and count.
     *
     * @param bufferSize the size of each buffer
     * @param bufferCount the number of buffers
     * @throws IOException if an I/O error occurs
     */
    void asyncReadBegin(final int bufferSize, final int bufferCount) throws IOException;

    /**
     * Reads data asynchronously from the USB serial port.
     *
     * @return the data read from the USB serial port
     * @throws IOException if an I/O error occurs
     */
    byte[] asyncRead() throws IOException;
}