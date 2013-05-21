package com.cgbystrom.sockjs.transports;

import com.cgbystrom.sockjs.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.*;

public class XhrPollingTransport extends BaseTransport {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(XhrPollingTransport.class);

    public XhrPollingTransport(ServiceMetadata.Metrics metrics) {
        super(metrics.getXhrPolling());
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof Frame) {
            Frame frame = (Frame) e.getMessage();
            ChannelBuffer content = Frame.encode(frame, true);
            HttpResponse response = createResponse(CONTENT_TYPE_JAVASCRIPT);
            response.setHeader(CONTENT_LENGTH, content.readableBytes());
            response.setHeader(CONNECTION, CLOSE);
            response.setContent(content);
            e.getFuture().addListener(ChannelFutureListener.CLOSE);
            ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), response, e.getRemoteAddress()));
        } else {
            super.writeRequested(ctx, e);
        }
    }
}