package io.github.biliblihuorong.responsiveimages.upload;

import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.infra.utils.JsonUtils;

/**
 * 「ImgNest 图床」存储策略：Halo 附件库上传的图片直接走 ImgNest /api/v2/upload，
 * 删除附件时把图片移进 ImgNest 回收站。只处理模板为 {@link #TEMPLATE} 的存储策略。
 */
@Slf4j
@Component
public class ImgNestAttachmentHandler implements AttachmentHandler {

    /** 与 policy-template-imgnest.yaml 里 PolicyTemplate 的名字一致。 */
    public static final String TEMPLATE = "responsive-images-imgnest";

    static final String ANNOTATION_PREFIX = "responsive-images.biliblihuorong.github.io/";
    static final String IMAGE_ID = ANNOTATION_PREFIX + "imgnest-id";
    static final String IMAGE_KEY = ANNOTATION_PREFIX + "imgnest-key";
    static final String INSTANCE_ID = ANNOTATION_PREFIX + "imgnest-instance";
    static final String PERMALINK = ANNOTATION_PREFIX + "imgnest-url";
    static final String THUMBNAILS = ANNOTATION_PREFIX + "imgnest-thumbnails";

    private final ImgNestClient client;

    public ImgNestAttachmentHandler() {
        this(new ImgNestClient(WebClient.builder().build()));
    }

    ImgNestAttachmentHandler(ImgNestClient client) {
        this.client = client;
    }

    @Override
    public Mono<Attachment> upload(UploadContext context) {
        if (!handles(context.policy())) {
            return Mono.empty();
        }
        return Mono.defer(() -> {
            var config = config(context.configMap());
            if (config.baseUri() == null) {
                return Mono.error(new ServerWebInputException("ImgNest 存储策略的站点地址无效，请检查策略设置"));
            }
            if (!StringUtils.hasText(config.getToken())) {
                return Mono.error(new ServerWebInputException("ImgNest 存储策略还没有填写 API Token"));
            }
            var file = context.file();
            var mediaType = imageType(file);
            if (mediaType == null) {
                return Mono.error(new ServerWebInputException(
                    "ImgNest 只接受图片，「" + file.filename() + "」请换用其他存储策略上传"));
            }
            return client.upload(config, file.content(), file.filename(), mediaType)
                .doOnNext(image -> log.info("Uploaded attachment {} to ImgNest as image {}", file.filename(),
                    image.id()))
                .map(image -> buildAttachment(image, file.filename(), config.effectiveInstanceId()))
                .onErrorMap(ImgNestException.class, e -> new ServerWebInputException(e.describe()));
        });
    }

