package com.cgbystrom.sockjs.transports;

import com.cgbystrom.sockjs.Frame;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XhrStreamingTransport extends StreamingTransport {
    private static final Logger logger = LoggerFactory.getLogger(XhrStreamingTransport.class);

    public XhrStreamingTransport(int maxResponseSize) {
        super(maxResponseSize);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof Frame) {
            if (headerSent.compareAndSet(false, true)) {
                HttpResponse response = createResponse(CONTENT_TYPE_JAVASCRIPT);
                ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), response, e.getRemoteAddress()));

                // IE requires 2KB prefix:
                // http://blogs.msdn.com/b/ieinternals/archive/2010/04/06/comet-streaming-in-internet-explorer-with-xmlhttprequest-and-xdomainrequest.aspx
                DefaultHttpChunk message = new DefaultHttpChunk(Frame.encode(Frame.preludeFrame(), true));
                ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), message, e.getRemoteAddress()));
            }
            final Frame frame = (Frame) e.getMessage();
            ChannelBuffer content = Frame.encode(frame, true);

            ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), new DefaultHttpChunk(content), e.getRemoteAddress()));
            logResponseSize(e.getChannel(), content);
        } else {
            super.writeRequested(ctx, e);
        }
    }
}