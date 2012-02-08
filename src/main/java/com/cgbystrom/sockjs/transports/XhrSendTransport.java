package com.cgbystrom.sockjs.transports;

import com.cgbystrom.sockjs.SessionHandler;
import com.cgbystrom.sockjs.SockJsMessage;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;

import java.util.List;

public class XhrSendTransport extends SimpleChannelUpstreamHandler {
    private static final Logger logger = LoggerFactory.getLogger(XhrSendTransport.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean isJsonpEnabled = false;

    public XhrSendTransport(boolean isJsonpEnabled) {
        this.isJsonpEnabled = isJsonpEnabled;
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
        // Overridden method to prevent propagation of channel state event upstream.
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        // Overridden method to prevent propagation of channel state event upstream.
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();

        if (request.getContent().readableBytes() == 0) {
            BaseTransport.respond(e.getChannel(), INTERNAL_SERVER_ERROR, "Payload expected.");
            return;
        }

        //logger.debug("Received {}", request.getContent().toString(CharsetUtil.UTF_8));

        String contentTypeHeader = request.getHeader(CONTENT_TYPE);
        if (contentTypeHeader == null) {
            contentTypeHeader = BaseTransport.CONTENT_TYPE_PLAIN;
        }

        String decodedContent;
        if (BaseTransport.CONTENT_TYPE_FORM.equals(contentTypeHeader)) {
            QueryStringDecoder decoder = new QueryStringDecoder("?" + request.getContent().toString(CharsetUtil.UTF_8));
            List<String> d = decoder.getParameters().get("d");
            if (d == null) {
                BaseTransport.respond(e.getChannel(), INTERNAL_SERVER_ERROR, "Payload expected.");
                return;
            }
            decodedContent = d.get(0);
        } else {
            decodedContent = request.getContent().toString(CharsetUtil.UTF_8);
        }

        if (decodedContent.length() == 0) {
            BaseTransport.respond(e.getChannel(), INTERNAL_SERVER_ERROR, "Payload expected.");
            return;
        }

        String[] messages = MAPPER.readValue(decodedContent, String[].class);
        for (String message : messages) {
            SockJsMessage jsMessage = new SockJsMessage(message);
            ctx.sendUpstream(new UpstreamMessageEvent(e.getChannel(), jsMessage, e.getRemoteAddress()));
        }

        if (isJsonpEnabled) {
            BaseTransport.respond(e.getChannel(), OK, "ok");
        } else {
            BaseTransport.respond(e.getChannel(), NO_CONTENT, "");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (e.getCause() instanceof JsonParseException) {
            BaseTransport.respond(e.getChannel(), HttpResponseStatus.INTERNAL_SERVER_ERROR, "Broken JSON encoding.");
        } else if (e.getCause() instanceof SessionHandler.NotFoundException) {
            BaseTransport.respond(e.getChannel(), HttpResponseStatus.NOT_FOUND, "Session not found. Cannot send data to non-existing session.");
        } else {
            super.exceptionCaught(ctx, e);
        }
    }
}
