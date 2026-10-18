package io.github.biliblihuorong.responsiveimages.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;

class ImgNestAttachmentHandlerTest {

    private final ImgNestClient client = mock(ImgNestClient.class);
    private final ImgNestAttachmentHandler handler = new ImgNestAttachmentHandler(client);

    private static Policy policy(String template) {
        var policy = new Policy();
        policy.setMetadata(new Metadata());
        policy.getMetadata().setName("p");
        var spec = new Policy.PolicySpec();
        spec.setTemplateName(template);
        policy.setSpec(spec);
        return policy;
    }

    private static ConfigMap configMap(String json) {
        var configMap = new ConfigMap();
        configMap.setMetadata(new Metadata());
        configMap.setData(Map.of("default", json));
        return configMap;
    }

    private static final ConfigMap CONFIG = configMap(
        "{\"endpoint\":\"https://img.test/\",\"token\":\"1|secret\",\"policyId\":2}");

    private static FilePart file(String name, MediaType declared) {
        var file = mock(FilePart.class);
        var headers = new HttpHeaders();
        if (declared != null) {
            headers.setContentType(declared);
        }
        when(file.filename()).thenReturn(name);
        when(file.headers()).thenReturn(headers);
        when(file.content()).thenReturn(Flux.empty());
        return file;
    }

    private static AttachmentHandler.UploadContext upload(FilePart file, Policy policy, ConfigMap configMap) {
        return new AttachmentHandler.UploadContext() {
            public FilePart file() {
                return file;
            }

            public Policy policy() {
                return policy;
            }

            public ConfigMap configMap() {
                return configMap;
            }
        };
    }

    private static AttachmentHandler.DeleteContext delete(Attachment attachment, Policy policy, ConfigMap configMap) {
        return new AttachmentHandler.DeleteContext() {
            public Attachment attachment() {
                return attachment;
            }

            public Policy policy() {
                return policy;
            }

            public ConfigMap configMap() {
                return configMap;
            }
        };
    }

    private static UploadedImage image() throws Exception {
        return UploadedImage.parse(new ObjectMapper().readTree(UploadedImageTest.BODY));
    }

    private static Attachment uploaded(String instance) throws Exception {
        var attachment = ImgNestAttachmentHandler.buildAttachment(image(), "photo.jpg", instance);
        attachment.getMetadata().setName("att");
        return attachment;
    }

    @Test
    void ignoresOtherPolicies() {
        var context = upload(file("a.png", null), policy("local"), CONFIG);
        assertThat(handler.upload(context).blockOptional()).isEmpty();
        assertThat(handler.getPermalink(new Attachment(), policy("s3os"), CONFIG).blockOptional()).isEmpty();
        assertThat(handler.delete(delete(new Attachment(), policy(null), CONFIG)).blockOptional()).isEmpty();
        verify(client, never()).upload(any(), any(), any(), any());
    }

    @Test
    void uploadsImagesAndBuildsTheAttachment() throws Exception {
        when(client.upload(any(), any(), any(), any())).thenReturn(Mono.just(image()));
        var attachment = handler.upload(upload(file("photo.jpg", null), policy(ImgNestAttachmentHandler.TEMPLATE),
            CONFIG)).block();

        verify(client).upload(any(), any(), any(), any());
        assertThat(attachment.getMetadata().getGenerateName()).isNotBlank();
        assertThat(attachment.getMetadata().getAnnotations())
            .containsEntry(ImgNestAttachmentHandler.IMAGE_ID, "4")
            .containsEntry(ImgNestAttachmentHandler.INSTANCE_ID, "img.test")
            .containsEntry(ImgNestAttachmentHandler.PERMALINK, "https://img.test/i/a.webp");
        assertThat(attachment.getSpec().getDisplayName()).isEqualTo("photo.jpg");
        assertThat(attachment.getSpec().getMediaType()).isEqualTo("image/webp");
        assertThat(attachment.getSpec().getSize()).isEqualTo(24228);
        assertThat(attachment.getStatus().getPermalink()).isEqualTo("https://img.test/i/a.webp");
        assertThat(attachment.getStatus().getThumbnails()).containsEntry("S", "https://img.test/i/a_w400_v1.webp");
    }

    @Test
    void acceptsADeclaredImageTypeWhenTheNameHasNoExtension() {
        when(client.upload(any(), any(), any(), any())).thenReturn(Mono.empty());
        handler.upload(upload(file("blob", MediaType.IMAGE_JPEG), policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG))
            .block();
        verify(client).upload(any(), any(), any(), any());
    }

    @Test
    void rejectsNonImages() {
        var context = upload(file("notes.pdf", MediaType.APPLICATION_PDF), policy(ImgNestAttachmentHandler.TEMPLATE),
            CONFIG);
        assertThatThrownBy(() -> handler.upload(context).block())
            .isInstanceOf(ServerWebInputException.class)
            .hasMessageContaining("只接受图片");
        verify(client, never()).upload(any(), any(), any(), any());
    }

