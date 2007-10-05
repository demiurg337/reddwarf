/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/**
 * A select-based AsynchronousChannelGroup.
 */
class ReactiveChannelGroup
    extends AsyncGroupImpl
{
    /** The logger for this class. */
    static final Logger log =
        Logger.getLogger(ReactiveChannelGroup.class.getName());

    /**
     * The lifecycle state of this group.
     * Increases monotonically.
     */
    protected volatile int lifecycleState;
    /** State: open and running */
    protected static final int RUNNING      = 0;
    /** State: graceful shutdown in progress */
    protected static final int SHUTDOWN     = 1;
    /** State: forced shutdown in progress */
    protected static final int SHUTDOWN_NOW = 2;
    /** State: terminated */
    protected static final int DONE         = 3;

    /**
     * Lock held on updates to lifecycleState, and condition variable
     * for awaiting group termination.
     */
    final Object stateLock = new Object();

    /**
     * The property to specify the number of reactors to be used by
     * channel groups: {@value}
     */
    public static final String REACTORS_PROPERTY =
        "com.sun.sgs.nio.async.reactive.reactors";

    /**
     * The default number of reactors to be used by channel groups:
     * {@code Runtime.getRuntime().availableProcessors()}
     */
    public static final int DEFAULT_REACTORS = 
        Runtime.getRuntime().availableProcessors();

    /** The active {@linkplain Reactor reactors} in this group. */
    final List<Reactor> reactors;

    /**
     * TODO doc
     * @param provider
     * @param executor
     * @throws IOException
     */
    ReactiveChannelGroup(ReactiveAsyncChannelProvider provider,
                         ExecutorService executor)
        throws IOException
    {
        this(provider, executor, 0);
    }

    /**
     * TODO doc
     * If requestedReactors == 0, choose from a property.
     * TODO determine how security model interacts with properties needed
     * for group creation
     * 
     * @param provider
     * @param executor
     * @param requestedReactors
     * @throws IOException
     */
    ReactiveChannelGroup(ReactiveAsyncChannelProvider provider,
                         ExecutorService executor,
                         int requestedReactors)
        throws IOException
    {
        super(provider, executor);

        int n = requestedReactors;

        if (n == 0) {
            try {
                n = Integer.valueOf(System.getProperty(REACTORS_PROPERTY));
            } catch (NumberFormatException e) {
                n = DEFAULT_REACTORS;
            }
        }

        if (n <= 0) {
            throw new IllegalArgumentException("non-positive reactor count");
        }

        reactors = new ArrayList<Reactor>(n);

        for (int i = 0; i < n; ++i) {
            reactors.add(new Reactor(this, executor()));
        }

        for (Reactor reactor : reactors) {
            executor.execute(new WorkerStrategy(reactor));
        }
    }

    /**
     * {@inheritDoc}
     */
    AsyncKey register(SelectableChannel ch) throws IOException {
        ch.configureBlocking(false);
        AsyncKey asyncKey = null;
        Reactor reactor = null;
        synchronized (stateLock) {
            if (lifecycleState != RUNNING)
                throw new ShutdownChannelGroupException();

            int k = reactorBucketStrategy(ch);
            reactor = reactors.get(Math.abs(k % reactors.size()));
        }
    
        try {
            asyncKey = reactor.register(ch);
            return asyncKey;
        } finally {
            if (asyncKey == null) {
                try {
                    ch.close();
                } catch (Throwable ignore) {}
            }
        }
    }

    /**
     * Returns a reactor bucket for the given channel.
     *
     * @param ch a channel
     * @return the reactor bucket for the channel
     */
    protected int reactorBucketStrategy(SelectableChannel ch) {
        return ch.hashCode();
    }
    
    /**
     * TODO doc
     */
    class WorkerStrategy implements Runnable {

        /** This worker's reactor. */
        private final Reactor reactor;

        /**
         * TODO doc
         * @param reactor
         */
        WorkerStrategy(Reactor reactor) {
            this.reactor = reactor;
        }

        /**
         * {@inheritDoc}
         */
        public void run() {
            Throwable exception = null;
            try {
                while (reactor.run()) { /* empty */ }
            } catch (Throwable t) {
                exception = t;
            }

            synchronized (stateLock) {
                reactors.remove(reactor);
                tryTerminate();
            }

            if (exception != null)
                log.log(Level.WARNING, "reactor exception", exception);
        }
    }

    /* Termination support. */

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        long nanos = unit.toNanos(timeout);
        final long deadline =
            TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) + nanos;

        synchronized (stateLock) {
            for (;;) {
                if (lifecycleState == DONE)
                    return true;
                if (nanos <= 0)
                    return false;
                long millis = TimeUnit.NANOSECONDS.toMillis(nanos);
                stateLock.wait(millis, (int) (nanos % 1000000));
                nanos = deadline -
                    TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return lifecycleState != RUNNING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return lifecycleState == DONE   ;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdown() {
        synchronized (stateLock) {
            if (lifecycleState < SHUTDOWN)
                lifecycleState = SHUTDOWN;

            for (Reactor reactor : reactors)
                reactor.shutdown();

            tryTerminate();

            return this;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdownNow() throws IOException {
        synchronized (stateLock) {
            if (lifecycleState < SHUTDOWN_NOW)
                lifecycleState = SHUTDOWN_NOW;

            for (Reactor reactor : reactors) {
                try {
                    reactor.shutdownNow();
                } catch (Throwable ignore) { }
            }

            tryTerminate();

            return this;
        }
    }

    /**
     * If the group is trying to shutdown, check that all the reactors
     * have shutdown.  If they have, mark this group as done and wake
     * anyone blocked on awaitTermination.
     * 
     * NOTE: Must be called with stateLock held!
     */
    private void tryTerminate() {
        if (lifecycleState == RUNNING || lifecycleState == DONE)
            return;

        if (log.isLoggable(Level.FINER)) {
            log.log(Level.FINER, " {0} tryTerminate: {1} reactors",
                new Object[] { this, reactors.size() });
        }

        if (reactors.isEmpty()) {
            lifecycleState = DONE;
            stateLock.notifyAll();
            log.log(Level.FINE, "{0} terminated", this);
        }
    }

}
