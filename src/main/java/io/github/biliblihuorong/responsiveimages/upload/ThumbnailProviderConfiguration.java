package io.github.biliblihuorong.responsiveimages.upload;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * 只在 Halo 还提供 ThumbnailProvider 扩展点时注册 {@link ImgNestThumbnailProvider}，
 * 否则加载这个类会失败，导致整个插件无法启动。
 */
@Configuration(proxyBeanMethods = false)
@Conditional(ThumbnailProviderConfiguration.ThumbnailProviderPresent.class)
public class ThumbnailProviderConfiguration {

    static final String THUMBNAIL_PROVIDER = "run.halo.app.core.attachment.ThumbnailProvider";

    @Bean
    ImgNestThumbnailProvider imgNestThumbnailProvider(ReactiveExtensionClient client) {
        return new ImgNestThumbnailProvider(client);
    }

    static class ThumbnailProviderPresent implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return ClassUtils.isPresent(THUMBNAIL_PROVIDER, context.getClassLoader());
        }
    }
}
