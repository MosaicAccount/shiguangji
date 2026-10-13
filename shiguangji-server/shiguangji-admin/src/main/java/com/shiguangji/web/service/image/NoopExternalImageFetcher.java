package com.shiguangji.web.service.image;

import com.shiguangji.common.exception.ServiceException;

/**
 * 抓取器占位实现：真实现（issue #68 交接件）落地前保证应用能启动、既有测试不炸。
 *
 * <p>本类不注册 @Component，由 {@link ExternalImageFetchConfig} 以
 * {@code @ConditionalOnMissingBean(ExternalImageFetcher.class)} 兜底装配——
 * 普通 @Component 的扫描注册先于 @Configuration 的 @Bean 条件求值，
 * 因此真实现只要标 @Component 就会取代本占位（设计 结论 70）。</p>
 *
 * @author shiguangji
 */
public class NoopExternalImageFetcher implements ExternalImageFetcher
{
    @Override
    public FetchedImage fetch(String imageUrl)
    {
        throw new ServiceException("外链图片抓取未启用（HttpExternalImageFetcher 尚未实现，见 docs/design/external-images-design.md §4）");
    }
}
