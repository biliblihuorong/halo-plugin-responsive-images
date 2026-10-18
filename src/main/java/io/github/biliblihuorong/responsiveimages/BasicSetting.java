package io.github.biliblihuorong.responsiveimages;

import java.util.Arrays;
import java.util.List;
import lombok.Data;

/**
 * 「基本设置」分组，对应 settings.yaml 里的 basic。
 */
@Data
public class BasicSetting {

    public static final String GROUP = "basic";

    /** 处理文章和独立页面正文里的图片。 */
    private boolean enableContent = true;

    /** 处理主题模板里的图片（封面、头像等）。 */
    private boolean enableTemplate = true;

    /** 图片没有 sizes 属性时补上的默认值。 */
    private String sizes = "(max-width: 800px) 100vw, 800px";

    /** 图片没有宽高属性时写入最大版本的宽高，需要主题给图片设置 height: auto。 */
    private boolean writeDimensions = false;

    /** 衍生图加载失败时撤下 srcset，回到原来的图片地址。 */
    private boolean fallbackOnError = true;

    /** 懒加载主题存放真实地址的属性，每行一个，按顺序优先于 src。 */
    private String lazyAttributes = "data-src\ndata-lazy-src\ndata-original";

    /** 真实地址来自懒加载属性时，把 srcset 写到哪个属性；lazysizes 等库用 data-srcset。 */
    private String lazySrcsetAttribute = "srcset";

    /** 单次查询的超时时间，超时后保持原图。 */
    private int timeoutMillis = 800;

    /** 查询结果的缓存时间。 */
    private int cacheMinutes = 30;

    public List<String> lazyAttributeList() {
        if (lazyAttributes == null) {
            return List.of();
        }
        return Arrays.stream(lazyAttributes.split("[\\r\\n,]+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    public String effectiveLazySrcsetAttribute() {
        return lazySrcsetAttribute == null || lazySrcsetAttribute.isBlank()
            ? "srcset" : lazySrcsetAttribute.trim();
    }
}
