package io.github.biliblihuorong.responsiveimages.source;

import java.net.URI;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 衍生版本来源。
 */
public interface VariantSource {

    /** 这个来源是否负责该图片地址。 */
    boolean supports(URI uri);

    /**
     * 批量查询。返回的 Map 以请求里的地址为键，值按宽度升序；没有衍生版本的地址对应空列表或不出现。
     */
    Mono<Map<String, List<Variant>>> resolve(List<String> urls);

    /** 用于判断配置是否变化、写日志，不能包含凭据。 */
    String describe();
}