    @Override
    public Mono<Attachment> delete(DeleteContext context) {
        if (!handles(context.policy())) {
            return Mono.empty();
        }
        var attachment = context.attachment();
        return Mono.defer(() -> {
            var config = config(context.configMap());
            var name = attachment.getMetadata().getName();
            if (!config.shouldDeleteRemote()) {
                return Mono.just(attachment);
            }
            var id = imageId(attachment);
            if (id.isEmpty()) {
                log.warn("Attachment {} has no ImgNest image id, skip deleting it from ImgNest", name);
                return Mono.just(attachment);
            }
            var instance = annotation(attachment, INSTANCE_ID).orElse("");
            if (!instance.equals(config.effectiveInstanceId())) {
                log.warn("Attachment {} belongs to another ImgNest instance, skip deleting it from ImgNest", name);
                return Mono.just(attachment);
            }
            return client.delete(config, id.get())
                .doOnSuccess(v -> log.info("Moved ImgNest image {} of attachment {} to the recycle bin", id.get(),
                    name))
                .thenReturn(attachment)
                .onErrorResume(ImgNestException.class, e -> {
                    if (e.permanent()) {
                        // 鉴权失败、接口关闭这类错误重试也没用，让 Halo 删掉附件记录，图片留在图床里。
                        log.warn("Could not delete ImgNest image {} of attachment {}, keeping it there: {}", id.get(),
                            name, e.describe());
                        return Mono.just(attachment);
                    }
                    return Mono.error(new ServerWebInputException(e.describe()));
                });
        });
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap, Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!handles(policy)) {
            return Mono.empty();
        }
        return Mono.justOrEmpty(annotation(attachment, PERMALINK)
                .or(() -> Optional.ofNullable(attachment.getStatus()).map(Attachment.AttachmentStatus::getPermalink))
                .filter(StringUtils::hasText))
            .map(URI::create);
    }

    /**
     * Halo 2.22 起由附件协调器调用，用来填附件的缩略图链接；2.20、2.21 的接口里没有这个方法，
     * 那时由上传时写进 status 的缩略图生效。所以这里不加 {@code @Override}。
     */
    public Mono<Map<ThumbnailSize, URI>> getThumbnailLinks(Attachment attachment, Policy policy,
                                                         ConfigMap configMap) {
        if (!handles(policy)) {
            return Mono.empty();
        }
        Map<ThumbnailSize, URI> result = new LinkedHashMap<>();
        var thumbnails = thumbnails(attachment);
        for (var size : ThumbnailSize.values()) {
            var url = thumbnails.get(size.name());
            if (StringUtils.hasText(url)) {
                result.put(size, URI.create(url));
            }
        }
        return Mono.just(result);
    }

    static boolean handles(@Nullable Policy policy) {
        return policy != null && policy.getSpec() != null && TEMPLATE.equals(policy.getSpec().getTemplateName());
    }

    static ImgNestPolicyConfig config(ConfigMap configMap) {
        var data = configMap == null || configMap.getData() == null ? null
            : configMap.getData().get(ImgNestPolicyConfig.GROUP);
        if (!StringUtils.hasText(data)) {
            return new ImgNestPolicyConfig();
        }
        return JsonUtils.jsonToObject(data, ImgNestPolicyConfig.class);
    }

    /** 文件名或请求头声明为图片时返回其类型。真正的格式由 ImgNest 读文件头判断。 */
    @Nullable
    static MediaType imageType(FilePart file) {
        var byName = MediaTypeFactory.getMediaType(file.filename()).filter(t -> "image".equals(t.getType()));
        if (byName.isPresent()) {
            return byName.get();
        }
        var declared = file.headers().getContentType();
        return declared != null && "image".equals(declared.getType()) ? declared : null;
    }

    static Attachment buildAttachment(UploadedImage image, String filename, String instanceId) {
        var metadata = new Metadata();
        metadata.setGenerateName(UUID.randomUUID().toString());
        var annotations = new HashMap<String, String>();
        annotations.put(IMAGE_ID, String.valueOf(image.id()));
        annotations.put(IMAGE_KEY, image.key());
        annotations.put(INSTANCE_ID, instanceId);
        annotations.put(PERMALINK, image.permalink());
        var thumbnails = image.thumbnails();
        if (!thumbnails.isEmpty()) {
            annotations.put(THUMBNAILS, JsonUtils.objectToJson(thumbnails));
        }
        metadata.setAnnotations(annotations);

        var spec = new Attachment.AttachmentSpec();
        spec.setDisplayName(StringUtils.hasText(filename) ? filename : image.name());
        if (StringUtils.hasText(image.mediaType())) {
            spec.setMediaType(image.mediaType());
        }
        spec.setSize(image.size());

        var status = new Attachment.AttachmentStatus();
        status.setPermalink(image.permalink());
        if (!thumbnails.isEmpty()) {
            status.setThumbnails(thumbnails);
        }

        var attachment = new Attachment();
        attachment.setMetadata(metadata);
        attachment.setSpec(spec);
        attachment.setStatus(status);
        return attachment;
    }

    static Optional<Long> imageId(Attachment attachment) {
        return annotation(attachment, IMAGE_ID).flatMap(text -> {
            try {
                var id = Long.parseLong(text);
                return id > 0 ? Optional.of(id) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    static Map<String, String> thumbnails(Attachment attachment) {
        return annotation(attachment, THUMBNAILS)
            .map(json -> {
                try {
                    return JsonUtils.jsonToObject(json, new TypeReference<Map<String, String>>() {
                    });
                } catch (RuntimeException e) {
                    return Map.<String, String>of();
                }
            })
            .orElse(Map.of());
    }

    private static Optional<String> annotation(Attachment attachment, String key) {
        var annotations = attachment.getMetadata() == null ? null : attachment.getMetadata().getAnnotations();
        return Optional.ofNullable(annotations == null ? null : annotations.get(key)).filter(StringUtils::hasText);
    }
}
