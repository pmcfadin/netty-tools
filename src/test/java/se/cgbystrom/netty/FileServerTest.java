package se.cgbystrom.netty;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import static org.junit.Assert.*;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class FileServerTest {

    @Test
    public void fromFileSystem() throws IOException, InterruptedException {
        final String content = "Testing the file system";
        File f = createTemporaryFile(content);
        int port = startServer(new ChunkedWriteHandler(), new FileServerHandler(f.getParent()));
        Thread.sleep(1000);

        assertEquals(content, get("http://localhost:" + port + "/" + f.getName()));
    }

    @Test
    public void fromClassPath() throws IOException, InterruptedException {
        int port = startServer(new ChunkedWriteHandler(), new FileServerHandler("classpath:///"));
        Thread.sleep(1000);

        assertEquals("Testing the class path", get("http://localhost:" + port + "/test.txt"));
    }

    public static File createTemporaryFile(String content) throws IOException {
        File f = File.createTempFile("FileServerTest", null);
        f.deleteOnExit();
        BufferedWriter out = new BufferedWriter(new FileWriter(f));
        out.write(content);
        out.close();
        return f;
    }

    public static String get(String url) throws IOException {
        return get(url, 200);
    }

    public static String get(String url, int expectedStatusCode) throws IOException {
        HttpClient client = new HttpClient();
        GetMethod method = new GetMethod(url);

        assertEquals(expectedStatusCode, client.executeMethod(method));
        return new String(method.getResponseBody());
    }

    public static int startServer(ChannelHandler... handlers) {
        ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool()));

        ChannelPipeline pipeline = bootstrap.getPipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(65536));
        pipeline.addLast("encoder", new HttpResponseEncoder());

        for (ChannelHandler handler : handlers) {
            pipeline.addLast("handler_" + handler.toString(), handler);
        }

        return bindBootstrap(bootstrap, 0);
    }

    private static int bindBootstrap(ServerBootstrap bootstrap, int retryCount) {
        try {
            bootstrap.bind(new InetSocketAddress(18080 + retryCount));
        } catch (ChannelException e) {
            retryCount++;
            if (retryCount < 100) {
               return bindBootstrap(bootstrap, retryCount);
            }
        }

        return 18080 + retryCount;
    }
}
