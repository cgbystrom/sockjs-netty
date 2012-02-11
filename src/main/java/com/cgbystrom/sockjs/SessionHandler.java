package com.cgbystrom.sockjs;

import org.jboss.netty.channel.*;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * Responsible for handling SockJS sessions.
 * It is a stateful channel handler and tied to each session.
 * Only session specific logic and is unaware of underlying transport.
 * This is by design and Netty enables a clean way to do this through the pipeline and handlers.
 */
public class SessionHandler extends SimpleChannelHandler implements Session {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SessionHandler.class);
    public enum State { CONNECTING, OPEN, CLOSED }

    private String id;
    private Service service;
    private Channel channel;
    private State state = State.CONNECTING;
    private final LinkedList<SockJsMessage> messageQueue = new LinkedList<SockJsMessage>();

    protected SessionHandler(String id, Service service) {
        this.id = id;
        this.service = service;
        if (logger.isDebugEnabled())
            logger.debug("Session " + id + " created");
    }

    @Override
    public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (logger.isDebugEnabled())
            logger.debug("Session " + id + " connected " + e.getChannel());

        // FIXME: Check if session has expired
        // FIXME: Check if session is locked (another handler already uses it), all but WS can do this


        if (state == State.CONNECTING) {
            setState(State.OPEN);
            setChannel(e.getChannel());
            e.getChannel().write(Frame.openFrame());
            // FIXME: Ability to reject a connection here by returning false in callback to onOpen?
            service.onOpen(this);
            // FIXME: Either start the heartbeat or flush pending messages in queue
            flush();
        } else if (state == State.OPEN) {
            if (channel != null) {
                logger.debug("Session " + id + " already have a channel connected. " + channel);
                throw new LockException(channel);
            }
            setChannel(e.getChannel());
            logger.debug("Session " + id + " is open, flushing..");
            flush();
        } else if (state == State.CLOSED) {
            logger.debug("Session " + id + " is closed, go away.");
            e.getChannel().write(Frame.closeFrame(3000, "Go away!")).addListener(ChannelFutureListener.CLOSE);
        } else {
            throw new Exception("Invalid channel state: " + state);
        }
    }

    @Override
    public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        logger.debug("Session " + id + " underlying channel closed " + e.getChannel());
        // FIXME: Stop any heartbeat
        // FIXME: Timer to expire the connection? Should not close session here.
        // FIXME: Notify the service? Unless timeout etc, disconnect it?
        removeChannel(e.getChannel());
        super.channelClosed(ctx, e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        SockJsMessage msg = (SockJsMessage)e.getMessage();
        logger.debug("Session " + id + " received message: " + msg.getMessage());
        service.onMessage(this, msg.getMessage());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        boolean shouldSilence = service.onError(this, e.getCause());
        if (!shouldSilence) {
            super.exceptionCaught(ctx, e);
        }
    }

    @Override
    public synchronized void send(String message) {
        final SockJsMessage msg = new SockJsMessage(message);
        // Check and see if we can send the message straight away
        if (channel != null && channel.isWritable() && messageQueue.size() == 0) {
            channel.write(Frame.messageFrame(msg));
        } else {
            messageQueue.addLast(msg);
            flush();
        }
    }

    @Override
    public void close() {
        close(3000, "Go away!");
    }

    public synchronized void close(int code, String message) {
        if (state != State.CLOSED) {
            logger.debug("Session " + id + " code initiated close, closing...");
            if (channel != null) {
                setState(State.CLOSED);
                channel.write(Frame.closeFrame(code, message)).addListener(ChannelFutureListener.CLOSE);
                // FIXME: Should we really call onClose here? Potentially calling it twice for same session close?
                // FIXME: Save this close code and reason
                service.onClose(this);
            }
        }
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
        logger.debug("Session " + id + " channel added");
    }

    public void setState(State state) {
        this.state = state;
        logger.debug("Session " + id + " state changed to " + state);
    }

    private synchronized void removeChannel(Channel channel) {
        if (this.channel != channel && this.channel != null) {
            return;
        }
        this.channel = null;
        logger.debug("Session " + id + " channel removed. " + channel);
    }

    private synchronized void flush() {
        if (channel == null || !channel.isWritable()) {
            return;
        }

        if (messageQueue.size() > 0) {
            logger.debug("Session " + id + " flushing queue");
            channel.write(Frame.messageFrame(new ArrayList<SockJsMessage>(messageQueue).toArray(new SockJsMessage[messageQueue.size()])));
            messageQueue.clear();
        }
    }

    public static class NotFoundException extends Exception {
        public NotFoundException(String baseUrl, String sessionId) {
            super("Session '" + sessionId + "' not found in service '" + baseUrl + "'");
        }
    }

    public static class LockException extends Exception {
        public LockException(Channel channel) {
            super("Session is locked by channel " + channel + ". Please disconnect other channel first before trying to register it with a session.");
        }
    }
}
