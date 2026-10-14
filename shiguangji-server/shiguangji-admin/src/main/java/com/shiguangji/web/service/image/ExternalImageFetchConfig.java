package com.shiguangji.web.service.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 抓取器装配：默认给 {@link NoopExternalImageFetcher} 占位，存在任何真实现（@Component）时退位。
 *
 * @author shiguangji
 */
@Configuration
public class ExternalImageFetchConfig
{
    @Bean
    @ConditionalOnMissingBean(ExternalImageFetcher.class)
    public ExternalImageFetcher noopExternalImageFetcher()
    {
        return new NoopExternalImageFetcher();
    }
}
