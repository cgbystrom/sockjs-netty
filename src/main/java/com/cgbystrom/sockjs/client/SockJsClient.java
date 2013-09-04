package com.cgbystrom.sockjs.client;

import com.cgbystrom.sockjs.SessionCallback;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseDecoder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;

public abstract class SockJsClient extends SimpleChannelUpstreamHandler {
    private static final NioClientSocketChannelFactory socketChannelFactory = new NioClientSocketChannelFactory(
            Executors.newCachedThreadPool(),
            Executors.newCachedThreadPool());
    protected static final ObjectMapper objectMapper = new ObjectMapper();

    public abstract ChannelFuture connect() throws URISyntaxException;
    public abstract ChannelFuture disconnect();

    public static SockJsClient newClient(URI url, Protocol protocol, final SessionCallback callback) {
        ClientBootstrap bootstrap = new ClientBootstrap(socketChannelFactory);

        switch (protocol) {
            case WEBSOCKET:
                final WebSocketClient clientHandler = new WebSocketClient(bootstrap, url, callback);

                bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
                    public ChannelPipeline getPipeline() throws Exception {
                        ChannelPipeline pipeline = Channels.pipeline();
                        pipeline.addLast("decoder", new HttpResponseDecoder());
                        pipeline.addLast("encoder", new HttpRequestEncoder());
                        pipeline.addLast("ws-handler", clientHandler);
                        return pipeline;
                    }
                });

                return clientHandler;
        }

        throw new IllegalArgumentException("Invalid protocol specified");
    }

    public static enum Protocol {
        WEBSOCKET

        /* Not yet implemented
        XDR_STREAMING,
        XHR_STREAMING,
        IFRAME_EVENTSOURCE,
        IFRAME_HTMLFILE,
        XDR_POLLING,
        XHR_POLLING,
        IFRAME_XHR_POLLING,
        JSONP_POLLING
        */
    }
}
