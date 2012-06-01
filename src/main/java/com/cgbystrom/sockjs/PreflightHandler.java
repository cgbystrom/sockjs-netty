package com.cgbystrom.sockjs;

import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;

public class PreflightHandler extends SimpleChannelHandler {
    private String origin = null;
    private String corsHeaders = null;

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpRequest) {
            HttpRequest request = (HttpRequest)e.getMessage();

            String originHeader = request.getHeader("Origin");
            if (originHeader != null)
                origin = originHeader;

            corsHeaders = request.getHeader("Access-Control-Request-Headers");

            System.out.println(request.getMethod().toString() + " " + request.getUri());
            if (request.getMethod().equals(HttpMethod.OPTIONS)) {
                HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.NO_CONTENT);
                response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
                response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "max-age=31536000, public");
                response.setHeader("Access-Control-Max-Age", "31536000");

                // FIXME: Dirty, handle per transport?
                if (request.getUri().contains("/xhr")) {
                    response.setHeader("Access-Control-Allow-Methods", "OPTIONS, POST");
                } else {
                    response.setHeader("Access-Control-Allow-Methods", "OPTIONS, GET");
                }

                response.setHeader("Access-Control-Allow-Headers", "Content-Type");
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader(HttpHeaders.Names.EXPIRES, "FIXME"); // FIXME: Fix this
                response.setHeader(HttpHeaders.Names.SET_COOKIE, "JSESSIONID=dummy; path=/");
                ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
                return;
            }
        }
        super.messageReceived(ctx, e);
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        if (e.getMessage() instanceof HttpResponse) {
            HttpResponse response = (HttpResponse)e.getMessage();
            response.setHeader("Access-Control-Allow-Origin", origin == null || "null".equals(origin) ? "*" : origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");

            if (corsHeaders != null) {
                response.setHeader("Access-Control-Allow-Headers", corsHeaders);
            }
        }
        super.writeRequested(ctx, e);
    }
}
