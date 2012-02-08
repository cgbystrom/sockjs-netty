package com.cgbystrom.sockjs;

import com.cgbystrom.sockjs.transports.*;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.CharsetUtil;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServiceRouter extends SimpleChannelHandler {
    public static final String CLIENT_URL = "http://cdn.sockjs.org/sockjs-0.2.js";
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ServiceRouter.class);
    private static final Pattern SERVER_SESSION = Pattern.compile("^/([^/.]+)/([^/.]+)/");
    private static final Random random = new Random();

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, SessionHandler>> serviceSessions = new ConcurrentHashMap<String, ConcurrentHashMap<String, SessionHandler>>();
    private final Map<String, Service> services = new LinkedHashMap<String, Service>();
    private IframePage iframe;

    public ServiceRouter() {
        this.iframe = new IframePage(CLIENT_URL);
    }

    public void registerService(String baseUrl, Service service) {
        services.put(baseUrl, service);
        serviceSessions.put(baseUrl, new ConcurrentHashMap<String, SessionHandler>());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        if (logger.isDebugEnabled())
            logger.debug("URI " + request.getUri());

        for (Map.Entry<String, Service> entry : services.entrySet()) {
            // Check if there's a service registered with this URL
            String baseUrl = entry.getKey();
            Service service = entry.getValue();
            if (request.getUri().startsWith(baseUrl)) {
                handleService(ctx, e, baseUrl, service);
                super.messageReceived(ctx, e);
                return;
            }
        }

        // No match for service found, return 404
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.NOT_FOUND);
        response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
        e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void handleService(ChannelHandlerContext ctx, MessageEvent e, String baseUrl, Service service) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        request.setUri(request.getUri().replaceFirst(baseUrl, ""));
        QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        String path = qsd.getPath();

        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK);
        if (path.equals("") || path.equals("/")) {
            response.setHeader(CONTENT_TYPE, BaseTransport.CONTENT_TYPE_PLAIN);
            response.setContent(ChannelBuffers.copiedBuffer("Welcome to SockJS!\n", CharsetUtil.UTF_8));
            e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        } else if (path.startsWith("/iframe")) {
            iframe.handle(request, response);
            e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        } else if (path.startsWith("/info")) {
            response.setHeader(CONTENT_TYPE, "application/json; charset=UTF-8");
            response.setHeader(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setContent(getInfo(service.isWebSocketEnabled()));
            e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            if (!handleSession(ctx, e, baseUrl, path, service)) {
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
                e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    private boolean handleSession(ChannelHandlerContext ctx, MessageEvent e, String baseUrl, String path, Service service) throws SessionHandler.NotFoundException {
        HttpRequest request = (HttpRequest)e.getMessage();
        Matcher m = SERVER_SESSION.matcher(path);

        if (!m.find()) {
            return false;
        }

        String server = m.group(1);
        String sessionId = m.group(2);
        String transport = path.replaceFirst("/" + server + "/" + sessionId, "");
        final ChannelPipeline pipeline = ctx.getPipeline();
        boolean expectExistingSession = false;

        if (transport.equals("/xhr_send")) {
            pipeline.addLast("sockjs-xhr-send", new XhrSendTransport(false));
            expectExistingSession = true;
        } else if (transport.equals("/jsonp_send")) {
            pipeline.addLast("sockjs-jsonp-send", new XhrSendTransport(true));
            expectExistingSession = true;
        } else if (transport.equals("/xhr_streaming")) {
            pipeline.addLast("sockjs-xhr-streaming", new XhrStreamingTransport(4096));
        } else if (transport.equals("/xhr")) {
            pipeline.addLast("sockjs-xhr-polling", new XhrPollingTransport());
        } else if (transport.equals("/jsonp")) {
            pipeline.addLast("sockjs-jsonp-polling", new JsonpPollingTransport());
        } else if (transport.equals("/htmlfile")) {
            pipeline.addLast("sockjs-htmlfile-polling", new HtmlFileTransport(4096));
        } else if (transport.equals("/eventsource")) {
            pipeline.addLast("sockjs-eventsource", new EventSourceTransport(4096));
        } else if (transport.equals("/websocket")) {
            pipeline.addLast("sockjs-websocket", new WebSocketTransport(path, 4096));
        } else {
            HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.NOT_FOUND);
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.setContent(ChannelBuffers.copiedBuffer("Unknown transport: " + transport + "\n", CharsetUtil.UTF_8));
            ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
        }

        SessionHandler sessionHandler = expectExistingSession ? getSession(baseUrl, sessionId) : getOrCreateSession(baseUrl, sessionId, service);
        pipeline.addLast("sockjs-session-handler", sessionHandler);

        return true;
    }

    private SessionHandler getOrCreateSession(String baseUrl, String sessionId, Service service) {
        ConcurrentHashMap<String, SessionHandler> sessions = serviceSessions.get(baseUrl);
        SessionHandler s = sessions.get(sessionId);

        if (s != null) {
            return s;
        }

        SessionHandler newSession = new SessionHandler(sessionId, service);
        SessionHandler existingSession = sessions.putIfAbsent(sessionId, newSession);
        return (existingSession == null) ? newSession : existingSession;
    }

    private SessionHandler getSession(String baseUrl, String sessionId) throws SessionHandler.NotFoundException {
        ConcurrentHashMap<String, SessionHandler> sessions = serviceSessions.get(baseUrl);
        SessionHandler s = sessions.get(sessionId);

        if (s == null) {
            throw new SessionHandler.NotFoundException(baseUrl, sessionId);
        }

        return s;
    }

    private ChannelBuffer getInfo(boolean webSocketEnabled) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("{");
        sb.append("\"websocket\": ");
        sb.append(webSocketEnabled);
        sb.append(", ");
        sb.append("\"origins\": [\"*:*\"], ");
        sb.append("\"cookie_needed\": true, ");
        sb.append("\"entropy\": ");
        sb.append(random.nextInt());
        sb.append("}");
        return ChannelBuffers.copiedBuffer(sb.toString(), CharsetUtil.UTF_8);
    }
}
