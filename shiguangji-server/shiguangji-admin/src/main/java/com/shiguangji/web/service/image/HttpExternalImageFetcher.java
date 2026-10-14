package com.shiguangji.web.service.image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.shiguangji.common.exception.ServiceException;
import com.shiguangji.common.utils.StringUtils;

/**
 * 外链图片抓取器真实现（0.0.6 FR-006-5，issue #68；行为规格见设计文档 §4）。
 *
 * <p>SSRF 防护的核心取舍：<b>连接必须绑到已校验的 IP</b>（rebinding 对策）——JDK HttpClient
 * 没有公开的 DNS resolver 钩子，按主机名请求会留下「解析两次答案不同」的 TOCTOU 窗口，
 * 所以这里用 {@link Socket} / {@link SSLSocket} 显式 {@code connect(InetAddress, port)}，
 * TLS 的 SNI 与 Host 头仍用原主机名、并以 endpointIdentificationAlgorithm=HTTPS 做证书校验。</p>
 *
 * <p>每跳流程：解析 URL → 校验协议（仅 http/https）→ DNS 解析全部记录并逐一校验 IP 段 →
 * 绑定已校验 IP 建连 → 最小 HTTP/1.1 GET（Connection: close）→ 流式读体（Content-Length /
 * chunked / 读到 EOF，累计超限即断开）→ 3xx 取 Location 换目标**重新走完整校验**。</p>
 *
 * @author shiguangji
 */
@Component
public class HttpExternalImageFetcher implements ExternalImageFetcher
{
    /** 普通浏览器 UA（豆瓣客户端同款）：不少图床拒绝 Java 客户端标识 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    /** 会跟跳的状态码（都带 Location；方法恒为 GET，303 的语义差异对此无影响） */
    private static final List<Integer> REDIRECT_CODES = List.of(301, 302, 303, 307, 308);

    /** 响应行 / 头部的最大长度：恶意服务器可以用无换行超长响应耗尽内存，超限即拒 */
    private static final int MAX_LINE_BYTES = 8192;

    /**
     * InetAddress 自带判定盖不住的 IPv4 保留段：{网络地址(网络序 int), 前缀位数}。
     * 回环 / 私网(A/B/C) / 链路本地 / 组播由 InetAddress 的判定兜住，这张表只补特批段。
     */
    private static final int[][] BLOCKED_V4_PREFIXES = {
            { 0x00000000, 8 },   // 0.0.0.0/8 「本网络」（0.0.0.0 本身由 isAnyLocalAddress 兜）
            { 0x64400000, 10 },  // 100.64.0.0/10 CGNAT（运营商级 NAT，常与内网互通）
            { 0xC0000000, 24 },  // 192.0.0.0/24 IETF 协议分配
            { 0xC0000200, 24 },  // 192.0.2.0/24 TEST-NET-1
            { 0xC6120000, 15 },  // 198.18.0.0/15 网络基准测试
            { 0xC6336400, 24 },  // 198.51.100.0/24 TEST-NET-2
            { 0xCB007100, 24 },  // 203.0.113.0/24 TEST-NET-3
            { 0xF0000000, 4 }    // 240.0.0.0/4 保留段（含 255.255.255.255）
    };

    /**
     * IPv6 侧需要显式拒绝的整字节前缀（比较地址的前 N 字节）；v4 映射 / 兼容地址先拆出
     * 内层 IPv4 再按 {@link #BLOCKED_V4_PREFIXES} 校验。fc00::/7 非 8 位对齐，展开成 fc/fd 两条。
     */
    private static final byte[][] BLOCKED_V6_PREFIXES = {
            { (byte) 0xFC },  // fc00::/8 ULA 前半
            { (byte) 0xFD },  // fd00::/8 ULA 后半（两条合起来即 fc00::/7）
            { 0x20, 0x02 },   // 2002::/16 6to4（内嵌 IPv4）
            { 0x20, 0x01, 0x00, 0x00 } // 2001:0::/32 Teredo（内嵌 IPv4）
    };

    @Value("${sgj.image-fetch.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${sgj.image-fetch.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${sgj.image-fetch.max-redirects:3}")
    private int maxRedirects;

