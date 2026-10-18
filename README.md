# halo-plugin-responsive-images

Halo 插件：渲染页面时为图片补上 `srcset`，让浏览器按屏幕宽度加载合适尺寸的图片。不改主题模板，也不需要额外的清单文件。

## 处理什么

- **文章与独立页面正文**：收集本页所有图片，按来源合并成一次批量查询，把结果写回 HTML。
- **主题模板里的图片**：封面、头像、友链图标等，通过 Halo 的 `ElementTagPostProcessor` 扩展点处理，任何主题都适用。
- 已经有 `srcset` 的图片（包括 Halo 自带缩略图补上的）一律跳过；只新增属性，不改 `src`。主题用 `thumbnail.gen` 给外链图生成的 `srcset`（每一项都是原图地址）视为没有，会被替换。
- 查询失败或超时（默认 800 毫秒）时保持原图，失败结果缓存 1 分钟，避免每次渲染都等超时。

## 图片来源

在插件设置「图片来源」里按顺序添加，图片地址命中第一个来源的域名就只问这个来源。

| 类型 | 适合 | 说明 |
| --- | --- | --- |
| 查询接口 | ImgNest 等预生成衍生图的图床 | `POST {endpoint}`，请求 `{"urls":[...]}`（每批最多 50 个），响应 `{"data":{"items":[{"found","variants":[{"url","width","height"}]}]}}` 或不带 `data` 外壳的 `{"items":[...]}`，`items` 与请求顺序一致。只返回真实存在的版本，所以不会出现死链接 |
| URL 规则 | 能实时缩放的 CDN（阿里云 OSS、腾讯云 COS、七牛、imgproxy 等） | 按模板拼地址，`{url}` 原图地址，`{sep}` 自动为 `?` 或 `&`，`{width}` 宽度。例如 `{url}{sep}x-oss-process=image/resize,w_{width}` |

### 接入 ImgNest

1. ImgNest 后台在上传规则里打开「响应式衍生图」，历史图片在「历史图片补生成」里补齐。
2. 插件里添加一个「查询接口」来源：图片域名填存储的 `base_url` 主机（如 `img.example.com`），查询接口填 `https://你的ImgNest站点/api/variants`。
3. 服务端直接调用，不需要配置 CORS，也不需要 Token。

## 基本设置

| 设置 | 默认 | 说明 |
| --- | --- | --- |
| 默认 sizes | `(max-width: 800px) 100vw, 800px` | 图片没有 `sizes` 时补上，按主题正文宽度调整效果最好 |
| 补写宽高属性 | 关 | 写入最大版本的 `width`/`height` 减少页面跳动，需要主题给图片设置 `height: auto` |
| 衍生图加载失败时回退 | 开 | 写一个只执行一次的 `onerror`：撤下 `srcset`，改加载原本的 `src`（懒加载图片用 `data-responsive-src`）。站点 CSP 禁止内联脚本时关闭 |
| 懒加载地址属性 | `data-src`、`data-lazy-src`、`data-original` | 懒加载主题把真实地址放在这些属性里，优先于 `src` |
| 懒加载图片的 srcset 属性名 | `srcset` | lazysizes、vanilla-lazyload 等库填 `data-srcset` |
| 结果缓存 | 30 分钟 | 按图片地址缓存在插件内存里，最多 2 万条；修改来源设置后自动清空 |

## 懒加载主题适配

懒加载主题常把占位图放在 `src`、真实地址放在 `data-lazy-src` 等属性里。插件从懒加载属性读到地址时：

- `src` 上占位图自带的 `srcset`（例如 Halo 自带缩略图补上的）描述的是占位图，会被替换；
- 同时写入 `data-responsive-src="原图地址"`，标明这个 `srcset` 属于哪张图。主题脚本可以据此判断：`data-responsive-src` 等于真实地址时保留 `srcset`，否则仍按占位图处理。没装插件时没有这个属性，主题行为不变。

## 开发

需要 Java 21。

```bash
./gradlew build       # 测试并打包到 build/libs
./gradlew haloServer  # 启动带插件的 Halo 开发环境（需要 Docker）
```

最低支持 Halo 2.20（`ElementTagPostProcessor` 从这个版本开始提供），用 2.20 的 API 编译。

## 许可证

MIT
