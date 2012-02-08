package com.cgbystrom.sockjs.transports;

import com.cgbystrom.sockjs.Frame;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;

public class EventSourceTransport extends StreamingTransport {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(EventSourceTransport.class);
    private static final ChannelBuffer NEW_LINE = ChannelBuffers.copiedBuffer("\r\n", CharsetUtil.UTF_8);
    private static final ChannelBuffer FRAME_BEGIN = ChannelBuffers.copiedBuffer("data: ", CharsetUtil.UTF_8);
    private static final ChannelBuffer FRAME_END = ChannelBuffers.copiedBuffer("\r\n\r\n", CharsetUtil.UTF_8);
    private static final String CONTENT_TYPE_EVENT_STREAM = "text/event-stream; charset=UTF-8";

    public EventSourceTransport(int maxResponseSize) {
        super(maxResponseSize);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof Frame) {
            Frame frame = (Frame) e.getMessage();
            if (headerSent.compareAndSet(false, true)) {
                HttpResponse response = createResponse(CONTENT_TYPE_EVENT_STREAM);
                ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), response, e.getRemoteAddress()));
                ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), new DefaultHttpChunk(NEW_LINE), e.getRemoteAddress()));
            }

            ChannelBuffer wrappedContent = ChannelBuffers.wrappedBuffer(FRAME_BEGIN, Frame.encode(frame, false), FRAME_END);
            ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), new DefaultHttpChunk(wrappedContent), e.getRemoteAddress()));
            logResponseSize(e.getChannel(), wrappedContent);
        } else {
            super.writeRequested(ctx, e);
        }
    }
}
