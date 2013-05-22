package com.cgbystrom.sockjs.client;

import com.cgbystrom.sockjs.Frame;
import com.cgbystrom.sockjs.Session;
import com.cgbystrom.sockjs.SessionCallback;
import com.cgbystrom.sockjs.SockJsMessage;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import org.jboss.netty.handler.codec.http.websocketx.WebSocketVersion;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

public class WebSocketClient extends SockJsClient implements Session {
    private ClientBootstrap bootstrap;
    private Channel channel;
    private String sessionId;
    private URI uri;
    private SessionCallback callback;
    private final WebSocketClientHandshaker wsHandshaker;
    private boolean sockJsHandshakeDone = false;

    public WebSocketClient(ClientBootstrap bootstrap, URI uri, SessionCallback callback) throws URISyntaxException {
        this.bootstrap = bootstrap;
        this.sessionId = UUID.randomUUID().toString();
        this.uri = uri;
        this.callback = callback;

        URI sockJsUri = new URI("http", uri.getUserInfo(), uri.getHost(), uri.getPort(),
                uri.getPath() + "/999/" + sessionId + "/websocket", uri.getQuery(), uri.getFragment());

        this.wsHandshaker = new WebSocketClientHandshakerFactory().newHandshaker(
                sockJsUri, WebSocketVersion.V13, null, false, null);
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channel = e.getChannel();
        sendWebSocketHandshake();
        super.channelConnected(ctx, e);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channel = null;
        callback.onClose();
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (!wsHandshaker.isHandshakeComplete()) {
            wsHandshaker.finishHandshake(e.getChannel(), (HttpResponse) e.getMessage());
            callback.onOpen(WebSocketClient.this);
            return;
        }

        if (e.getMessage() instanceof TextWebSocketFrame) {
            TextWebSocketFrame wf = (TextWebSocketFrame) e.getMessage();
            String frame = wf.getText();
            char frameType = frame.charAt(0);

            if (!sockJsHandshakeDone) {
                if (frameType == 'o') {
                    sockJsHandshakeDone = true;
                } else {
                    throw new IllegalStateException("Expected open frame 'o' as first frame. Got " + frame);
                }
                return;
            }

            switch (frameType) {
                case 'h':
                    break;

                case 'a':
                    try {
                        List<String> messages = objectMapper.readValue(frame.substring(1), List.class);
                        for (String msg : messages) {
                            try {
                                callback.onMessage(msg);
                            } catch (Exception ex) {
                                callback.onError(ex);
                            }
                        }
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Unable to decode frame: " + frame);
                    }
                    break;

                case 'c':
                    disconnect();
                    break;

                default:
                    throw new IllegalArgumentException("Received unknown frame type '" + frameType + "'");
            }
        }
    }

    @Override
    public ChannelFuture connect() {
        return bootstrap.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
    }

    @Override
    public ChannelFuture disconnect() {
        return channel.close();
    }

    @Override
    public void send(String message) {
        ChannelBuffer cb = Frame.messageFrame(new SockJsMessage(message)).getData();
        cb.readerIndex(1); // Skip the framing char
        channel.write(new TextWebSocketFrame(cb));
    }

    @Override
    public void close() {
        disconnect();
    }

    private void sendWebSocketHandshake() throws Exception {
        wsHandshaker.handshake(channel);
    }
}
