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
import static org.jboss.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;

import java.io.IOException;
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
    private enum SessionCreation { CREATE_OR_REUSE, FORCE_REUSE, FORCE_CREATE }

    private final Map<String, ServiceMetadata> services = new LinkedHashMap<String, ServiceMetadata>();
    private IframePage iframe;

    public ServiceRouter() {
        this.iframe = new IframePage(CLIENT_URL);
    }

    public synchronized ServiceMetadata registerService(String baseUrl, final SessionCallback service,
                boolean isWebSocketEnabled, int maxResponseSize, boolean isSsl) {
        return registerService(baseUrl, new SessionCallbackFactory() {
            @Override
            public SessionCallback getSession(String id) throws Exception {
                return service;
            }
        }, isWebSocketEnabled, maxResponseSize, isSsl);
    }

    public synchronized ServiceMetadata registerService(String baseUrl, SessionCallbackFactory sessionFactory,
                boolean isWebSocketEnabled, int maxResponseSize, boolean isSsl) {
        ServiceMetadata sm = new ServiceMetadata(baseUrl, sessionFactory,
                new ConcurrentHashMap<String, SessionHandler>(), isWebSocketEnabled, maxResponseSize, isSsl);
        services.put(baseUrl, sm);
        return sm;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        if (logger.isDebugEnabled())
            logger.debug("URI " + request.getUri());

        for (ServiceMetadata serviceMetadata : services.values()) {
            // Check if there's a service registered with this URL
            if (request.getUri().startsWith(serviceMetadata.url)) {
                handleService(ctx, e, serviceMetadata);
                super.messageReceived(ctx, e);
                return;
            }
        }

        // No match for service found, return 404
        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.NOT_FOUND);
        response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
        writeResponse(e.getChannel(), request, response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        final String connectionClosedMsg = "An existing connection was forcibly closed by the remote host";
        final Throwable t = e.getCause();

        if (t instanceof IOException && t.getMessage().equalsIgnoreCase(connectionClosedMsg)) {
            logger.debug("Unexpected close (may be safe to ignore).");
        } else {
            super.exceptionCaught(ctx, e);
        }
    }

    private void handleService(ChannelHandlerContext ctx, MessageEvent e, ServiceMetadata serviceMetadata) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        request.setUri(request.getUri().replaceFirst(serviceMetadata.url, ""));
        QueryStringDecoder qsd = new QueryStringDecoder(request.getUri());
        String path = qsd.getPath();

        HttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), HttpResponseStatus.OK);
        if (path.equals("") || path.equals("/")) {
            response.setHeader(CONTENT_TYPE, BaseTransport.CONTENT_TYPE_PLAIN);
            response.setContent(ChannelBuffers.copiedBuffer("Welcome to SockJS!\n", CharsetUtil.UTF_8));
            writeResponse(e.getChannel(), request, response);
        } else if (path.startsWith("/iframe")) {
            iframe.handle(request, response);
            writeResponse(e.getChannel(), request, response);
        } else if (path.startsWith("/info")) {
            response.setHeader(CONTENT_TYPE, "application/json; charset=UTF-8");
            response.setHeader(CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0");
            response.setContent(getInfo(serviceMetadata));
            writeResponse(e.getChannel(), request, response);
        } else if (path.startsWith("/websocket")) {
            // Raw web socket
            ctx.getPipeline().addLast("sockjs-websocket", new RawWebSocketTransport(path));
            SessionHandler sessionHandler = getOrCreateSession(serviceMetadata.url, "rawwebsocket-" + random.nextLong(), serviceMetadata.factory, true);
            ctx.getPipeline().addLast("sockjs-session-handler", sessionHandler);
        } else {
            if (!handleSession(ctx, e, path, serviceMetadata)) {
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                response.setContent(ChannelBuffers.copiedBuffer("Not found", CharsetUtil.UTF_8));
                writeResponse(e.getChannel(), request, response);
            }
        }
    }

    private boolean handleSession(ChannelHandlerContext ctx, MessageEvent e, String path, ServiceMetadata serviceMetadata) throws Exception {
        HttpRequest request = (HttpRequest)e.getMessage();
        Matcher m = SERVER_SESSION.matcher(path);

        if (!m.find()) {
            return false;
        }

        String server = m.group(1);
        String sessionId = m.group(2);
        String transport = path.replaceFirst("/" + server + "/" + sessionId, "");
        final ChannelPipeline pipeline = ctx.getPipeline();
        SessionCreation sessionCreation = SessionCreation.CREATE_OR_REUSE;

        if (transport.equals("/xhr_send")) {
            pipeline.addLast("sockjs-xhr-send", new XhrSendTransport(false));
            sessionCreation = SessionCreation.FORCE_REUSE; // Expect an existing session
        } else if (transport.equals("/jsonp_send")) {
            pipeline.addLast("sockjs-jsonp-send", new XhrSendTransport(true));
            sessionCreation = SessionCreation.FORCE_REUSE; // Expect an existing session
        } else if (transport.equals("/xhr_streaming")) {
            pipeline.addLast("sockjs-xhr-streaming", new XhrStreamingTransport(serviceMetadata.maxResponseSize));
        } else if (transport.equals("/xhr")) {
            pipeline.addLast("sockjs-xhr-polling", new XhrPollingTransport());
        } else if (transport.equals("/jsonp")) {
            pipeline.addLast("sockjs-jsonp-polling", new JsonpPollingTransport());
        } else if (transport.equals("/htmlfile")) {
            pipeline.addLast("sockjs-htmlfile-polling", new HtmlFileTransport(serviceMetadata.maxResponseSize));
        } else if (transport.equals("/eventsource")) {
            pipeline.addLast("sockjs-eventsource", new EventSourceTransport(serviceMetadata.maxResponseSize));
        } else if (transport.equals("/websocket")) {
            pipeline.addLast("sockjs-websocket", new WebSocketTransport(serviceMetadata.url + path, serviceMetadata));
            // Websockets should re-create a session every time
            sessionCreation = SessionCreation.FORCE_CREATE;
        } else {
            return false;
        }

        SessionHandler sessionHandler = null;
        switch (sessionCreation) {
            case CREATE_OR_REUSE:
                sessionHandler = getOrCreateSession(serviceMetadata.url, sessionId, serviceMetadata.factory, false);
                break;
            case FORCE_REUSE:
                sessionHandler = getSession(serviceMetadata.url, sessionId);
                break;
            case FORCE_CREATE:
                SessionCallback callback = serviceMetadata.factory.getSession(sessionId);
                sessionHandler = new SessionHandler(sessionId, callback);
                break;
            default:
                throw new Exception("Unknown sessionCreation value: " + sessionCreation);
        }

        pipeline.addLast("sockjs-session-handler", sessionHandler);

        return true;
    }

    private synchronized SessionHandler getOrCreateSession(String baseUrl, String sessionId, SessionCallbackFactory factory, boolean forceCreate) throws Exception {
        ConcurrentHashMap<String, SessionHandler> sessions = services.get(baseUrl).sessions;
        SessionHandler s = sessions.get(sessionId);

        if (s != null && !forceCreate) {
            return s;
        }

        SessionCallback callback = factory.getSession(sessionId);
        SessionHandler newSession = new SessionHandler(sessionId, callback);
        SessionHandler existingSession = sessions.putIfAbsent(sessionId, newSession);
        return (existingSession == null) ? newSession : existingSession;
    }

    private synchronized SessionHandler getSession(String baseUrl, String sessionId) throws SessionHandler.NotFoundException {
        ConcurrentHashMap<String, SessionHandler> sessions = services.get(baseUrl).sessions;
        SessionHandler s = sessions.get(sessionId);

        if (s == null) {
            throw new SessionHandler.NotFoundException(baseUrl, sessionId);
        }

        return s;
    }

    /** Handle conditional connection close depending on keep-alive */
    private void writeResponse(Channel channel, HttpRequest request, HttpResponse response) {
        response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());

        boolean hasKeepAliveHeader = KEEP_ALIVE.equalsIgnoreCase(request.getHeader(CONNECTION));
        if (!request.getProtocolVersion().isKeepAliveDefault() && hasKeepAliveHeader) {
            response.setHeader(CONNECTION, KEEP_ALIVE);
        }

        ChannelFuture wf = channel.write(response);
        if (!HttpHeaders.isKeepAlive(request)) {
            wf.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private ChannelBuffer getInfo(ServiceMetadata metadata) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("{");
        sb.append("\"websocket\": ");
        sb.append(metadata.isWebSocketEnabled());
        sb.append(", ");
        sb.append("\"origins\": [\"*:*\"], ");
        sb.append("\"cookie_needed\": ");
        sb.append(metadata.isJsessionid());
        sb.append(", ");
        sb.append("\"entropy\": ");
        sb.append(random.nextInt(Integer.MAX_VALUE) + 1);
        sb.append("}");
        return ChannelBuffers.copiedBuffer(sb.toString(), CharsetUtil.UTF_8);
    }

    public static class ServiceMetadata {
        private ServiceMetadata(String url, SessionCallbackFactory factory, ConcurrentHashMap<String,
                SessionHandler> sessions, boolean isWebSocketEnabled, int maxResponseSize, boolean isSsl) {
            this.url = url;
            this.factory = factory;
            this.sessions = sessions;
            this.isWebSocketEnabled = isWebSocketEnabled;
            this.maxResponseSize = maxResponseSize;
            this.isSsl = isSsl;
        }

        // FIXME: Make private.
        public String url;
        public SessionCallbackFactory factory;
        public ConcurrentHashMap<String, SessionHandler> sessions;
        public boolean isWebSocketEnabled = true;
        public int maxResponseSize;
        public boolean jsessionid = false;
        public boolean isSsl = false;

        public String getUrl() {
            return url;
        }

        public ServiceMetadata setUrl(String url) {
            this.url = url;
            return this;
        }

        public SessionCallbackFactory getFactory() {
            return factory;
        }

        public ServiceMetadata setFactory(SessionCallbackFactory factory) {
            this.factory = factory;
            return this;
        }

        public ConcurrentHashMap<String, SessionHandler> getSessions() {
            return sessions;
        }

        public ServiceMetadata setSessions(ConcurrentHashMap<String, SessionHandler> sessions) {
            this.sessions = sessions;
            return this;
        }

        public boolean isWebSocketEnabled() {
            return isWebSocketEnabled;
        }

        public ServiceMetadata setWebSocketEnabled(boolean webSocketEnabled) {
            isWebSocketEnabled = webSocketEnabled;
            return this;
        }

        public int getMaxResponseSize() {
            return maxResponseSize;
        }

        public ServiceMetadata setMaxResponseSize(int maxResponseSize) {
            this.maxResponseSize = maxResponseSize;
            return this;
        }

        public boolean isJsessionid() {
            return jsessionid;
        }

        public ServiceMetadata setJsessionid(boolean jsessionid) {
            this.jsessionid = jsessionid;
            return this;
        }
    }
}
