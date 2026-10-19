# ImgNest 响应式图片

ImgNest 配套的社区 Halo 插件：渲染页面时为图片补上 `srcset`，让浏览器按屏幕宽度加载合适尺寸的图片。不改主题模板，也不需要额外的清单文件。本项目由 shell 维护，不依赖特定主题，不代表 Halo 官方。

支持通用图片查询接口和 CDN URL 规则；另外提供「ImgNest 图床」存储策略，Halo 附件库可以直接把图片上传到同一作者维护的独立自托管项目 [ImgNest](https://github.com/biliblihuorong/imgnest)。ImgNest 适配是可选功能，使用 CDN URL 规则无需安装 ImgNest。本插件不提供托管图床或 CDN 服务。

0.1.1 将插件显示名从「响应式图片」改为「ImgNest 响应式图片」。插件标识仍为 `responsive-images`，仓库名、jar 文件名和现有配置保持不变，升级无需迁移配置。

应用市场图标与安装包使用同一份自有图标：[src/main/resources/logo.png](src/main/resources/logo.png)。可下载该 PNG 用于市场上架；矢量源文件和许可分别见 [logo.svg](src/main/resources/logo.svg)、[LOGO-LICENSE.txt](src/main/resources/LOGO-LICENSE.txt)。

## 安装

插件声明支持 Halo 2.20 及以上，用 Halo 2.20.20 API 编译，字节码目标为 Java 21；运行环境还需满足所用 Halo 版本的 Java 要求。现有 [PR #1](https://github.com/biliblihuorong/halo-plugin-responsive-images/pull/1)、[PR #2](https://github.com/biliblihuorong/halo-plugin-responsive-images/pull/2)、[PR #3](https://github.com/biliblihuorong/halo-plugin-responsive-images/pull/3) 记录覆盖 Halo 2.20.20、2.22.11 和 2.27.0；这些记录来自 AI 辅助开发，不能代替维护者对待发布版本的人工复核，也不表示所有主题和后续 Halo 版本都已验证。

从 [Releases](https://github.com/biliblihuorong/halo-plugin-responsive-images/releases) 下载已发布的 jar，在 Halo 后台「插件 → 安装」上传。升级前备份 Halo 数据和插件、存储策略配置。正在准备的 0.1.1 版本说明见 [docs/releases/v0.1.1.md](docs/releases/v0.1.1.md)，发布前人工复核项目见 [docs/release-checklist.md](docs/release-checklist.md)。

## 处理什么

- **文章与独立页面正文**：收集本页所有图片，按来源合并成一次批量查询，把结果写回 HTML。
- **主题模板里的图片**：封面、头像、友链图标等，通过 Halo 的 `ElementTagPostProcessor` 扩展点处理，任何主题都适用。
- 已经有 `srcset` 的图片（包括 Halo 自带缩略图补上的）一律跳过；只新增属性，不改 `src`。主题用 `thumbnail.gen` 给外链图生成的 `srcset`（每一项都是原图地址）视为没有，会被替换。
- 查询失败或超时（默认 800 毫秒）时保持原图，失败结果缓存 1 分钟，避免每次渲染都等超时。

## 图片来源

在插件设置「图片来源」里按顺序添加，图片地址命中第一个来源的域名就只问这个来源。

| 类型 | 适合 | 说明 |
| --- | --- | --- |
| 查询接口 | ImgNest 等预生成衍生图的图床 | `POST {endpoint}`，请求 `{"urls":[...]}`（每批最多 50 个），响应 `{"data":{"items":[{"found","variants":[{"url","width","height"}]}]}}` 或不带 `data` 外壳的 `{"items":[...]}`，`items` 与请求顺序一致。服务应只返回已经生成的版本；远端删除或链接失效时仍可能加载失败 |
| URL 规则 | 能实时缩放的 CDN（阿里云 OSS、腾讯云 COS、七牛、imgproxy 等） | 按模板拼地址，`{url}` 原图地址，`{sep}` 自动为 `?` 或 `&`，`{width}` 宽度。例如 `{url}{sep}x-oss-process=image/resize,w_{width}` |

### 接入 ImgNest

1. ImgNest 后台在上传规则里打开「响应式衍生图」，历史图片在「历史图片补生成」里补齐。
2. 插件里添加一个「查询接口」来源：图片域名填存储的 `base_url` 主机（如 `img.example.com`），查询接口填 `https://你的ImgNest站点/api/variants`。
3. 服务端直接调用，不需要配置 CORS，也不需要 Token。

### 接口兼容基线

ImgNest 仍处于预览阶段。本插件按以下接口契约适配，不要求部署作者的站点；也不能仅凭 ImgNest 的版本号保证接口兼容。已有测试使用本地构建的 ImgNest，记录未锁定其提交，因此目前不声明已经验证的最低 ImgNest 发布版本。接入前请在自己的实例确认这些接口：

| 功能 | 实例需要提供的接口 |
| --- | --- |
| 响应式版本查询 | `POST /api/variants`，接受 `urls` 数组，按请求顺序返回 `items` 和每个版本的 `url`、正整数 `width`，可选 `height`；不要求 Token |
| 附件上传 | `POST /api/v2/upload`，接受 Bearer Token 和 multipart `file`，可选 `policy_id`、`album_id`、`is_public`；成功响应为 `code: 0`，`data` 含图片 ID 和可访问链接 |
| 同步删除 | `DELETE /api/v2/images/{id}`，接受 Bearer Token；移入图床回收站，404 视为已经删除 |

ImgNest 的部署、API 开启、账户权限及未来接口调整由图床实例维护者负责。接口异常时，查询模式保留原图；附件上传会报错，需要恢复图床服务或改用其他存储策略。

## 上传到 ImgNest（存储策略）

在 Halo 后台「附件 → 存储策略 → 新建」选择「ImgNest 图床」，之后上传附件时选这个策略即可。走 ImgNest 自己的 `/api/v2` 接口。

| 设置 | 说明 |
| --- | --- |
| ImgNest 地址 | 站点根地址，例如 `https://img.example.com` |
| API Token | 在 ImgNest「Token 与安全」页面创建。站点需要开启 API |
| 上传规则 ID | 可选，对应 `policy_id`，留空用账号的默认规则 |
| 相册 ID | 可选，对应 `album_id` |
| 公开性 | 跟随图床默认、公开或不公开，只影响 ImgNest 画廊，链接始终可直接访问 |
| 同步删除 | 默认开。Halo 删除附件时把图片移进 ImgNest 回收站（可恢复） |
| 实例 ID | 可选。删除时只处理实例 ID 相同的附件，留空时用图床地址生成。以后要换地址，先填上旧地址生成的值（如 `img.example.com`） |

- 附件链接取 ImgNest 返回的首选链接（通常是 WebP），附件的类型和大小按这个文件填写。
- 规则开启了「响应式衍生图」时，附件的 Halo 缩略图直接用 ImgNest 生成的 400/800/1600 宽版本，主题里的 `thumbnail.gen` 和 Halo 自带的正文 `srcset` 都会用上，不需要再配置「图片来源」。Halo 2.20、2.21 通过缩略图扩展点提供，2.22 起通过存储策略的缩略图接口提供。
- 只接受图片，其他文件请用别的存储策略。真正的格式由 ImgNest 读文件头判断。
- 插件发送请求时把 Token 放在 `Authorization` 请求头中，不主动将该字段写入日志。鉴权失败、接口关闭这类错误会直接提示原因；删除时遇到这类错误，Halo 照常删除附件记录，图片留在图床里；网络错误则让 Halo 稍后重试。

## 外部服务、数据与费用

默认没有配置图片来源，也没有默认 ImgNest 站点。插件只在管理员配置来源或使用 ImgNest 存储策略后启用对应功能，不向作者提供的固定服务上传图片，不包含遥测。

| 场景 | 数据去向与内容 |
| --- | --- |
| 查询接口 | Halo 服务端向配置的查询地址发送命中来源的完整图片 URL，包括 URL 中的查询参数；不发送图片文件或访问者的 Cookie。查询结果按完整 URL 缓存在插件内存中，默认 30 分钟、最多 2 万条 |
| CDN URL 规则 | 插件只拼接图片地址，不从 Halo 服务端请求 CDN；访问者浏览器加载生成的地址时会连接对应 CDN，并向其提供浏览器正常请求所包含的信息 |
| ImgNest 上传 | Halo 服务端向配置的实例发送图片文件（包括文件携带的元数据）、原文件名、可选上传规则/相册/公开性参数和 API Token；图片内容及其保留、备份、访问规则由该实例和底层存储管理 |
| ImgNest 删除 | Halo 服务端向配置实例发送图片 ID 和 API Token，请求将图片移入回收站；到期清理由 ImgNest 的配置决定 |

API Token 保存在 Halo 存储策略对应的 ConfigMap 配置中，表单使用密码输入框；本插件没有额外实现静态加密。能够读取该配置、Halo 数据库或相关备份的人员可能取得 Token，请按凭据保护这些数据。建议使用 HTTPS、专用 Token 和所需的最小权限；停用适配后可在 ImgNest 撤销 Token。不要在查询地址、URL 模板或图片地址中放入密码；签名图片 URL 会随查询发送到接口，DEBUG 日志还可能记录完整图片 URL，来源地址及 URL 模板会进入常规诊断日志。

本插件免费提供，不向用户收取图床或 CDN 费用。自行部署 ImgNest 的服务器、存储、流量和备份成本由实例使用者承担；第三方图床、对象存储和 CDN 是否收费、如何计费，以相应服务商和用户的约定为准。ImgNest 是独立项目，其许可和服务条款不因本插件采用 MIT 而改变。

## 关闭、卸载与回退

- **只关闭正文/模板处理**：在插件基本设置中关闭相应开关。下次渲染停止补写对应图片属性，不修改已保存的文章正文，也不会关闭 ImgNest 存储策略。若页面经过外部缓存，还需刷新相应缓存。
- **禁用插件**：正文和模板处理、ImgNest 上传与删除处理都停止。图床中已有图片不会因此被删除；继续访问外链取决于图床是否保留它们。依赖该存储策略的后台操作应在重新启用插件后执行。
- **删除附件**：这是独立的数据操作。同步删除开启时，插件会将匹配实例 ID 的远程图片移入 ImgNest 回收站，原链接可能立即失效；关闭同步删除只移除 Halo 记录。请勿通过批量删除附件来卸载插件。
- **卸载插件**：卸载前备份 Halo 数据及插件、存储策略配置，先将后续上传切换到其他策略。本插件没有卸载时批量删除远程图片的逻辑；Halo 对插件配置、策略和附件记录的具体保留行为请先在备份环境验证，不要把卸载当作数据迁移或清理工具。
- **从 0.1.1 回退到 0.1.0**：0.1.1 不更改插件标识 `responsive-images`、设置名称、存储策略模板名称或附件注解格式，不需要主动迁移配置。备份后禁用插件，通过 Halo 插件管理更新为保留的 0.1.0 jar，再启用并核对设置、已有图片、上传和删除。若 Halo 拒绝降级，先在备份环境验证从备份恢复的方案，不要为强制降级直接卸载或删除附件。回退插件不会恢复已被手动删除或回收的图床图片。

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

## 排查

插件把关键信息写进 Halo 日志（搜 `Responsive images` 或 `Variant lookup`）：

- 设置变化后打一次实际生效的来源，例如 `Responsive images: 1 source(s) in use: lookup:https://img.example.com/api/variants`；配置有问题（没填域名、查询接口无效、选了「URL 规则」却没填模板）会给出 WARN。选了「URL 规则」但模板为空、又填了查询接口时，按查询接口使用。
- 每次真正去查询（缓存未命中）时打一行结果和耗时，例如 `Variant lookup via …: 3 of 3 image(s) have variants (169 ms)`；失败或超时打 WARN，并附上当前超时设置。
- 想看每页的匹配情况和没有衍生图的地址，把日志级别 `io.github.biliblihuorong.responsiveimages` 调到 DEBUG。

## 开发

需要 Java 21。

```bash
./gradlew build       # 测试并打包到 build/libs
./gradlew haloServer  # 启动带插件的 Halo 开发环境（需要 Docker）
```

最低支持 Halo 2.20（`ElementTagPostProcessor` 从这个版本开始提供），用 2.20 的 API 编译。

发布新版本前完成 [人工复核清单](docs/release-checklist.md)，并提交对应的 `docs/releases/vX.Y.Z.md`。推送 `vX.Y.Z` 标签或在 Actions 里手动运行 CD 并填 `X.Y.Z`；CD 校验版本说明和源码版本，执行 `./gradlew build -Pversion=X.Y.Z`，检查 jar 内 `plugin.yaml` 的版本，再用版本说明文件创建 Release 和上传 jar、SHA-256。已有 Release 和制品不会被覆盖。市场后台版本号必须与该 jar 内的版本相同，版本说明使用同一文件；不要上传 `-SNAPSHOT` 包。

## 许可证

MIT
