package com.shiguangji.web.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.shiguangji.common.exception.ServiceException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * 抓取器 SSRF 矩阵单测（issue #68，测试用例 T-56 ~ T-61；行为规格见设计文档 §4）。
 *
 * <p>用 JDK 自带的 {@link HttpServer} 起本地桩：拒绝向量的地址（127.0.0.1 / localhost）天然
 * 指向它——既验证「拒」也验证「拒在连接之前」（桩的命中计数不变）；成功路径 / 重定向链 /
 * 体积与超时上限经 {@code allow-private-ranges=true} 测试开关放行内网后在本机验证。
 * 不依赖 Spring 容器：@Value 字段用 ReflectionTestUtils 注入，配置绑定由装配冒烟覆盖。</p>
 */
class ExternalImageFetcherTest
{
    private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4 };

    private static HttpServer server;

    private static String base;

    /** 桩的累计命中数：拒绝断言靠它证明「校验发生在建连之前」 */
    private static final AtomicLong hits = new AtomicLong();

    @BeforeAll
    static void startStub() throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/a.png", counting(ex -> serveBytes(ex, 200, "image/png", PNG)));
        // sendResponseHeaders(200, 0) = chunked 传输，覆盖分块解码口径
        server.createContext("/chunked.png", counting(ex -> serveChunked(ex, "image/png", PNG)));
        server.createContext("/hop1", counting(ex -> redirect(ex, "/hop2")));
        server.createContext("/hop2", counting(ex -> serveBytes(ex, 200, "image/png", PNG)));
        server.createContext("/bad-redirect", counting(ex -> redirect(ex, "file:///etc/passwd")));
        server.createContext("/loop", counting(ex -> redirect(ex, "/loop")));
        server.createContext("/big.png", counting(ex -> serveBytes(ex, 200, "image/png",
                new byte[100])));
        server.createContext("/missing", counting(ex -> ex.sendResponseHeaders(404, -1)));
        server.createContext("/slow.png", counting(ex -> {
            try
            {
                Thread.sleep(900);
            }
            catch (InterruptedException ignored)
            {
                Thread.currentThread().interrupt();
            }
            serveBytes(ex, 200, "image/png", PNG);
        }));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopStub()
    {
        server.stop(0);
    }

    // ------------------------------------------------------------------
    // T-56：非 http(s) 协议一律拒
    // ------------------------------------------------------------------

    @Test
    void nonHttpSchemesRejected()
    {
        for (String url : List.of(
                "file:///etc/passwd",
                "ftp://" + base.substring("http://".length()) + "/a.png",
                "data:image/png;base64,AAAA",
                "gopher://127.0.0.1/x",
                "not-a-url"))
        {
            assertThatThrownBy(() -> newFetcher(true).fetch(url))
                    .as("应拒绝：" + url)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("仅支持 http(s)");
        }
    }

    // ------------------------------------------------------------------
    // T-57：内网 / 保留地址拒，且拒在连接之前（桩命中数不变）
    // ------------------------------------------------------------------

    @Test
    void loopbackAndPrivateRangesRejectedBeforeConnecting()
    {
        long before = hits.get();
        List<String> rejected = List.of(
                base + "/a.png",
                base.replace("127.0.0.1", "localhost") + "/a.png",
                "http://10.0.0.1/a.png",
                "http://172.16.0.1/a.png",
                "http://192.168.1.1/a.png",
                "http://169.254.1.1/a.png",
                "http://100.64.0.1/a.png",
                "http://0.0.0.1/a.png",
                "http://198.18.0.1/a.png",
                "http://240.0.0.1/a.png",
                "http://[::1]/a.png",
                "http://[::ffff:127.0.0.1]/a.png",
                "http://[::ffff:192.168.1.1]/a.png",
                "http://[fd12::1]/a.png");
        for (String url : rejected)
        {
            assertThatThrownBy(() -> newFetcher(false).fetch(url))
                    .as("应拒绝：" + url)
                    .isInstanceOf(ServiceException.class)
                    .hasMessageContaining("内网或保留网段");
        }
        assertThat(hits.get()).as("段校验必须发生在建连之前，桩不得被请求").isEqualTo(before);
    }

    @Test
    void unresolvableHostRejectedReadable()
    {
        assertThatThrownBy(() -> newFetcher(false).fetch("http://nonexistent-qaz7f.invalid/a.png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("域名无法解析");
    }

    // ------------------------------------------------------------------
    // T-58：成功路径（allow-private-ranges=true + 本地桩）
    // ------------------------------------------------------------------

    @Test
    void pngServedWithFixedLengthIsReturned()
    {
        ExternalImageFetcher.FetchedImage image = newFetcher(true).fetch(base + "/a.png");
        assertThat(image.bytes()).isEqualTo(PNG);
        assertThat(image.contentType()).isEqualTo("image/png");
    }

    @Test
    void chunkedBodyIsDecoded()
    {
        ExternalImageFetcher.FetchedImage image = newFetcher(true).fetch(base + "/chunked.png");
        assertThat(image.bytes()).as("chunked 分块解码后应还原出原字节").isEqualTo(PNG);
    }

    // ------------------------------------------------------------------
    // T-59：重定向逐跳重验
    // ------------------------------------------------------------------

    @Test
    void relativeRedirectChainIsFollowed()
    {
        ExternalImageFetcher.FetchedImage image = newFetcher(true).fetch(base + "/hop1");
        assertThat(image.bytes()).isEqualTo(PNG);
        assertThat(hits.get()).as("两跳都应到达桩").isGreaterThanOrEqualTo(2);
    }

    @Test
    void redirectToDisallowedSchemeRejected()
    {
        // allow-private-ranges=true 放行的是段校验，协议校验不放宽——跳到 file:// 必须拒
        assertThatThrownBy(() -> newFetcher(true).fetch(base + "/bad-redirect"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅支持 http(s)");
    }

    @Test
    void redirectLoopRejectedAfterLimit()
    {
        assertThatThrownBy(() -> newFetcher(true, 2, -1).fetch(base + "/loop"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("重定向次数超限");
    }

    // ------------------------------------------------------------------
    // T-60 / T-61：体积上限与非 200
    // ------------------------------------------------------------------

    @Test
    void oversizeBodyRejected()
    {
        assertThatThrownBy(() -> newFetcher(true, 3, 16).fetch(base + "/big.png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超过大小上限");
    }

    @Test
    void non200RejectedWithStatus()
    {
        assertThatThrownBy(() -> newFetcher(true).fetch(base + "/missing"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("对方返回 HTTP 404");
    }

    @Test
    void slowResponseTimesOut()
    {
        HttpExternalImageFetcher fetcher = build(1000, 300, 3, 10485760, true);
        assertThatThrownBy(() -> fetcher.fetch(base + "/slow.png"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("超时");
    }

    // ------------------------------------------------------------------
    // 桩与夹具
    // ------------------------------------------------------------------

    /** @param maxBytesOverride 传 -1 表示用默认 10MB；@param maxRedirectsOverride 同理 */
    private static HttpExternalImageFetcher newFetcher(boolean allowPrivate)
    {
        return newFetcher(allowPrivate, 3, -1);
    }

    private static HttpExternalImageFetcher newFetcher(boolean allowPrivate, int maxRedirects, long maxBytesOverride)
    {
        return build(1000, 2000, maxRedirects, maxBytesOverride < 0 ? 10485760L : maxBytesOverride, allowPrivate);
    }

    private static HttpExternalImageFetcher build(int connectMs, int readMs, int redirects, long maxBytes,
            boolean allowPrivate)
    {
        HttpExternalImageFetcher fetcher = new HttpExternalImageFetcher();
        ReflectionTestUtils.setField(fetcher, "connectTimeoutMs", connectMs);
        ReflectionTestUtils.setField(fetcher, "readTimeoutMs", readMs);
        ReflectionTestUtils.setField(fetcher, "maxRedirects", redirects);
        ReflectionTestUtils.setField(fetcher, "maxBytes", maxBytes);
        ReflectionTestUtils.setField(fetcher, "allowPrivateRanges", allowPrivate);
        return fetcher;
    }

    private static HttpHandler counting(HttpHandler handler)
    {
        return exchange -> {
            hits.incrementAndGet();
            handler.handle(exchange);
        };
    }

    private static void serveBytes(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody())
        {
            out.write(body);
        }
    }

    /** responseLength=0 让桩走 chunked 传输，验证分块解码 */
    private static void serveChunked(HttpExchange exchange, String contentType, byte[] body) throws IOException
    {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody())
        {
            // 分两块写，桩不会把它们合并成一个 chunk
            out.write(body, 0, 4);
            out.write(body, 4, body.length - 4);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException
    {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }
}
