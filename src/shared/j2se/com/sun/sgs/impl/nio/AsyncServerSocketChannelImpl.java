/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Callable;

import com.sun.sgs.nio.channels.AlreadyBoundException;
import com.sun.sgs.nio.channels.AsynchronousServerSocketChannel;
import com.sun.sgs.nio.channels.AsynchronousSocketChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.SocketOption;
import com.sun.sgs.nio.channels.StandardSocketOption;

import static java.nio.channels.SelectionKey.OP_ACCEPT;

/**
 * TODO doc
 */
final class AsyncServerSocketChannelImpl
    extends AsynchronousServerSocketChannel
{
    /** The valid socket options for this channel. */
    private static final Set<SocketOption> socketOptions;
    static {
        Set<? extends SocketOption> es = EnumSet.of(
            StandardSocketOption.SO_RCVBUF,
            StandardSocketOption.SO_REUSEADDR);
        socketOptions = Collections.unmodifiableSet(es);
    }

    /** The channel group. */
    final AsyncGroupImpl group;

    /** The underlying {@code ServerSocketChannel}. */
    final ServerSocketChannel channel;

    /** The {@code AsyncKey} for the underlying channel. */
    final AsyncKey key;

    /**
     * TODO doc
     * @param group 
     * @throws IOException 
     */
    AsyncServerSocketChannelImpl(AsyncGroupImpl group)
        throws IOException
    {
        super(group.provider());
        this.group = group;
        channel = group.selectorProvider().openServerSocketChannel();
        key = group.register(channel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return super.toString() + ":" + key;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isOpen() {
        return channel.isOpen();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        key.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl bind(SocketAddress local,
                                             int backlog)
        throws IOException
    {
        if ((local != null) && (!(local instanceof InetSocketAddress)))
            throw new UnsupportedAddressTypeException();

        InetSocketAddress inetLocal = (InetSocketAddress) local;
        if ((inetLocal != null) && inetLocal.isUnresolved())
            throw new UnresolvedAddressException();

        final ServerSocket socket = channel.socket();

        try {
            socket.bind(local, backlog);
        } catch (SocketException e) {
            if (socket.isBound())
                throw new AlreadyBoundException();
            if (socket.isClosed())
                throw new ClosedChannelException();
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public SocketAddress getLocalAddress() throws IOException {
        return channel.socket().getLocalSocketAddress();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AsyncServerSocketChannelImpl setOption(SocketOption name, Object value)
        throws IOException
    {
        if (! (name instanceof StandardSocketOption))
            throw new IllegalArgumentException("Unsupported option " + name);

        if (value == null || !name.type().isAssignableFrom(value.getClass()))
            throw new IllegalArgumentException("Bad parameter for " + name);

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final ServerSocket socket = channel.socket();
        
        try {
            switch (stdOpt) {
            case SO_RCVBUF:
                socket.setReceiveBufferSize(((Integer)value).intValue());
                break;

            case SO_REUSEADDR:
                socket.setReuseAddress(((Boolean)value).booleanValue());
                break;

            default:
                throw new IllegalArgumentException("Unsupported option " + name);
            }
        } catch (SocketException e) {
            if (socket.isClosed())
                throw new ClosedChannelException();
            throw e;
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Object getOption(SocketOption name) throws IOException {
        if (! (name instanceof StandardSocketOption))
            throw new IllegalArgumentException("Unsupported option " + name);

        StandardSocketOption stdOpt = (StandardSocketOption) name;
        final ServerSocket socket = channel.socket();
        
        try {
            switch (stdOpt) {
            case SO_RCVBUF:
                return socket.getReceiveBufferSize();

            case SO_REUSEADDR:
                return socket.getReuseAddress();

            default:
                throw new IllegalArgumentException("Unsupported option " + name);
            }
        } catch (SocketException e) {
            if (socket.isClosed())
                throw new ClosedChannelException();
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    public Set<SocketOption> options() {
        return socketOptions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAcceptPending() {
        return key.isOpPending(OP_ACCEPT);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <A> IoFuture<AsynchronousSocketChannel, A> accept(
            A attachment,
            CompletionHandler<AsynchronousSocketChannel, ? super A> handler)
    {
        return key.execute(OP_ACCEPT, attachment, handler,
            new Callable<AsynchronousSocketChannel>() {
                public AsynchronousSocketChannel call() throws IOException {
                    try {
                        SocketChannel newChannel = channel.accept();
                        if (newChannel == null) {
                            throw new IOException("accept failed");
                        }
                        return new AsyncSocketChannelImpl(group, newChannel);
                    } catch (ClosedChannelException e) {
                        throw Util.initCause(
                            new AsynchronousCloseException(), e);
                    }
                }});
    }
}
