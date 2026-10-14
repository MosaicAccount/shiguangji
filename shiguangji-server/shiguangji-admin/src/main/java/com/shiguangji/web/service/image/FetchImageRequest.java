package com.shiguangji.web.service.image;

/**
 * 转存外链图片的请求体（`POST /common/upload/fetch-image`）
 *
 * @author shiguangji
 */
public record FetchImageRequest(String url)
{
}
