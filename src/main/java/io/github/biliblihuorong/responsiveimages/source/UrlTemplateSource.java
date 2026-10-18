package io.github.biliblihuorong.responsiveimages.source;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 按 URL 规则生成衍生版本，适合能实时处理图片的 CDN 或图床（OSS、COS、七牛、imgproxy 等）。
 * 不发请求，也不知道图片真实尺寸，所以只给出宽度。
 */
public final class UrlTemplateSource implements VariantSource {

    private final HostMatcher hosts;
    private final List<Integer> widths;
    private final String template;

    public UrlTemplateSource(HostMatcher hosts, List<Integer> widths, String template) {
        this.hosts = hosts;
        this.widths = widths.stream().filter(w -> w > 0).distinct().sorted().toList();
        this.template = template;
    }

    public static List<Integer> parseWidths(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("[\\s,]+"))
            .filter(s -> s.matches("\\d{1,5}"))
            .map(Integer::parseInt)
            .toList();
    }

    @Override
    public boolean supports(URI uri) {
        return template != null && template.contains("{width}") && !widths.isEmpty() && hosts.matches(uri);
    }

    @Override
    public Mono<Map<String, List<Variant>>> resolve(List<String> urls) {
        Map<String, List<Variant>> result = new LinkedHashMap<>();
        for (var url : urls) {
            result.put(url, widths.stream().map(w -> new Variant(build(url, w), w, null)).toList());
        }
        return Mono.just(result);
    }

    String build(String url, int width) {
        var sep = url.contains("?") ? "&" : "?";
        return template.replace("{url}", url).replace("{sep}", sep).replace("{width}", Integer.toString(width));
    }

    @Override
    public String describe() {
        return "template:" + template + ":" + widths;
    }
}
