package com.cgbystrom.sockjs.transports;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

/**
* Created with IntelliJ IDEA.
* User: cbystrom
* Date: 2013-05-21
* Time: 16:26
* To change this template use File | Settings | File Templates.
*/
public class TransportMetrics {
    public final Counter sessionsOpen;
    public final Meter sessionsOpened;
    public final Counter connectionsOpen;
    public final Meter connectionsOpened;
    public final Meter messagesReceived;
    public final Histogram messagesReceivedSize;
    public final Meter messagesSent;
    public final Histogram messagesSentSize;
    private final String prefix;
    private final String transport;

    public TransportMetrics(String prefix, String transport, MetricRegistry metrics) {
        this.prefix = prefix;
        this.transport = transport;
        sessionsOpen = metrics.counter(getName("sessionsOpen"));
        sessionsOpened = metrics.meter(getName("sessionsOpened"));
        connectionsOpen = metrics.counter(getName("connectionsOpen"));
        connectionsOpened = metrics.meter(getName("connectionsOpened"));
        messagesReceived = metrics.meter(getName("messagesReceived"));
        messagesReceivedSize = metrics.histogram(getName("messagesReceivedSize"));
        messagesSent = metrics.meter(getName("messagesSent"));
        messagesSentSize = metrics.histogram(getName("messagesSentSize"));
    }

    private String getName(String name) {
        return MetricRegistry.name(prefix, transport, name);
    }
}
