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

## Running the test server
Right now everything is packaged as a library rather than a ready-to-run server.
But there is a test server included that serves as an example and is also used for verifying the tests provided by the SockJS project.

Until a prebuilt Java binary, a JAR, is shipped you'll need to compile and run the project with Maven.
(Maven is a Java build tool used by the project).

Running the server:

 1. Install Maven ([Download it](http://maven.apache.org/download.html), 3.x should be fine, even 2.x. Or use your favorite package manager)
 1. Clone the project
 1. Run ```mvn exec:java -Dexec.mainClass="com.cgbystrom.sockjs.TestServer" -Dexec.classpathScope=test -e``` from your cloned project directory.

## What's missing?
Currently, not all tests provided by the SockJS protocol pass. As mentioned, it is still work in progress and the goal is naturally to be 100% compatible with the protocol.
The tests currently not passing are the ones testing Web Socket edge cases.