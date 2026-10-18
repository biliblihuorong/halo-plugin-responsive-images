package io.github.biliblihuorong.responsiveimages.upload;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.biliblihuorong.responsiveimages.source.Variant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import run.halo.app.core.attachment.ThumbnailSize;

/**
 * /api/v2/upload 返回的 ImageView 里本插件用到的部分。
 *
 * @param permalink 附件的固定链接，取 links.url（图床认为最合适的版本，通常是 WebP）
 * @param mediaType permalink 对应文件的类型
 * @param size permalink 对应文件的字节数
 */
public record UploadedImage(long id, String key, String name, String permalink, String mediaType, long size,
                            int width, int height, List<Variant> variants) {

    static UploadedImage parse(JsonNode data) {
        var links = data.path("links");
        var url = links.path("url").asText("");
        if (data.path("id").asLong(0) <= 0 || url.isEmpty()) {
            throw new IllegalArgumentException("upload response has no image id or link");
        }
        var webp = links.path("webp").asText("");
        var isWebp = !webp.isEmpty() && webp.equals(url);
        var mediaType = isWebp ? "image/webp" : data.path("mime").asText("");
        var size = isWebp ? data.path("webp_size").asLong(0) : data.path("size").asLong(0);
        List<Variant> variants = new ArrayList<>();
        for (var v : data.path("variants")) {
            var vurl = v.path("url").asText("");
            var width = v.path("width").asInt(0);
            if (!vurl.isEmpty() && width > 0) {
                var height = v.path("height").asInt(0);
                variants.add(new Variant(vurl, width, height > 0 ? height : null));
            }
        }
        variants.sort(Comparator.comparingInt(Variant::width));
        return new UploadedImage(data.path("id").asLong(), data.path("key").asText(""), data.path("name").asText(""),
            url, mediaType, size, data.path("width").asInt(0), data.path("height").asInt(0), List.copyOf(variants));
    }

    /**
     * 按 Halo 的缩略图尺寸挑选版本：取宽度不小于目标的最小版本，没有就用 permalink
     * （图床不放大，原图比目标窄时不会生成那一档）。没有任何响应式版本时返回空，Halo 会直接用原链接。
     */
    public Map<String, String> thumbnails() {
        Map<String, String> result = new LinkedHashMap<>();
        if (variants.isEmpty()) {
            return result;
        }
        for (var size : ThumbnailSize.values()) {
            var url = variants.stream()
                .filter(v -> v.width() >= size.getWidth())
                .findFirst()
                .map(Variant::url)
                .orElse(permalink);
            result.put(size.name(), url);
        }
        return result;
    }
}