    @Test
    void rejectsAPolicyWithoutToken() {
        var context = upload(file("a.png", null), policy(ImgNestAttachmentHandler.TEMPLATE),
            configMap("{\"endpoint\":\"https://img.test\"}"));
        assertThatThrownBy(() -> handler.upload(context).block()).hasMessageContaining("Token");
    }

    @Test
    void turnsImgNestErrorsIntoReadableMessages() {
        when(client.upload(any(), any(), any(), any()))
            .thenReturn(Mono.error(new ImgNestException(413, 10005, "file too large")));
        var context = upload(file("a.png", null), policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG);
        assertThatThrownBy(() -> handler.upload(context).block())
            .isInstanceOf(ServerWebInputException.class)
            .hasMessageContaining("HTTP 413")
            .hasMessageNotContaining("secret");
    }

    @Test
    void returnsThePermalinkAndThumbnails() throws Exception {
        var attachment = uploaded("img.test");
        var policy = policy(ImgNestAttachmentHandler.TEMPLATE);
        assertThat(handler.getPermalink(attachment, policy, CONFIG).block())
            .isEqualTo(URI.create("https://img.test/i/a.webp"));
        assertThat(handler.getSharedURL(attachment, policy, CONFIG, null).block())
            .isEqualTo(URI.create("https://img.test/i/a.webp"));
        assertThat(handler.getThumbnailLinks(attachment, policy, CONFIG).block())
            .containsEntry(ThumbnailSize.M, URI.create("https://img.test/i/a_w800_v1.webp"))
            .containsEntry(ThumbnailSize.XL, URI.create("https://img.test/i/a_w1600_v1.webp"));
    }

    @Test
    void deletesTheImageFromTheSameInstance() throws Exception {
        when(client.delete(any(), anyLong())).thenReturn(Mono.empty());
        var attachment = uploaded("img.test");
        assertThat(handler.delete(delete(attachment, policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG)).block())
            .isSameAs(attachment);
        verify(client).delete(any(), org.mockito.ArgumentMatchers.eq(4L));
    }

    @Test
    void leavesImagesOfAnotherInstanceAlone() throws Exception {
        var attachment = uploaded("old.test");
        assertThat(handler.delete(delete(attachment, policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG)).block())
            .isSameAs(attachment);
        verify(client, never()).delete(any(), anyLong());
    }

    @Test
    void canKeepImagesWhenSyncDeleteIsOff() throws Exception {
        var config = configMap("{\"endpoint\":\"https://img.test\",\"token\":\"t\",\"deleteRemote\":false}");
        handler.delete(delete(uploaded("img.test"), policy(ImgNestAttachmentHandler.TEMPLATE), config)).block();
        verify(client, never()).delete(any(), anyLong());
    }

    @Test
    void letsHaloRemoveTheRecordWhenImgNestRefusesForGood() throws Exception {
        when(client.delete(any(), anyLong())).thenReturn(Mono.error(new ImgNestException(401, 20001, "")));
        var attachment = uploaded("img.test");
        assertThat(handler.delete(delete(attachment, policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG)).block())
            .isSameAs(attachment);
    }

    @Test
    void failsTheDeleteOnTemporaryErrorsSoHaloRetries() throws Exception {
        when(client.delete(any(), anyLong())).thenReturn(Mono.error(new ImgNestException(0, 0, "连不上 ImgNest")));
        var context = delete(uploaded("img.test"), policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG);
        assertThatThrownBy(() -> handler.delete(context).block()).isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void skipsAttachmentsWithoutAnImageId() {
        var attachment = new Attachment();
        attachment.setMetadata(new Metadata());
        attachment.getMetadata().setName("att");
        attachment.getMetadata().setAnnotations(new HashMap<>());
        handler.delete(delete(attachment, policy(ImgNestAttachmentHandler.TEMPLATE), CONFIG)).block();
        verify(client, never()).delete(any(), anyLong());
    }

    @Test
    void readsFormValuesIncludingEmptyNumbersAndUnknownFields() {
        var config = ImgNestAttachmentHandler.config(configMap(
            "{\"endpoint\":\"https://img.test\",\"token\":\"t\",\"policyId\":\"\",\"albumId\":5,"
                + "\"visibility\":\"public\",\"extra\":1}"));
        assertThat(config.getPolicyId()).isNull();
        assertThat(config.getAlbumId()).isEqualTo(5L);
        assertThat(config.publicFlag()).isTrue();
        assertThat(config.shouldDeleteRemote()).isTrue();
        assertThat(config.toString()).doesNotContain("token=t");
    }

    @Test
    void derivesTheInstanceIdFromTheEndpoint() {
        var config = new ImgNestPolicyConfig();
        config.setEndpoint("https://IMG.test:8443/sub/");
        assertThat(config.effectiveInstanceId()).isEqualTo("img.test:8443/sub");
        config.setInstanceId("mine");
        assertThat(config.effectiveInstanceId()).isEqualTo("mine");
        config.setEndpoint("ftp://img.test");
        assertThat(config.baseUri()).isNull();
    }
}
