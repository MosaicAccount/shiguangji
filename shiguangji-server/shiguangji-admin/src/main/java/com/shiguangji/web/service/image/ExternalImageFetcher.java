package com.shiguangji.web.service.image;

import com.shiguangji.common.exception.ServiceException;

/**
 * 外链图片抓取器（0.0.6 FR-006-5，issue #68）
 *
 * <p>传输安全契约的全部所在：服务器替用户访问任意网址（SSRF 面），实现必须满足——</p>
 * <ol>
 *   <li>只允许 http(s)，URL 解析失败即拒；</li>
 *   <li>DNS 解析全部 A/AAAA 记录，任一 IP 命中回环 / 私网 / 链路本地 / 保留段（含
 *       CGNAT、IPv4 映射的 IPv6）即拒；解析后连接必须绑到已校验的 IP（rebinding 对策）；</li>
 *   <li>重定向逐跳重新校验（含重新解析），限 {@code sgj.image-fetch.max-redirects} 跳；</li>
 *   <li>连接 / 读取超时（{@code connect-timeout-ms} / {@code read-timeout-ms}）；</li>
 *   <li>响应体流式读取，超过 {@code sgj.image-fetch.max-bytes} 即断开拒收。</li>
 * </ol>
 *
 * <p>完整行为规格与测试要点见 {@code docs/design/external-images-design.md} §4。
 * {@code NoopExternalImageFetcher} 是真实现落地前的占位；真实现以 @Component 注册即自动退位。</p>
 *
 * @author shiguangji
 */
public interface ExternalImageFetcher
{
    /**
     * 抓取一张外链图片
     *
     * @param imageUrl 绝对 http(s) 地址（协议相对 // 已由调用方补全为 https:）
     * @return 字节与响应 Content-Type
     * @throws ServiceException 任何拒绝 / 失败都以一句可读中文抛出（前端逐条展示，结论 69）
     */
    FetchedImage fetch(String imageUrl);

    /** 抓取结果：字节 + 响应头 Content-Type（对方未给时为 application/octet-stream） */
    record FetchedImage(byte[] bytes, String contentType)
    {
    }
}
