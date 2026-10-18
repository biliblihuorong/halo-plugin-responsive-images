package io.github.biliblihuorong.responsiveimages.source;

/**
 * 一张图片的一个宽度版本。高度未知时为 null。
 */
public record Variant(String url, int width, Integer height) {
}
