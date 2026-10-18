package io.github.biliblihuorong.responsiveimages.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URL;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import run.halo.app.core.attachment.ThumbnailProvider.ThumbnailContext;
import run.halo.app.core.attachment.ThumbnailSize;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.ReactiveExtensionClient;

class ImgNestThumbnailProviderTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ImgNestThumbnailProvider provider = new ImgNestThumbnailProvider(client);

    private static ThumbnailContext context(ThumbnailSize size) throws Exception {
        return ThumbnailContext.builder().imageUrl(new URL("https://img.test/i/a.webp")).size(size).build();
    }

    @Test
    void servesVariantsOfImgNestAttachments() throws Exception {
        var image = UploadedImage.parse(new ObjectMapper().readTree(UploadedImageTest.BODY));
        var attachment = ImgNestAttachmentHandler.buildAttachment(image, "a.jpg", "img.test");
        when(client.listAll(eq(Attachment.class), any(), any())).thenReturn(Flux.just(attachment));

        assertThat(provider.supports(context(ThumbnailSize.S)).block()).isTrue();
        assertThat(provider.generate(context(ThumbnailSize.M)).block())
            .isEqualTo(URI.create("https://img.test/i/a_w800_v1.webp"));
    }

    @Test
    void ignoresOtherImages() throws Exception {
        var other = new Attachment();
        other.setMetadata(new run.halo.app.extension.Metadata());
        when(client.listAll(eq(Attachment.class), any(), any())).thenReturn(Flux.just(other), Flux.empty());

        assertThat(provider.supports(context(ThumbnailSize.S)).block()).isFalse();
        assertThat(provider.supports(context(ThumbnailSize.S)).block()).isFalse();
    }
}
