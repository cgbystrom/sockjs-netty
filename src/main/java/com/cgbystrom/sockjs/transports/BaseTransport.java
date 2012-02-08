package com.cgbystrom.sockjs.transports;

import com.cgbystrom.sockjs.SessionHandler;
import com.cgbystrom.sockjs.Frame;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;

import java.util.Set;

public class BaseTransport extends SimpleChannelHandler {
    public static final String CONTENT_TYPE_JAVASCRIPT = "application/javascript; charset=UTF-8";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_TYPE_PLAIN = "text/plain; charset=UTF-8";
    public static final String CONTENT_TYPE_HTML = "text/html; charset=UTF-8";

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(BaseTransport.class);
    private static final CookieDecoder COOKIE_DECODER = new CookieDecoder();
    private static final String JSESSIONID = "JSESSIONID";
    private static final String DEFAULT_COOKIE = "JSESSIONID=dummy; path=/";

    protected String cookie = DEFAULT_COOKIE;

    public static void respond(Channel channel, HttpResponseStatus status, String message) throws Exception {
        // TODO: Why aren't response data defined in SockJS for error messages?
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, status);
        response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");

        final ChannelBuffer buffer = ChannelBuffers.copiedBuffer(message, CharsetUtil.UTF_8);
        response.setContent(buffer);
        response.setHeader(CONTENT_LENGTH, buffer.readableBytes());
        response.setHeader(SET_COOKIE, "JSESSIONID=dummy; path=/"); // FIXME: Don't sprinkle cookies in every request
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        if (channel.isWritable())
            channel.write(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Overridden method to prevent propagation of channel state event upstream.
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Overridden method to prevent propagation of channel state event upstream.
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        handleCookie((HttpRequest) e.getMessage());

        // Since we have silenced the usual channel state events for open and connected for the socket,
        // we must notify handlers downstream to now consider this connection connected.
        // We are responsible for manually dispatching this event upstream
        ctx.sendUpstream(new UpstreamChannelStateEvent(e.getChannel(), ChannelState.CONNECTED, Boolean.TRUE));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof SessionHandler.NotFoundException) {
            respond(e.getChannel(), HttpResponseStatus.NOT_FOUND, "Session not found.");
        } else if (e.getCause() instanceof SessionHandler.LockException) {
            if (e.getChannel().isWritable()) {
                e.getChannel().write(Frame.closeFrame(2010, "Another connection still open"));
            }
        } else {
            super.exceptionCaught(ctx, e);
        }
    }

    protected HttpResponse createResponse(String contentType) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK);
        response.setHeader(CONTENT_TYPE, contentType);
        response.setHeader(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader(SET_COOKIE, cookie); // FIXME: Check if cookies are enabled
        return response;
    }

    protected void handleCookie(HttpRequest request) {
        // FIXME: Check if cookies are enabled in the server
        cookie = DEFAULT_COOKIE;
        String cookieHeader = request.getHeader(COOKIE);
        if (cookieHeader != null) {
            Set<Cookie> cookies = COOKIE_DECODER.decode(cookieHeader);
            for (Cookie c : cookies) {
                if (c.getName().equals(JSESSIONID)) {
                    c.setPath("/");
                    CookieEncoder cookieEncoder = new CookieEncoder(true);
                    cookieEncoder.addCookie(c);
                    cookie = cookieEncoder.encode();
                }
            }
        }
    }
}
