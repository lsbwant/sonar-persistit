/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.persistit;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.spi.AbstractInterruptibleChannel;

/**
 * <p>
 * A {@link FileChannel} implementation that provides different semantics on
 * interrupt. This class wraps an actual FileChannelImpl instance obtained from
 * a {@link RandomAccessFile} and delegates all supported operations to that
 * FileChannelImpl. If a blocking I/O operation, e.g., a read or write, is
 * interrupted, the actual FileChannel instance is closed by its
 * {@link AbstractInterruptibleChannel} superclass. The result is that the
 * interrupted thread receives a {@link ClosedByInterruptException}, and other
 * threads concurrently or subsequently reading or writing the same channel
 * receive {@link ClosedChannelException}s.
 * </p>
 * <p>
 * However, this mediator class class catches the
 * <code>ClosedChannelException</code>, and implicitly opens a FileChanel if the
 * client did not actually call {@link #close()}. If the operation that threw
 * the <code>ClosedChannelException</code> was on an interrupted thread then the
 * method that received the exception throws a {@link InterruptedIOException}
 * after re-opening the channel. Otherwise the method retries the operation
 * using the new channel.
 * </p>
 * <p>
 * To maintain the <code>FileChannel</code> contract, methods of this class may
 * only throw an <code>IOException</code>. Therefore, to signify a interrupt,
 * this method throws <code>InteruptedIOException</code> rather than
 * <code>InterruptedException</code>.
 * </p>
 * <p>
 * A number of methods of <code>FileChannel</code> including all methods that
 * depend on the channel's file position, are unsupported and throw
 * {@link UnsupportedOperationException}s.
 * </p>
 * 
 * @author peter
 * 
 */
class MediatedFileChannel extends FileChannel {

    private final static String LOCK_EXTENSION = ".lck";

    final File _file;
    final File _lockFile;
    final String _mode;

    volatile FileChannel _channel;
    volatile FileChannel _lockChannel;

    volatile IOException _injectedIOException;
    volatile String _injectedIOExceptionFlags;

    MediatedFileChannel(final String path, final String mode) throws IOException {
        this(new File(path), mode);
    }

    MediatedFileChannel(final File file, final String mode) throws IOException {
        _file = file;
        _lockFile = new File(file.getParentFile(), file.getName() + LOCK_EXTENSION);
        _mode = mode;
        openChannel();
    }

    /**
     * Handles <code>ClosedChannelException</code> and its subclasses
     * <code>AsynchronousCloseException</code> and
     * <code>ClosedByInterruptException</code>. Empirically we determined (and
     * by reading {@link AbstractInterruptibleChannel}) that an interrupted
     * thread can throw either <code>ClosedChannelException</code> or
     * <code>ClosedByInterruptException</code>. Therefore we simply use the
     * interrupted state of the thread itself to determine whether the Exception
     * occurred due to an interrupt on the current thread.
     * 
     * @param cce
     *            A ClosedChannelException
     * @throws IOException
     *             if (a) the attempt to reopen a new channel fails, or (b) the
     *             current thread was in fact interrupted.
     */
    private void handleClosedChannelException(final ClosedChannelException cce) throws IOException {
        /*
         * The ClosedChannelException may have occurred because the client
         * actually called close. In that event throwing the original exception
         * is correct.
         */
        if (!isOpen()) {
            throw cce;
        }
        /*
         * Open a new inner FileChannel
         */
        openChannel();
        /*
         * Behavior depends on whether this thread was originally the
         * interrupted thread. If so then throw an InterruptedIOException which
         * wraps the original exception. Otherwise return normally so that the
         * while-loops in the methods below can retry the I/O operation using
         * the new FileChannel.
         */
        if (Thread.interrupted()) {
            final InterruptedIOException iioe = new InterruptedIOException();
            iioe.initCause(cce);
            throw iioe;
        }
    }

    /**
     * Attempt to open a real FileChannel. This method is synchronized and
     * checks the status of the existing channel because multiple threads might
     * receive AsynchronousCloseException
     * 
     * @throws IOException
     */
    private synchronized void openChannel() throws IOException {
        if (isOpen() && (_channel == null || !_channel.isOpen())) {
            injectFailure('o');
            _channel = new RandomAccessFile(_file, _mode).getChannel();
        }
    }

    private void injectFailure(final char type) throws IOException {
        final IOException e = _injectedIOException;
        if (e != null && _injectedIOExceptionFlags.indexOf(type) >= 0) {
            throw e;
        }
    }

