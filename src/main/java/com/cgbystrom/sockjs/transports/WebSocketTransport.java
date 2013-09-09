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
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.websocketx.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

// FIMXE: Mark as sharable?
public class WebSocketTransport extends SimpleChannelHandler {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(WebSocketTransport.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private WebSocketServerHandshaker handshaker;
    private final String path;
    private TransportMetrics transportMetrics;
    private Service service;
    private Timeout pingPongFrameTimeout;
    private Channel channel;

    public WebSocketTransport(String path, Service metadata) {
        this.path = path;
        this.service = metadata;
        transportMetrics = metadata.getMetrics().getWebSocket();
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Overridden method to prevent propagation of channel state event upstream.
        // Depending on pipeline this may or may not be called.
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Overridden method to prevent propagation of channel state event upstream.
        // Depending on pipeline this may or may not be called.
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Metrics for connect is handled by ServiceRouter since we are not attached
        // to pipeline when channelConnected fires.
        cancelHeartbeatTask();
        channel = null;
        transportMetrics.connectionsOpen.dec();
        super.channelDisconnected(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        Object msg = e.getMessage();
        if (msg instanceof HttpRequest) {
            handleHttpRequest(ctx, e.getChannel(), (HttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            WebSocketFrame wsf = (WebSocketFrame) msg;
            transportMetrics.messagesReceived.mark();
            if (wsf.getBinaryData() != null) {
                transportMetrics.messagesReceivedSize.update(wsf.getBinaryData().readableBytes());
            }
            handleWebSocketFrame(ctx, e.getChannel(), wsf);
        } else {
            throw new IOException("Unknown frame type: " + msg.getClass().getSimpleName());
        }
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

            ChannelBuffer frame = Frame.encode((Frame) e.getMessage(), false);
            transportMetrics.messagesSent.mark();
            transportMetrics.messagesSentSize.update(frame.readableBytes());
            TextWebSocketFrame message = new TextWebSocketFrame(frame);
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
            req.setHeader(CONNECTION, UPGRADE);
        }

        // If we get WS version 7, treat it as 8 as they are almost identical. (Really true?)
        String wsVersionHeader = req.getHeader(SEC_WEBSOCKET_VERSION);
        if (wsVersionHeader != null && wsVersionHeader.equals("7")) {
            req.setHeader(SEC_WEBSOCKET_VERSION, "8");
        }

        // Handshake
        String wsLocation = getWebSocketLocation(channel.getPipeline(), req);
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(wsLocation, null, false);

        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            wsFactory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel());
        } else {
            handshaker.handshake(ctx.getChannel(), req).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        WebSocketTransport.this.channel = ctx.getChannel();
                        ctx.getPipeline().remove(ServiceRouter.class);
                        ctx.getPipeline().remove(PreflightHandler.class);
                        ctx.sendUpstream(new UpstreamChannelStateEvent(channel, ChannelState.CONNECTED, Boolean.TRUE));
                        scheduleHeartbeatTask();
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
        } else if (frame instanceof TextWebSocketFrame) {
            // Send the uppercase string back.
            String request = ((TextWebSocketFrame) frame).getText();
            ChannelBuffer payload = frame.getBinaryData();

            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Channel %s received '%s'", ctx.getChannel().getId(), request));
            }

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
        } else if (frame instanceof PongWebSocketFrame) {
            // Ignore
        } else {
            logger.error("Unhandled frame type: " + frame.getClass().getSimpleName());
        }
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, HttpRequest req, HttpResponse res) {
        // Send the response and close the connection if necessary.
        if (!isKeepAlive(req) || res.getStatus().getCode() != 200) {
            res.setHeader(CONNECTION, Values.CLOSE);
            ctx.getChannel().write(res).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.getChannel().write(res);
        }
    }

    private String getWebSocketLocation(ChannelPipeline pipeline, HttpRequest req) {
        boolean isSsl = pipeline.get(SslHandler.class) != null;
        if (isSsl) {
            return "wss://" + req.getHeader(HttpHeaders.Names.HOST) + path;
        } else {
            return "ws://" + req.getHeader(HttpHeaders.Names.HOST) + path;
        }
    }

    private void scheduleHeartbeatTask() {
        int interval = service.getHeartbeatInterval();
        pingPongFrameTimeout = service.getTimer().newTimeout(new HeartbeatTimerTask(), interval, TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeatTask() {
        if (pingPongFrameTimeout != null) {
            pingPongFrameTimeout.cancel();
        }
    }

    /**
     * Sends a Web Socket ping frame to the client to ensure TCP connection stays alive.
     * Especially important on the public internet where many can/will interfere, such as proxies/load balancers.
     * */
    private class HeartbeatTimerTask implements TimerTask {
        @Override
        public void run(Timeout timeout) throws Exception {
            if (!timeout.isCancelled() && channel != null && channel.isWritable()) {
                logger.debug("Sending heartbeat/ping frame");
                channel.write(new PingWebSocketFrame());
                scheduleHeartbeatTask();
            }
        }
    }
}
