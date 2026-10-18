package io.github.biliblihuorong.responsiveimages.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UploadedImageTest {

    static final String BODY = """
        {"id":4,"key":"9491","name":"photo.jpg","ext":"jpg","mime":"image/jpeg","size":175318,"webp_size":24228,
         "width":2400,"height":1500,
         "links":{"url":"https://img.test/i/a.webp","original":"https://img.test/i/a.jpg",
                  "webp":"https://img.test/i/a.webp","thumbnail_url":"https://img.test/i/a_thumbs.webp"},
         "variants":[
           {"url":"https://img.test/i/a_w1600_v1.webp","width":1600,"height":1000,"bytes":1,"mime":"image/webp"},
           {"url":"https://img.test/i/a_w400_v1.webp","width":400,"height":250,"bytes":1,"mime":"image/webp"},
           {"url":"https://img.test/i/a_w800_v1.webp","width":800,"height":500,"bytes":1,"mime":"image/webp"}]}""";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesThePreferredLinkAndItsTypeAndSize() throws Exception {
        var image = UploadedImage.parse(mapper.readTree(BODY));
        assertThat(image.id()).isEqualTo(4);
        assertThat(image.permalink()).isEqualTo("https://img.test/i/a.webp");
        assertThat(image.mediaType()).isEqualTo("image/webp");
        assertThat(image.size()).isEqualTo(24228);
        assertThat(image.variants()).extracting("width").containsExactly(400, 800, 1600);
    }

    @Test
    void keepsTheOriginalTypeWhenTheLinkIsNotWebp() throws Exception {
        var body = BODY.replace("\"url\":\"https://img.test/i/a.webp\"", "\"url\":\"https://img.test/i/a.jpg\"");
        var image = UploadedImage.parse(mapper.readTree(body));
        assertThat(image.mediaType()).isEqualTo("image/jpeg");
        assertThat(image.size()).isEqualTo(175318);
    }

    @Test
    void mapsHaloThumbnailSizesToTheSmallestWideEnoughVariant() throws Exception {
        var image = UploadedImage.parse(mapper.readTree(BODY));
        assertThat(image.thumbnails()).containsExactly(
            org.assertj.core.api.Assertions.entry("S", "https://img.test/i/a_w400_v1.webp"),
            org.assertj.core.api.Assertions.entry("M", "https://img.test/i/a_w800_v1.webp"),
            org.assertj.core.api.Assertions.entry("L", "https://img.test/i/a_w1600_v1.webp"),
            org.assertj.core.api.Assertions.entry("XL", "https://img.test/i/a_w1600_v1.webp"));
    }

    @Test
    void usesThePermalinkForSizesWiderThanTheImage() throws Exception {
        var body = BODY.replace(
            "{\"url\":\"https://img.test/i/a_w1600_v1.webp\",\"width\":1600,\"height\":1000,\"bytes\":1,\"mime\":\"image/webp\"},",
            "");
        var thumbnails = UploadedImage.parse(mapper.readTree(body)).thumbnails();
        assertThat(thumbnails.get("M")).isEqualTo("https://img.test/i/a_w800_v1.webp");
        assertThat(thumbnails.get("L")).isEqualTo("https://img.test/i/a.webp");
        assertThat(thumbnails.get("XL")).isEqualTo("https://img.test/i/a.webp");
    }

    @Test
    void hasNoThumbnailsWithoutVariants() throws Exception {
        var body = mapper.readTree(BODY);
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).putArray("variants");
        assertThat(UploadedImage.parse(body).thumbnails()).isEmpty();
    }

    @Test
    void rejectsAResponseWithoutIdOrLink() throws Exception {
        assertThatThrownBy(() -> UploadedImage.parse(mapper.readTree("{\"links\":{}}")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