    /**
     * Set an IOException to be thrown on subsequent I/O operations. This method
     * is intended for use only for unit tests. The <code>flags</code> parameter
     * determines which I/O operations throw exceptions:
     * <ul>
     * <li>o - open</li>
     * <li>c - close</li>
     * <li>r - read</li>
     * <li>w - write</li>
     * <li>f - force</li>
     * <li>t - truncate</li>
     * <li>l - lock</li>
     * <li>e - extending Volume file</li>
     * </ul>
     * For example, if flags is "wt" then write and truncate operations with
     * throw the injected IOException.
     * 
     * @param exception
     *            The IOException to throw
     * @param flags
     *            Selected operations
     */
    void injectTestIOException(final IOException exception, final String flags) {
        _injectedIOException = exception;
        _injectedIOExceptionFlags = flags;
    }

    /*
     * --------------------------------
     * 
     * Implementations of these FileChannel methods simply delegate to the inner
     * FileChannel. But they retry upon receiving a ClosedChannelException
     * caused by an I/O operation on a different thread having been interrupted.
     * 
     * --------------------------------
     */
    @Override
    public void force(boolean metaData) throws IOException {
        while (true) {
            try {
                injectFailure('f');
                _channel.force(metaData);
                break;
            } catch (ClosedChannelException e) {
                handleClosedChannelException(e);
            }
        }
    }

    @Override
    public int read(ByteBuffer byteBuffer, long position) throws IOException {
        final int offset = byteBuffer.position();
        while (true) {
            try {
                injectFailure('r');
                return _channel.read(byteBuffer, position);
            } catch (ClosedChannelException e) {
                handleClosedChannelException(e);
            }
            byteBuffer.position(offset);
        }
    }

    @Override
    public long size() throws IOException {
        while (true) {
            try {
                injectFailure('s');
                return _channel.size();
            } catch (ClosedChannelException e) {
                handleClosedChannelException(e);
            }
        }
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
        while (true) {
            try {
                injectFailure('t');
                return _channel.truncate(size);
            } catch (ClosedChannelException e) {
                handleClosedChannelException(e);
            }
        }
    }

    @Override
    public synchronized FileLock tryLock(long position, long size, boolean shared) throws IOException {
        if (_lockChannel == null) {
            injectFailure('l');
            try {
                _lockChannel = new RandomAccessFile(_lockFile, "rw").getChannel();
            } catch (IOException ioe) {
                if (!shared) {
                    throw ioe;
                } else {
                    /*
                     * Read-only volume, probably failed to create a lock file
                     * due to permissions. We'll assume that means no other
                     * process could be modifying the corresponding volume file.
                     */
                }
            }
        }
        return _lockChannel.tryLock(position, size, shared);
    }

    @Override
    public int write(ByteBuffer byteBuffer, long position) throws IOException {
        final int offset = byteBuffer.position();
        while (true) {
            try {
                injectFailure('w');
                if (byteBuffer.remaining() == 1) {
                    injectFailure('e');
                }
                return _channel.write(byteBuffer, position);
            } catch (ClosedChannelException e) {
                handleClosedChannelException(e);
            }
            byteBuffer.position(offset);
        }
    }

    /**
     * Implement closing of this <code>MediatedFileChannel</code> by closing the
     * real channel and setting the <code>_reallyClosed</code> flag. The flag
     * prevents another thread from performing an {@link #openChannel()}
     * operation after this thread has closed the channel.
     */
    @Override
    protected synchronized void implCloseChannel() throws IOException {
        try {
            IOException exception = null;
            try {
                if (_lockChannel != null) {
                    _lockFile.delete();
                    _lockChannel.close();
                }
            } catch (IOException e) {
                exception = e;
            }
            try {
                if (_channel != null) {
                    _channel.close();
                    injectFailure('c');
                }
            } catch (IOException e) {
                exception = e;
            }
            if (exception != null) {
                throw exception;
            }
        } catch (ClosedChannelException e) {
            // ignore - whatever, the channel is closed
        }
    }

    /*
     * --------------------------------
     * 
     * Persistit does not use these methods and so they are Unsupported. Note
     * that it would be difficult to support the relative read/write methods
     * because the channel size is unavailable after it is closed. Therefore a
     * client of this class must maintain its own position counter and cannot
     * use the relative-addressing calls.
     * 
     * --------------------------------
     */
    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public MappedByteBuffer map(MapMode arg0, long arg1, long arg2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long position() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileChannel position(long arg0) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(ByteBuffer byteBuffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long read(ByteBuffer[] arg0, int arg1, int arg2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferFrom(ReadableByteChannel arg0, long arg1, long arg2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long transferTo(long arg0, long arg1, WritableByteChannel arg2) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int write(ByteBuffer byteBuffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long write(ByteBuffer[] arg0, int arg1, int arg2) throws IOException {
        throw new UnsupportedOperationException();
    }

}
