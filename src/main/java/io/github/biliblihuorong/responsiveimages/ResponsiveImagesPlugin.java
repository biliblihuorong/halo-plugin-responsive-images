package io.github.biliblihuorong.responsiveimages;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * 插件入口。实际工作由内容处理器和模板 img 标签处理器完成。
 */
@Component
public class ResponsiveImagesPlugin extends BasePlugin {

    public ResponsiveImagesPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
