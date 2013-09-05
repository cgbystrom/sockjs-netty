package com.cgbystrom.sockjs;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.channel.local.LocalAddress;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.junit.Ignore;

import static org.jboss.netty.channel.Channels.pipeline;

@Ignore
public class StressTestServer {
    private final ServerBootstrap bootstrap = new ServerBootstrap(new DefaultLocalServerChannelFactory());
    private final MetricRegistry registry = new MetricRegistry();
    private final int port;

    public StressTestServer(int port) {
        this.port = port;
    }

    public void start() throws Exception {
        final JmxReporter reporter = JmxReporter.forRegistry(registry).build();
        final ServiceRouter router = new ServiceRouter();

        reporter.start();
        router.setMetricRegistry(registry);

        Service echoService = new Service("/stresstest", new SessionCallbackFactory() {
            @Override
            public StressTestSession getSession(String id) throws Exception {
                return new StressTestSession();
            }
        });
        router.registerService(echoService);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = pipeline();
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("chunkAggregator", new HttpChunkAggregator(130 * 1024)); // Required for WS handshaker or else NPE.
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("preflight", new PreflightHandler());
                pipeline.addLast("router", router);
                return pipeline;
            }
        });

        bootstrap.bind(new LocalAddress(port));
    }
}
