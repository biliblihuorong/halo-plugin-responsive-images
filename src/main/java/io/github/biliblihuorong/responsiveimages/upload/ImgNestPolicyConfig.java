package io.github.biliblihuorong.responsiveimages.upload;

import java.net.URI;
import lombok.Data;
import lombok.ToString;
import org.springframework.util.StringUtils;

/**
 * 「ImgNest 图床」存储策略的配置，对应 policy-template-imgnest.yaml 里 default 分组的表单。
 */
@Data
public class ImgNestPolicyConfig {

    /** 存储策略配置在 ConfigMap 里的键，即表单分组名。 */
    public static final String GROUP = "default";

    /** ImgNest 站点地址，例如 https://img.example.com。 */
    private String endpoint;

    /** ImgNest 的 API Token。不能出现在日志和错误信息里。 */
    @ToString.Exclude
    private String token;

    /** 可选，上传规则 ID（policy_id）。 */
    private Long policyId;

    /** 可选，相册 ID（album_id）。 */
    private Long albumId;

    /** 公开性：空为跟随图床默认，public 或 private。 */
    private String visibility;

    /** 可选，实例 ID。删除附件时只处理实例 ID 相同的图片，留空时用站点地址生成。 */
    private String instanceId;

    /** 在 Halo 删除附件时是否把图床里的图片移进回收站。 */
    private Boolean deleteRemote = true;

    /** 解析站点地址，只接受 http(s)，去掉末尾的斜杠。不合法时返回 null。 */
    public URI baseUri() {
        if (!StringUtils.hasText(endpoint)) {
            return null;
        }
        var text = endpoint.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            var uri = URI.create(text);
            var scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 实际使用的实例 ID：填了就用填的，否则是站点的 host[:port][/path]。 */
    public String effectiveInstanceId() {
        if (StringUtils.hasText(instanceId)) {
            return instanceId.trim();
        }
        var uri = baseUri();
        if (uri == null) {
            return "";
        }
        var id = new StringBuilder(uri.getHost().toLowerCase());
        if (uri.getPort() != -1) {
            id.append(':').append(uri.getPort());
        }
        if (uri.getPath() != null) {
            id.append(uri.getPath());
        }
        return id.toString();
    }

    /** 上传时的 is_public 参数，跟随默认时为 null。 */
    public Boolean publicFlag() {
        if ("public".equals(visibility)) {
            return true;
        }
        if ("private".equals(visibility)) {
            return false;
        }
        return null;
    }

    public boolean shouldDeleteRemote() {
        return deleteRemote == null || deleteRemote;
    }
}
