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
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;

public class HtmlFileTransport extends StreamingTransport {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(HtmlFileTransport.class);
    private static final ChannelBuffer HEADER_PART1 = ChannelBuffers.copiedBuffer("<!doctype html>\n" +
            "<html><head>\n" +
            "  <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\" />\n" +
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
            "</head><body><h2>Don't panic!</h2>\n" +
            "  <script>\n" +
            "    document.domain = document.domain;\n" +
            "    var c = parent.", CharsetUtil.UTF_8);
    private static final ChannelBuffer HEADER_PART2 = ChannelBuffers.copiedBuffer(";\n" +
            "    c.start();\n" +
            "    function p(d) {c.message(d);};\n" +
            "    window.onload = function() {c.stop();};\n" +
            "  </script>", CharsetUtil.UTF_8);
    private static final ChannelBuffer PREFIX = ChannelBuffers.copiedBuffer("<script>\np(\"", CharsetUtil.UTF_8);
    private static final ChannelBuffer POSTFIX = ChannelBuffers.copiedBuffer("\");\n</script>\r\n", CharsetUtil.UTF_8);


    private ChannelBuffer header;

    public HtmlFileTransport(ServiceMetadata.Metrics metrics, int maxResponseSize) {
        super(metrics.getHtmlFile(), maxResponseSize);
    }

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest) e.getMessage();
        QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());

        final List<String> c = qsd.getParameters().get("c");
        if (c == null) {
            respond(e.getChannel(), HttpResponseStatus.INTERNAL_SERVER_ERROR, "\"callback\" parameter required.");
            return;
        }
        final String callback = c.get(0);
        header = ChannelBuffers.wrappedBuffer(HEADER_PART1, ChannelBuffers.copiedBuffer(callback, CharsetUtil.UTF_8), HEADER_PART2);

        super.messageReceived(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof Frame) {
            final Frame frame = (Frame) e.getMessage();
            if (headerSent.compareAndSet(false, true)) {
                HttpResponse response = createResponse(CONTENT_TYPE_HTML);
                response.setHeader(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");

                // Safari needs at least 1024 bytes to parse the website. Relevant:
                //   http://code.google.com/p/browsersec/wiki/Part2#Survey_of_content_sniffing_behaviors
                int spaces = 1024 - header.readableBytes();
                ChannelBuffer paddedHeader = ChannelBuffers.buffer(1024 + 50);

                paddedHeader.writeBytes(header);
                for (int i = 0; i < spaces + 20; i++) {
                    paddedHeader.writeByte(' ');
                }
                paddedHeader.writeByte('\r');
                paddedHeader.writeByte('\n');
                // Opera needs one more new line at the start.
                paddedHeader.writeByte('\r');
                paddedHeader.writeByte('\n');

                ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), response, e.getRemoteAddress()));
                ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), new DefaultHttpChunk(paddedHeader), e.getRemoteAddress()));
            }

            final ChannelBuffer frameContent = Frame.encode(frame, false);
            final ChannelBuffer content = ChannelBuffers.dynamicBuffer(frameContent.readableBytes() + 10);

            Frame.escapeJson(frameContent, content);
            ChannelBuffer wrappedContent = ChannelBuffers.wrappedBuffer(PREFIX, content, POSTFIX);
            ctx.sendDownstream(new DownstreamMessageEvent(e.getChannel(), e.getFuture(), new DefaultHttpChunk(wrappedContent), e.getRemoteAddress()));

            logResponseSize(e.getChannel(), content);
        } else {
            super.writeRequested(ctx, e);
        }
    }
}
