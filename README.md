# sockjs-netty

An implementation of SockJS for Java using JBoss Netty. It is currently not finished and not production ready.
However, all transports offered by SockJS have been implemented and the issues remaining are minor.

## What is SockJS?
SockJS is a browser JavaScript library that provides a WebSocket-like object. SockJS gives you a coherent, cross-browser, Javascript API which creates a low latency, full duplex, cross-domain communication channel between the browser and the web server.

Under the hood SockJS tries to use native WebSockets first. If that fails it can use a variety of browser-specific transport protocols and presents them through WebSocket-like abstractions.

SockJS is intended to work for all modern browsers and in environments which don't support WebSocket protcol, for example behind restrictive corporate proxies.

Read more at http://sockjs.org

## Installing
sockjs-netty is packaged as a library for JBoss Netty and not as a standalone server. Rather than installing a prepackaged JAR, you are required to include it as you would with any other Java library.

Intention is to make it available through Maven Central once it is stable enough. But right now you will have to build it from source with Maven:

    mvn package
    
That will output a JAR inside the ```target/``` folder.

## What's missing?
Currently, not all tests provided by the SockJS protocol pass. As mentioned, it is still work in progress and the goal is naturally to be 100% compatible with the protocol.