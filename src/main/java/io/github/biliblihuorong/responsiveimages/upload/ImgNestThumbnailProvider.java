package io.github.biliblihuorong.responsiveimages.upload;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.core.attachment.ThumbnailProvider;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.QueryFactory;

/**
 * Halo 2.20、2.21 的附件协调器会用缩略图服务重新生成缩略图并覆盖上传时写入的结果，
 * 这里把本插件上传的附件对应到 ImgNest 已经生成的响应式版本。
 * 2.22 起改由 {@link ImgNestAttachmentHandler#getThumbnailLinks} 提供；
 * 更新的 Halo 去掉了这个扩展点，所以只在类存在时注册（见 {@link ThumbnailProviderConfiguration}）。
 */
public class ImgNestThumbnailProvider implements ThumbnailProvider {

    private final ReactiveExtensionClient client;

    public ImgNestThumbnailProvider(ReactiveExtensionClient client) {
        this.client = client;
    }

    @Override
    public Mono<URI> generate(ThumbnailContext context) {
        return thumbnails(context.getImageUrl())
            .mapNotNull(map -> map.get(context.getSize().name()))
            .map(URI::create);
    }

    @Override
    public Mono<Void> delete(URL imageUrl) {
        // 衍生图属于 ImgNest，随附件删除时由存储策略移进回收站。
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> supports(ThumbnailContext context) {
        return thumbnails(context.getImageUrl()).hasElement();
    }

    private Mono<Map<String, String>> thumbnails(URL imageUrl) {
        if (imageUrl == null) {
            return Mono.empty();
        }
        var options = ListOptions.builder()
            .fieldQuery(QueryFactory.equal("status.permalink", imageUrl.toString()))
            .build();
        return client.listAll(Attachment.class, options, Sort.unsorted())
            .map(ImgNestAttachmentHandler::thumbnails)
            .filter(map -> !map.isEmpty())
            .next();
    }
}
