package com.shiguangji.file.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import com.shiguangji.common.constant.Constants;
import com.shiguangji.file.storage.FileStorageService;

/**
 * /profile/** 读取代理
 *
 * 仅在 sgj.storage.type=s3 时生效：控制器映射优先级高于静态资源映射，
 * 读取请求经策略接口从 S3 兼容存储取回内容；local 模式仍由静态资源映射直接读磁盘。
 *
 * @author shiguangji
 */
@RestController
@ConditionalOnProperty(name = "sgj.storage.type", havingValue = "s3")
public class ProfileResourceController
{
    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping(Constants.RESOURCE_PREFIX + "/**")
    public void getResource(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
        String uri = request.getRequestURI();
        int prefixIndex = uri.indexOf(Constants.RESOURCE_PREFIX + "/");
        if (prefixIndex < 0)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // 数据库中保存的 key 为未编码形式，这里需与存储时保持一致
        String objectKey = UriUtils.decode(uri.substring(prefixIndex + Constants.RESOURCE_PREFIX.length() + 1), StandardCharsets.UTF_8);

        String contentType = URLConnection.guessContentTypeFromName(objectKey);
        response.setContentType(contentType == null ? "application/octet-stream" : contentType);
        // 对象 key 含日期与序列号，内容不可变，可安全缓存
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400");

        try (InputStream in = fileStorageService.getContent(objectKey))
        {
            IOUtils.copy(in, response.getOutputStream());
        }
        catch (NoSuchFileException e)
        {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
