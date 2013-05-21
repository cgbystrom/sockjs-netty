package com.cgbystrom.sockjs.transports;

import com.cgbystrom.sockjs.Frame;
import com.cgbystrom.sockjs.ServiceMetadata;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;

import java.util.List;

public class JsonpPollingTransport extends BaseTransport {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JsonpPollingTransport.class);
    
    private String jsonpCallback;

    public JsonpPollingTransport(ServiceMetadata.Metrics metrics) {
        super(metrics.getJsonp());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();

        QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        final List<String> c = qsd.getParameters().get("c");
        if (c == null) {
            respond(e.getChannel(), HttpResponseStatus.INTERNAL_SERVER_ERROR, "\"callback\" parameter required.");
            return;
        }
        jsonpCallback = c.get(0);

        super.messageReceived(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof Frame) {
            final Frame frame = (Frame) e.getMessage();
            HttpResponse response = createResponse(CONTENT_TYPE_JAVASCRIPT);
            response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
            response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");

            ChannelBuffer escapedContent = ChannelBuffers.dynamicBuffer();
            Frame.escapeJson(Frame.encode(frame, false), escapedContent);
            String m = jsonpCallback + "(\"" + escapedContent.toString(CharsetUtil.UTF_8) + "\");\r\n";

            e.getFuture().addListener(ChannelFutureListener.CLOSE);
            
            final ChannelBuffer content = ChannelBuffers.copiedBuffer(m, CharsetUtil.UTF_8);
            response.setContent(content);
            response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, content.readableBytes());
            ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), response, e.getRemoteAddress()));
            transportMetrics.messagesSent.mark();
            transportMetrics.messagesSentSize.update(content.readableBytes());
        } else {
            super.writeRequested(ctx, e);
        }
    }
}
