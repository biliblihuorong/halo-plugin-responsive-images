package io.github.biliblihuorong.responsiveimages.upload;

import lombok.Getter;

/**
 * ImgNest 返回错误或连不上。status 为 0 表示请求没有得到 HTTP 响应。
 */
@Getter
public class ImgNestException extends RuntimeException {

    private final int status;
    private final int code;

    public ImgNestException(int status, int code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /** 重试也不会好的错误：鉴权失败、接口关闭、参数或文件被拒绝。 */
    public boolean permanent() {
        return status >= 400 && status < 500 && status != 408 && status != 429;
    }

    /** 给 Halo 后台看的说明，不含 Token。 */
    public String describe() {
        if (status == 0) {
            return getMessage();
        }
        var hint = switch (status) {
            case 401 -> "Token 无效或已撤销，请在 ImgNest「Token 与安全」里重新创建";
            case 403 -> code == 20003 ? "ImgNest 关闭了 API，请在后台设置里开启" : "这个 Token 没有权限";
            case 413 -> "文件超过了 ImgNest 的大小限制";
            case 429 -> "请求太频繁或容量不足，请稍后再试";
            default -> null;
        };
        var detail = getMessage() == null || getMessage().isBlank() ? "" : "：" + getMessage();
        return "ImgNest 返回 HTTP " + status + (code > 0 ? "（" + code + "）" : "") + detail
            + (hint == null ? "" : "。" + hint);
    }
}
