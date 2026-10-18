package io.github.biliblihuorong.responsiveimages;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 「图片来源」分组，对应 settings.yaml 里的 sources。
 */
@Data
public class SourcesSetting {

    public static final String GROUP = "sources";

    private List<SourceConfig> sources = new ArrayList<>();

    @Data
    public static class SourceConfig {
        /** lookup：查询接口；template：URL 规则。 */
        private String type = "lookup";
        /** 匹配的图片域名，每行一个，可带路径前缀，支持 *.example.com。 */
        private String hosts;
        /** 查询接口地址，例如 https://img.example.com/api/variants。 */
        private String endpoint;
        /** URL 规则生成的宽度，逗号分隔。 */
        private String widths = "400,800,1600";
        /** URL 规则模板，可用 {url}、{sep}、{width}。 */
        private String template;
    }
}
