package com.cgbystrom.sockjs;

import java.net.SocketAddress;

public interface Session {
    public void send(String message);
    public void close();

    /**
     * Allow to get client's Address
     *
     * @return SocketAddress (ip, port etc.) of the client.
     */
    public SocketAddress getRemoteAddress();
}