    @Value("${sgj.image-fetch.max-bytes:10485760}")
    private long maxBytes;

    /** 仅测试：放行内网/回环段以验证成功路径与重定向链；生产必须 false（application.yml 注释已写明） */
    @Value("${sgj.image-fetch.allow-private-ranges:false}")
    private boolean allowPrivateRanges;

    /** 一次跳的目标：解析并校验后的请求要素 */
    private static class Target
    {
        final String host;

        final int port;

        final String pathQuery;

        final InetAddress address;

        final boolean tls;

        Target(String host, int port, String pathQuery, InetAddress address, boolean tls)
        {
            this.host = host;
            this.port = port;
            this.pathQuery = pathQuery;
            this.address = address;
            this.tls = tls;
        }
    }

    /** 一次请求的响应：3xx 只取定位头与内容类型，不读体 */
    private record Response(int status, String location, String contentType, byte[] body)
    {
    }

    @Override
    public FetchedImage fetch(String imageUrl)
    {
        String current = imageUrl;
        for (int redirect = 0; redirect <= maxRedirects; redirect++)
        {
            Target target = parseAndValidate(current);
            Response response = request(target);
            if (REDIRECT_CODES.contains(response.status()))
            {
                if (StringUtils.isEmpty(response.location()))
                {
                    throw new ServiceException("对方返回 HTTP " + response.status() + " 但缺少 Location，无法继续");
                }
                current = resolveRedirect(current, response.location());
                continue;
            }
            if (response.status() != 200)
            {
                throw new ServiceException("对方返回 HTTP " + response.status());
            }
            return new FetchedImage(response.body(), response.contentType());
        }
        throw new ServiceException("重定向次数超限（超过 " + maxRedirects + " 跳）");
    }

    // ------------------------------------------------------------------
    // 解析与校验（每跳都完整走一遍，含重定向目标）
    // ------------------------------------------------------------------

    private Target parseAndValidate(String url)
    {
        URI uri;
        try
        {
            uri = URI.create(url);
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("图片地址无法解析：" + url);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme))
        {
            throw new ServiceException("仅支持 http(s) 图片地址");
        }
        String host = uri.getHost();
        if (StringUtils.isEmpty(host) || uri.getUserInfo() != null)
        {
            // 带 userinfo（user:pass@host）的地址不充当凭据跳板，直接拒
            throw new ServiceException("图片地址缺少主机名或带凭据，不允许转存");
        }
        int port = uri.getPort();
        if (port == -1)
        {
            port = "https".equals(scheme) ? 443 : 80;
        }
        else if (port < 1 || port > 65535)
        {
            throw new ServiceException("图片地址端口非法");
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String pathQuery = uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();

        InetAddress[] addresses;
        try
        {
            addresses = InetAddress.getAllByName(host);
        }
        catch (UnknownHostException e)
        {
            throw new ServiceException("域名无法解析：" + host);
        }
        for (InetAddress address : addresses)
        {
            assertAddressAllowed(address, host);
        }
        // 连接绑定首个已校验记录：全部记录都过了段校验，取哪个都不会打到内网
        return new Target(host, port, pathQuery, addresses[0], "https".equals(scheme));
    }

