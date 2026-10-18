package io.github.biliblihuorong.responsiveimages.render;

import io.github.biliblihuorong.responsiveimages.BasicSetting;
import io.github.biliblihuorong.responsiveimages.source.Variant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 决定一个 img 标签用哪个地址查询、要补哪些属性。与 Jsoup、Thymeleaf 无关，正文和模板共用。
 */
public final class ImgRewriter {

    /** 选中的图片地址，以及它是否来自懒加载属性。 */
    public record Candidate(String url, boolean fromLazyAttribute) {
    }

    private ImgRewriter() {
    }

    /**
     * 已有 srcset（包括 Halo 自带缩略图补上的）时返回空，不覆盖。
     */
    public static Optional<Candidate> candidate(Function<String, String> attr, BasicSetting setting) {
        if (hasText(attr.apply("srcset")) || hasText(attr.apply(setting.effectiveLazySrcsetAttribute()))) {
            return Optional.empty();
        }
        for (var name : setting.lazyAttributeList()) {
            var value = attr.apply(name);
            if (isImageUrl(value)) {
                return Optional.of(new Candidate(value.trim(), true));
            }
        }
        var src = attr.apply("src");
        return isImageUrl(src) ? Optional.of(new Candidate(src.trim(), false)) : Optional.empty();
    }

    /**
     * 返回要设置的属性。只新增，不改已有的 src、sizes、width、height。
     */
    public static Map<String, String> attributes(Candidate candidate, List<Variant> variants,
        Function<String, String> attr, BasicSetting setting) {
        Map<String, String> result = new LinkedHashMap<>();
        if (variants.isEmpty()) {
            return result;
        }
        var srcsetName = candidate.fromLazyAttribute() ? setting.effectiveLazySrcsetAttribute() : "srcset";
        result.put(srcsetName, srcset(variants));
        if (!hasText(attr.apply("sizes")) && hasText(setting.getSizes())) {
            result.put("sizes", setting.getSizes().trim());
        }
        var largest = variants.get(variants.size() - 1);
        if (setting.isWriteDimensions() && largest.height() != null
            && !hasText(attr.apply("width")) && !hasText(attr.apply("height"))) {
            result.put("width", Integer.toString(largest.width()));
            result.put("height", Integer.toString(largest.height()));
        }
        return result;
    }

    static String srcset(List<Variant> variants) {
        return variants.stream()
            .map(v -> v.url().replace(" ", "%20").replace(",", "%2C") + " " + v.width() + "w")
            .collect(Collectors.joining(", "));
    }

    private static boolean isImageUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        var v = value.trim().toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://") || v.startsWith("//");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
