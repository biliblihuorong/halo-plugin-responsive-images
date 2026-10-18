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
     * 写入 srcset 时，如果真实地址来自懒加载属性，用这个属性标明 srcset 对应的原图，
     * 让主题脚本能区分它和占位图自带的 srcset。
     */
    public static final String MARKER = "data-responsive-src";

    /**
     * 衍生图加载失败时撤下 srcset，浏览器会重新按 src 加载文章里原本写的图片地址。
     * 只执行一次，原图也失败时不会循环。懒加载图片的 src 是占位图，所以改用标记里的原图地址。
     */
    static final String FALLBACK = "this.onerror=null;this.removeAttribute('srcset')";
    static final String FALLBACK_LAZY = FALLBACK + ";this.src=this.getAttribute('" + MARKER + "')";

    /**
     * 已有真正的 srcset（包括 Halo 自带缩略图补上的）时返回空，不覆盖。
     * 以下几种视为没有，可以覆盖：主题用 thumbnail.gen 给外链图生成、每一项都是原图地址的 srcset；
     * 真实地址在懒加载属性里时，src 是占位图，此时 srcset 描述的是占位图。
     */
    public static Optional<Candidate> candidate(Function<String, String> attr, BasicSetting setting) {
        Candidate candidate = null;
        for (var name : setting.lazyAttributeList()) {
            var value = attr.apply(name);
            if (isImageUrl(value)) {
                candidate = new Candidate(value.trim(), true);
                break;
            }
        }
        var src = attr.apply("src");
        if (candidate == null && isImageUrl(src)) {
            candidate = new Candidate(src.trim(), false);
        }
        if (candidate == null) {
            return Optional.empty();
        }
        if (candidate.url().equals(attr.apply(MARKER))) {
            return Optional.empty();
        }
        var srcIsPlaceholder = candidate.fromLazyAttribute() && !candidate.url().equals(trim(src));
        for (var name : List.of("srcset", setting.effectiveLazySrcsetAttribute())) {
            var placeholderOwned = srcIsPlaceholder && name.equals("srcset");
            if (!placeholderOwned && !isPlaceholderSrcset(attr.apply(name), candidate.url(), src)) {
                return Optional.empty();
            }
        }
        return Optional.of(candidate);
    }

    /** 空，或者每一项都只是原图地址本身。 */
    static boolean isPlaceholderSrcset(String srcset, String url, String src) {
        if (!hasText(srcset)) {
            return true;
        }
        for (var item : srcset.split(",\\s+|,$")) {
            var parts = item.trim().split("\\s+");
            if (parts[0].isEmpty()) {
                continue;
            }
            if (!parts[0].equals(url) && !parts[0].equals(src == null ? null : src.trim())) {
                return false;
            }
        }
        return true;
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
        if (candidate.fromLazyAttribute() && srcsetName.equals("srcset")) {
            result.put(MARKER, candidate.url());
        }
        if (setting.isFallbackOnError() && srcsetName.equals("srcset") && !hasText(attr.apply("onerror"))) {
            result.put("onerror", candidate.fromLazyAttribute() ? FALLBACK_LAZY : FALLBACK);
        }
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
            .map(v -> v.url().replace(" ", "%20") + " " + v.width() + "w")
            .collect(Collectors.joining(", "));
    }

    private static boolean isImageUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        var v = value.trim().toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://") || v.startsWith("//");
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