    /** IP 段校验（allow-private-ranges=true 时整体跳过，协议校验保留——测试开关的唯一让步） */
    private void assertAddressAllowed(InetAddress address, String host)
    {
        if (allowPrivateRanges)
        {
            return;
        }
        // 内建判定先跑：回环 / 私网(A/B/C) / 链路本地 / 组播。注意它们认不出 v4 映射形式的
        // IPv6（JDK 对 ::ffff:127.0.0.1 的 isLoopbackAddress 是 false），映射地址走下面的拆套
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress())
        {
            throw rejectAddress(host);
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4)
        {
            assertV4Allowed(v4Of(raw, 0), host);
        }
        else if (isV4Embedded(raw))
        {
            // IPv4 映射（::ffff:a.b.c.d）与兼容（::a.b.c.d）：拆出内层 v4 完整重跑本方法
            // （内建判定 + 显式表），任何映射形式的内网 / 保留段都拦得住
            assertAddressAllowed(v4EmbeddedOf(raw), host);
        }
        else
        {
            for (byte[] prefix : BLOCKED_V6_PREFIXES)
            {
                if (prefixMatches(raw, prefix))
                {
                    throw rejectAddress(host);
                }
            }
        }
    }

    /** 取 v6 地址内嵌的 v4（最后 4 字节）为 InetAddress；4 字节入参不会抛 UnknownHostException */
    private static InetAddress v4EmbeddedOf(byte[] raw)
    {
        try
        {
            return InetAddress.getByAddress(Arrays.copyOfRange(raw, 12, 16));
        }
        catch (UnknownHostException e)
        {
            throw new IllegalStateException("4 字节地址不会解析失败", e);
        }
    }

    private void assertV4Allowed(int v4, String host)
    {
        for (int[] cidr : BLOCKED_V4_PREFIXES)
        {
            int mask = cidr[1] == 0 ? 0 : (0xFFFFFFFF << (32 - cidr[1]));
            if ((v4 & mask) == (cidr[0] & mask))
            {
                throw rejectAddress(host);
            }
        }
    }

    private static ServiceException rejectAddress(String host)
    {
        return new ServiceException("图片地址指向内网或保留网段，不允许转存：" + host);
    }

    /** 前 12 字节全 0（v4 兼容）或第 11、12 字节为 0xff（v4 映射） */
    private static boolean isV4Embedded(byte[] raw)
    {
        boolean leadingZero = true;
        for (int i = 0; i < 12; i++)
        {
            leadingZero &= raw[i] == 0;
        }
        return leadingZero || (raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF);
    }

    /** 从字节串的 offset 起取 4 字节拼成 int（网络序） */
    private static int v4Of(byte[] raw, int offset)
    {
        return ((raw[offset] & 0xFF) << 24) | ((raw[offset + 1] & 0xFF) << 16)
                | ((raw[offset + 2] & 0xFF) << 8) | (raw[offset + 3] & 0xFF);
    }

    /** 逐字节比较地址前 prefix.length 字节（黑名单段全部整字节对齐，无需掩码） */
    private static boolean prefixMatches(byte[] address, byte[] prefix)
    {
        return Arrays.equals(address, 0, prefix.length, prefix, 0, prefix.length);
    }

    /** 相对 Location 按当前 URL 解析为绝对地址（下一跳会重新走完整校验） */
    private static String resolveRedirect(String baseUrl, String location)
    {
        try
        {
            return URI.create(baseUrl).resolve(location).toString();
        }
        catch (IllegalArgumentException e)
        {
            throw new ServiceException("重定向地址无法解析：" + location);
        }
    }

    // ------------------------------------------------------------------
    // 建连与请求（连接绑定已校验 IP；TLS 用原主机名做 SNI 与证书校验）
    // ------------------------------------------------------------------

    private Response request(Target target)
    {
        try (Socket socket = connect(target))
        {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + target.pathQuery + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader(target) + "\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n"
                    + "Accept: image/*\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return readResponse(socket.getInputStream());
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (java.net.SocketTimeoutException e)
        {
            throw new ServiceException("抓取超时，对方响应太慢");
        }
        catch (IOException e)
        {
            throw new ServiceException("抓取失败：" + e.getMessage());
        }
    }

    private Socket connect(Target target) throws IOException
    {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(target.address, target.port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        if (!target.tls)
        {
            return socket;
        }
        SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(socket, target.host, target.port, true);
        SSLParameters parameters = sslSocket.getSSLParameters();
        // SSLSocket 默认不做主机名校验（那是 HttpsURLConnection 的行为），必须显式打开
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        if (!isIpLiteral(target.host))
        {
            parameters.setServerNames(List.of((SNIServerName) new SNIHostName(target.host)));
        }
        sslSocket.setSSLParameters(parameters);
        sslSocket.startHandshake();
        return sslSocket;
    }

    private static String hostHeader(Target target)
    {
        boolean defaultPort = (target.tls && target.port == 443) || (!target.tls && target.port == 80);
        return defaultPort ? target.host : target.host + ":" + target.port;
    }

    private static boolean isIpLiteral(String host)
    {
        return host.indexOf(':') >= 0 || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    // ------------------------------------------------------------------
    // 响应读取：状态行 + 头 + 体（chunked 与读到 EOF 两口径，都有体积与超时上限）
    // ------------------------------------------------------------------

    private Response readResponse(InputStream in) throws IOException
    {
        int status = parseStatus(readLine(in));
        String location = null;
        String contentType = "application/octet-stream";
        boolean chunked = false;
        String header = readLine(in);
        while (!header.isEmpty())
        {
            int colon = header.indexOf(':');
            if (colon > 0)
            {
                String name = header.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = header.substring(colon + 1).trim();
                if ("location".equals(name) && location == null)
                {
                    location = value;
                }
                else if ("content-type".equals(name) && "application/octet-stream".equals(contentType))
                {
                    contentType = value;
                }
                else if ("transfer-encoding".equals(name) && value.toLowerCase(Locale.ROOT).contains("chunked"))
                {
                    chunked = true;
                }
            }
            header = readLine(in);
        }
        if (REDIRECT_CODES.contains(status))
        {
            return new Response(status, location, contentType, new byte[0]);
        }
        // 无 Transfer-Encoding 时按「读到 EOF」处理：我们发了 Connection: close，服务器会在
        // 体后关连接；这比信 Content-Length 更稳（对方虚报长度也不会多读或少读出体边界）
        byte[] body = chunked ? readChunkedBody(in) : readCapped(in, Long.MAX_VALUE);
        return new Response(status, location, contentType, body);
    }

    private static int parseStatus(String statusLine)
    {
        if (statusLine == null || !statusLine.startsWith("HTTP/"))
        {
            throw new ServiceException("对方返回的不是有效的 HTTP 响应");
        }
        try
        {
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
        catch (Exception e)
        {
            throw new ServiceException("对方返回的 HTTP 状态行无法解析：" + statusLine);
        }
    }

    /** 读一行（\n 结尾，\r 剔除）；超长视为恶意响应直接拒 */
    private String readLine(InputStream in) throws IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1)
        {
            if (b == '\n')
            {
                byte[] bytes = buffer.toByteArray();
                int end = bytes.length - (bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? 1 : 0);
                return new String(bytes, 0, end, StandardCharsets.ISO_8859_1);
            }
            buffer.write(b);
            if (buffer.size() > MAX_LINE_BYTES)
            {
                throw new ServiceException("对方响应的头部异常，已中止");
            }
        }
        throw new ServiceException("对方提前断开了连接");
    }

    private byte[] readChunkedBody(InputStream in) throws IOException
    {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true)
        {
            String sizeLine = readLine(in).split(";")[0].trim();
            int size;
            try
            {
                size = Integer.parseInt(sizeLine, 16);
            }
            catch (NumberFormatException e)
            {
                throw new ServiceException("对方响应的 chunked 数据异常，已中止");
            }
            if (size == 0)
            {
                String trailer = readLine(in);
                while (!trailer.isEmpty())
                {
                    trailer = readLine(in);
                }
                return body.toByteArray();
            }
            byte[] chunk = readCapped(in, size);
            body.write(chunk, 0, chunk.length);
            readLine(in); // chunk 数据后的 CRLF
        }
    }

    /** 流式读取并施加累计体积与读取总超时（deadline 跨 read 生效，不被慢速滴流绕过） */
    private byte[] readCapped(InputStream in, long expected) throws IOException
    {
        long deadline = System.nanoTime() + readTimeoutMs * 1_000_000L;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (body.size() < expected)
        {
            if (System.nanoTime() > deadline)
            {
                throw new ServiceException("抓取超时，对方响应太慢");
            }
            int maxRead = (int) Math.min(buffer.length, expected - body.size());
            int n = in.read(buffer, 0, maxRead);
            if (n == -1)
            {
                break;
            }
            body.write(buffer, 0, n);
            if (body.size() > maxBytes)
            {
                throw new ServiceException("图片超过大小上限（" + (maxBytes / 1024 / 1024) + "MB）");
            }
        }
        return body.toByteArray();
    }
}
