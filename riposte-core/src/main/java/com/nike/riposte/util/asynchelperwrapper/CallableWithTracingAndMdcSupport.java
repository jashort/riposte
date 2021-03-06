package com.nike.riposte.util.asynchelperwrapper;

import com.nike.internal.util.Pair;
import com.nike.riposte.util.AsyncNettyHelper;
import com.nike.wingtips.Span;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;

import io.netty.channel.ChannelHandlerContext;

import static com.nike.riposte.util.AsyncNettyHelper.linkTracingAndMdcToCurrentThread;
import static com.nike.riposte.util.AsyncNettyHelper.unlinkTracingAndMdcFromCurrentThread;

/**
 * A {@link Callable} that wraps the given original so that the given {@link ChannelHandlerContext}'s distributed
 * tracing and MDC information is registered with the thread and therefore available during execution and unregistered
 * after execution. There are also other constructors for building an instance of this class if you have the raw trace
 * and MDC info but no {@link ChannelHandlerContext}.
 */
@SuppressWarnings("WeakerAccess")
public class CallableWithTracingAndMdcSupport<U> implements Callable<U> {

    protected final Callable<U> origCallable;
    protected final Deque<Span> distributedTraceStackForExecution;
    protected final Map<String, String> mdcContextMapForExecution;

    /**
     * Contructor that extracts the trace and MDC info from the given {@link ChannelHandlerContext}'s {@link
     * com.nike.riposte.server.http.HttpProcessingState} so that it can be associated with the thread when the given
     * operation is executed.
     * <p/>
     * The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in null
     * for the operation).
     * <p/>
     * The {@link ChannelHandlerContext} can be null (or the state inside can be null) but if you pass null it means
     * there won't be any trace or MDC info associated with the thread when the given operation is executed.
     */
    public CallableWithTracingAndMdcSupport(Callable<U> origCallable, ChannelHandlerContext ctx) {
        this(origCallable, AsyncNettyHelper.extractTracingAndMdcInfoFromChannelHandlerContext(ctx));
    }

    /**
     * Constructor that uses the given trace and MDC information, which will be associated with the thread when the
     * given operation is executed.
     * <p/>
     * The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in null
     * for the operation).
     * <p/>
     * The {@link Pair} can be null, or you can pass null for the left and/or right side of the pair, and no error will
     * be thrown. Any trace or MDC info that is null means the corresponding info will not be available to the thread
     * when the operation is executed however.
     */
    public CallableWithTracingAndMdcSupport(Callable<U> origCallable,
                                            Pair<Deque<Span>, Map<String, String>> originalThreadInfo) {
        this(
            origCallable,
            (originalThreadInfo == null) ? null : originalThreadInfo.getLeft(),
            (originalThreadInfo == null) ? null : originalThreadInfo.getRight()
        );
    }

    /**
     * Constructor that uses the given trace and MDC information, which will be associated with the thread when the
     * given operation is executed.
     * <p/>
     * The operation you pass in cannot be null (an {@link IllegalArgumentException} will be thrown if you pass in null
     * for the operation).
     * <p/>
     * The trace and/or MDC info can be null and no error will be thrown, however any trace or MDC info that is null
     * means the corresponding info will not be available to the thread when the operation is executed.
     */
    public CallableWithTracingAndMdcSupport(Callable<U> origCallable, Deque<Span> distributedTraceStackForExecution,
                                            Map<String, String> mdcContextMapForExecution) {
        if (origCallable == null)
            throw new IllegalArgumentException("origCallable cannot be null");

        this.origCallable = origCallable;
        this.distributedTraceStackForExecution = distributedTraceStackForExecution;
        this.mdcContextMapForExecution = mdcContextMapForExecution;
    }

    @Override
    public U call() throws Exception {
        Pair<Deque<Span>, Map<String, String>> originalThreadInfo = null;
        try {
            originalThreadInfo =
                linkTracingAndMdcToCurrentThread(distributedTraceStackForExecution, mdcContextMapForExecution);

            return origCallable.call();
        }
        finally {
            unlinkTracingAndMdcFromCurrentThread(originalThreadInfo);
        }
    }
}
