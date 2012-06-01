package com.cgbystrom.sockjs.transports;


import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpMethod.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import com.cgbystrom.sockjs.*;
import com.cgbystrom.sockjs.PreflightHandler;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// FIMXE: Mark as sharable?
public class WebSocketTransport extends SimpleChannelHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketTransport.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final CookieDecoder COOKIE_DECODER = new CookieDecoder();
    private static final CookieEncoder COOKIE_ENCODER = new CookieEncoder(true);
    private static final String JSESSIONID = "JSESSIONID";
    private static final String DEFAULT_COOKIE = "JSESSIONID=dummy; path=/";
    private static final ChannelBuffer NEW_LINE = ChannelBuffers.copiedBuffer("\r\n", CharsetUtil.UTF_8);
    private static final ChannelBuffer FRAME_BEGIN = ChannelBuffers.copiedBuffer("data: ", CharsetUtil.UTF_8);
    private static final ChannelBuffer FRAME_DELIMITER = ChannelBuffers.copiedBuffer("\r\n\0", CharsetUtil.UTF_8);
    private static final ChannelBuffer FRAME_END = ChannelBuffers.copiedBuffer("\r\n\r\n", CharsetUtil.UTF_8);

    private String cookie = DEFAULT_COOKIE;
    //private String jsonpCallback;
    private ChannelBuffer header;
    private AtomicBoolean headerSent = new AtomicBoolean(false);

    /**
     *  Max size of response content sent before closing the connection.
     *  Since browsers buffer chunked/streamed content in-memory the connection must be closed
     *  at regular intervals. Call it "garbage collection" if you will.
     */
    private final int maxResponseSize;

    /** Track size of content chunks sent to the browser. */
    private AtomicInteger numBytesSent = new AtomicInteger(0);
    // FIXME: Do we really need to be atomic? Are not each pipeline handler assigned to an I/O thread, such as this class?

    private WebSocketServerHandshaker handshaker;
    private final String path;

    public WebSocketTransport(String path) {
        this.path = path;
        this.maxResponseSize = 128 * 1024; // 128 KiB
    }

    public WebSocketTransport(String path, int maxResponseSize) {
        this.path = path;
        this.maxResponseSize = maxResponseSize;
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
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {

        super.channelDisconnected(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        //self.preflight()
        //self.handle_session_cookie()
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, e.getChannel(), (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, e.getChannel(), (WebSocketFrame) msg);
        } else {
            System.out.println("UNKNOWN FRAME TYPE");
        }


        /*HttpRequest request = (HttpRequest) e.getMessage();

        // FIXME: Check if cookies are enabled in the server
        cookie = DEFAULT_COOKIE;
        String cookieHeader = request.getHeader(HttpHeaders.Names.COOKIE);
        if (cookieHeader != null) {
            Set<Cookie> cookies = COOKIE_DECODER.decode(cookieHeader);
            for (Cookie c : cookies) {
                if (c.getName().equals(JSESSIONID)) {
                    c.setPath("/");
                    CookieEncoder cookieEncoder = new CookieEncoder(true);
                    cookieEncoder.addCookie(c);
                    cookie = cookieEncoder.encode();
                    System.out.println("COOKIE SET TO " + cookie);
                }
            }
        }

        /*QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());

        final List<String> c = qsd.getParameters().get("c");
        if (c == null) {
            NotFoundHandler.respond(e.getChannel(), HttpResponseStatus.INTERNAL_SERVER_ERROR, "\"callback\" parameter required.");
            return;
        }
        final String callback = c.get(0);
        header = ChannelBuffers.wrappedBuffer(HEADER_PART1, ChannelBuffers.copiedBuffer(callback, CharsetUtil.UTF_8), HEADER_PART2);
        e.getChannel().write(header);*/
        
        


        // Only when we have received the HTTP request is the sockjs connection open
        // if it's a returning request for an existing session do nothing in terms of connection opening
        // deliver any outstanding messages
        // create a session if new

        // We received a polling XHR request, keep it alive and notify upstream about session, connected/disconnected

        //String sessionId = "BOGUS_FIX_123";
        
        // Since we have silenced the usual channel state events for open and connected for the socket,
        // we must notify handlers downstream to now consider this connection connected.
        // We are responsible for manually dispatching this event upstream

        // ctx.sendUpstream(new UpstreamChannelStateEvent(e.getChannel(), ChannelState.CONNECTED, Boolean.TRUE));
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
        if (e.getMessage() instanceof Frame) {
            Frame f = (Frame) e.getMessage();
            logger.debug("Write requested for " + f.getClass().getSimpleName());
            if (f instanceof Frame.CloseFrame) {
                e.getFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        // FIXME: Should really send close frame here?
                        // handshaker.close(e.getChannel(), new CloseWebSocketFrame()); ?
                        e.getChannel().close();
                    }
                });
            }
            TextWebSocketFrame message = new TextWebSocketFrame(Frame.encode((Frame) e.getMessage(), false));
            super.writeRequested(ctx, new DownstreamMessageEvent(e.getChannel(), e.getFuture(), message, e.getRemoteAddress()));
        } else {
            super.writeRequested(ctx, e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        // FIXME: Move to BaseTransport
        if (e.getCause() instanceof SessionHandler.NotFoundException) {
            BaseTransport.respond(e.getChannel(), HttpResponseStatus.NOT_FOUND, "Session not found.");
        } else if (e.getCause() instanceof SessionHandler.LockException) {
            if (e.getChannel().isWritable()) {
                e.getChannel().write(Frame.closeFrame(2010, "Another connection still open"));
            }
        } else if (e.getCause() instanceof JsonParseException || e.getCause() instanceof JsonMappingException) {
            //NotFoundHandler.respond(e.getChannel(), HttpResponseStatus.INTERNAL_SERVER_ERROR, "Broken JSON encoding.");
            e.getChannel().close();
        } else if (e.getCause() instanceof WebSocketHandshakeException) {
            if (e.getCause().getMessage().contains("missing upgrade")) {
                BaseTransport.respond(e.getChannel(), HttpResponseStatus.BAD_REQUEST, "Can \"Upgrade\" only to \"WebSocket\".");
            }
            //NotFoundHandler.respond(e.getChannel(), HttpResponseStatus.INTERNAL_SERVER_ERROR, "Broken JSON encoding.");
            //e.getChannel().close();

        } else {
            super.exceptionCaught(ctx, e);
        }

        
    }

    private void handleHttpRequest(final ChannelHandlerContext ctx, final Channel channel, HttpRequest req) throws Exception {
        // Allow only GET methods.
        if (req.getMethod() != GET) {
            DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED);
            response.addHeader(ALLOW, GET.toString());
            sendHttpResponse(ctx, req, response);
            return;
        }


        // Compatibility hack for Firefox 6.x
        String connectionHeader = req.getHeader(CONNECTION);
        if (connectionHeader != null && connectionHeader.equals("keep-alive, Upgrade")) {
            System.out.println("FIXING IT FOR FIREFOX");
            req.setHeader(CONNECTION, UPGRADE);
        }

        // If we get WS version 7, treat it as 8 as they are almost identical. (Really true?)
        String wsVersionHeader = req.getHeader(SEC_WEBSOCKET_VERSION);
        if (wsVersionHeader != null && wsVersionHeader.equals("7")) {
            req.setHeader(SEC_WEBSOCKET_VERSION, "8");
        }

        // Handshake
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), "chat, superchat", false);

        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        } else {
            handshaker.handshake(ctx.getChannel(), req).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        ctx.getPipeline().remove(ServiceRouter.class);
                        ctx.getPipeline().remove(PreflightHandler.class);
                        ctx.sendUpstream(new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, Boolean.TRUE));
                    }
                }
            });
        }
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, Channel channel, WebSocketFrame frame) throws IOException {
        // Check for closing frame
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.getChannel(), (CloseWebSocketFrame) frame);
            return;
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.getChannel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        } else if (!(frame instanceof TextWebSocketFrame)) {
            throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass().getName()));
        }

        // Send the uppercase string back.
        String request = ((TextWebSocketFrame) frame).getText();
        logger.debug(String.format("Channel %s received '%s'", ctx.getChannel().getId(), request));
        ChannelBuffer payload = frame.getBinaryData();

        if (frame.getBinaryData().readableBytes() == 0) {
            return;
        }

        ChannelBufferInputStream cbis = new ChannelBufferInputStream(payload);
        String[] messages;
        if (payload.getByte(0) == '[') {
            // decode array
            messages = mapper.readValue(cbis, String[].class);
        } else if (payload.getByte(0) == '"') {
            // decode string
            messages = new String[1];
            messages[0] = mapper.readValue(cbis, String.class);
        } else {
            throw new IOException("Expected message as string or string[]");
        }

        for (String message : messages) {
            SockJsMessage jsMessage = new SockJsMessage(message);
            ctx.sendUpstream(new UpstreamMessageEvent(channel, jsMessage, channel.getRemoteAddress()));
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Generate an error page if response status code is not OK (200).
        if (res.getStatus().getCode() != 200) {
            //res.setContent(ChannelBuffers.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8));
            //setContentLength(res, res.getContent().readableBytes());
        }

        // Send the response and close the connection if necessary.
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            res.setHeader(CONNECTION, Values.CLOSE);
            ctx.getChannel().write(res).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.getChannel().write(res);
        }
    }

    private String getWebSocketLocation(HttpRequest req) {
        // FIXME: Handle SSL and non-standard HTTP port?
        return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + path;
    }
}
