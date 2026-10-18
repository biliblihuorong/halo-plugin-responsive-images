package io.github.biliblihuorong.responsiveimages.source;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 按「主机 + 可选路径前缀」匹配图片地址，忽略协议、查询串和片段。
 * 每行一条：img.example.com、img.example.com/blog/、*.example.com。
 */
public final class HostMatcher {

    private record Rule(String host, boolean wildcard, String pathPrefix) {
    }

    private final List<Rule> rules;

    private HostMatcher(List<Rule> rules) {
        this.rules = rules;
    }

    public static HostMatcher parse(String text) {
        if (text == null) {
            return new HostMatcher(List.of());
        }
        var rules = Arrays.stream(text.split("[\\r\\n,]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(HostMatcher::toRule)
            .toList();
        return new HostMatcher(rules);
    }

    private static Rule toRule(String line) {
        var s = line.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "").replaceFirst("^//", "");
        var slash = s.indexOf('/');
        var host = (slash < 0 ? s : s.substring(0, slash)).toLowerCase(Locale.ROOT);
        var path = slash < 0 ? "/" : s.substring(slash);
        var wildcard = host.startsWith("*.");
        if (wildcard) {
            host = host.substring(1);
        }
        return new Rule(host, wildcard, path);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public boolean matches(URI uri) {
        if (uri == null || uri.getRawAuthority() == null) {
            return false;
        }
        var authority = uri.getRawAuthority().toLowerCase(Locale.ROOT);
        var path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        for (var rule : rules) {
            var hostOk = rule.wildcard() ? authority.endsWith(rule.host()) : authority.equals(rule.host());
            if (hostOk && path.startsWith(rule.pathPrefix())) {
                return true;
            }
        }
        return false;
    }
}
