# COMP2850 · Campus Lost & Found

一个可以独立运行的 Kotlin / Ktor 校园失物招领模块。界面为英文，项目根目录包含源码、可运行 JAR、数据库和自动化测试。主页面是 `/lost-found`，现有网站的上传按钮可直接链接至 `/lost-found/new`。

## 在 Windows 上打开

1. 双击 **Start.cmd**。首次启动会在首页就绪后打开浏览器，请保留运行服务的终端窗口。再次双击时会打开已运行的服务。如果浏览器没有弹出，可直接访问 **http://127.0.0.1:8080/lost-found**。
2. 点击 **Upload an item**。填入物品名称和拾获日期。可以加标签、领取地点、JPEG 或 PNG 照片，点击 **Upload item** 后进入新记录的详情页。
3. 点击 **Item board**，输入关键词即可看到结果。点击物品卡片进入详情，点击详情里的标签可查看同标签的其他物品。没有 JavaScript 时，仍可使用 **Search** 按钮搜索和上传表单。
4. 删除时，打开物品详情，点 **Delete item**，核对名称、日期和领取地点，再点 **Confirm deletion**。选择 **Cancel** 会回到详情。确认后，记录及其附图会从数据库中移除。
5. 终端按 **Ctrl+C** 停止。下次在同一个项目目录启动时，已有记录和图片会保留。

启动需要 Java 17 或更高版本。脚本优先寻找本机 IntelliJ IDEA 自带的 Java。使用终端也可在本目录执行 `java -jar app/lost-found.jar`。如果 8080 已被其他项目占用，在 PowerShell 运行 `$env:PORT='8085'`，再运行 `.\Start.cmd`，打开终端显示的地址。

## 试一下 red 搜索

| 上传的名称 | 标签 | 输入 red 后 |
| --- | --- | --- |
| Red skirt | red, clothing, skirt | 名称和标签都包含关键词，出现 |
| Red pen | red, stationery, pen | 名称和标签都包含关键词，出现 |
| Canvas tote bag | red, bag, canvas | 标签包含关键词，出现 |
| Silver water bottle、Black earbuds case、Blue knitted scarf | 分别含 silver、black、blue | 搜索 red 时隐藏 |

桌面版已录入这六条虚构的校园失物，以 **Example** 标识。示例可像普通记录一样删除。对新的数据目录可在停服后运行 `java -jar app/lost-found.jar --load-examples`，每个数据库只导入一次。删除的示例在重启或再次导入后均会保持删除。

搜索忽略大小写，支持名称和标签的子串匹配。用空格隔开的每个词都须命中，例如 `red clo` 能找到红裙子。结果按拾获日期从近到远排序，同日按记录编号倒序，每页最多 24 项。

## 数据与代码

| 位置 | 用途 |
| --- | --- |
| `app/lost-found.jar` | 含运行依赖的可执行程序。运行时可离线使用。 |
| `data/lost-found.mv.db` | 本地 H2 数据库。物品字段、标签、照片格式和图片二进制保存在同一条记录中。停止程序后可复制整个 data 文件夹作备份。 |
| `src/main/resources/lost-found/schema.sql` | 建表与索引。首次启动自动执行，同一目录重复启动时保留已有记录。 |
| `src/main/kotlin/campus/found/Application.kt` | 路由、表单校验、模板渲染和错误处理。 |
| `src/main/kotlin/campus/found/Items.kt` | JDBC 参数化查询、保存、去重、分页和照片读取。 |
| `src/main/kotlin/campus/found/Photos.kt` | 校验实际图片格式与尺寸，限制文件大小，重新编码后入库。 |
| `src/main/kotlin/campus/found/Problems.kt` | 统一的错误编号、HTTP 状态、英文提示及对应表单字段。 |
| `src/main/resources/lost-found/templates/`、`assets/` | Pebble 页面、样式、即时搜索与本地图片预览。 |
| `tests/test_feature.py` | 从真实 HTTP 表单到实际数据库的自动测试，使用单独的临时数据目录。 |

项目默认仅监听本机。可用环境变量 `LOST_FOUND_DATA` 指向其他数据目录，用 `PORT` 调整端口。

## 把上传按钮接入小组项目

已有导航中可用这两个链接。表单页的提交按钮已经接好上传路由。

```html
<a href="/lost-found">Lost &amp; found</a>
<a href="/lost-found/new">Upload a found item</a>
```

同一个 Ktor 应用中，复制 `campus/found` Kotlin 源文件以及整套 `resources/lost-found` 资源，按本项目 `pom.xml` 配置 H2、Pebble 和 Ktor 依赖。独立项目附带 Maven 构建；使用课程工具链时可复用同样的 Kotlin 源码和资源。小组项目可在自己的 routing 中注册路由，保留原有首页。例如：

```kotlin
val foundItems = Items(Path.of("data", "lost-found-module"))
routing {
    lostFoundRoutes(foundItems)
}
```

需要导入 `campus.found.Items`、`campus.found.lostFoundRoutes`、`java.nio.file.Path` 和 `io.ktor.server.routing.routing`。独立版的 `lostFoundModule` 同时安装 StatusPages。小组项目若已有 StatusPages，在已有配置中合并本模块的 InputProblem 和数据库异常映射；整合时保持已有模块的异常处理约定。这里的上传按钮已在独立版实测，小组仓库的导航需要在该仓库中接入。

| 请求 | 行为 |
| --- | --- |
| GET /lost-found | 物品列表，可带 q 和 page |
| GET /lost-found/results | 即时搜索使用的结果片段 |
| GET /lost-found/new | 上传表单，为这次表单生成唯一提交键 |
| POST /lost-found/items | 接收 multipart/form-data，成功后以 303 跳转到详情；同一表单重发会返回原记录 |
| GET /lost-found/items/{id} | 物品详情 |
| GET /lost-found/items/{id}/photo | 读取该物品的 JPEG 或 PNG |
| GET /lost-found/items/{id}/delete | 查看该条记录的删除确认页 |
| POST /lost-found/items/{id}/delete | 核验本物品的确认令牌，删除记录及附图，返回列表 |

## 报错速查

| 编号 | 场景 | 处理 |
| --- | --- | --- |
| LF01 / LF02 / LF03 | 名称为空或超过 100 字符；日期无效；日期在将来 | 修改对应字段。日期按利兹当地日历判断。 |
| LF04 / LF05 | 标签数量超过 8 或单个超过 24 字符；领取地点超过 120 字符 | 精简对应内容。多个标签用英文或中文逗号分隔。 |
| LF06 / LF07 | 实际文件格式不是 JPEG/PNG；图片超过 5 MiB | 导出支持的格式，或压缩后重新选择。 |
| LF08 / LF09 | 图片损坏；边长超过 8192 像素或总像素超过 1600 万 | 重新导出或调整图片尺寸。 |
| LF10 | 搜索词超过 100 字符 | 缩短关键词。无匹配是正常的空结果。 |
| LF11 / LF12 | 物品编号不存在；记录没有照片 | 返回列表核对编号，或查看文字详情。 |
| LF13 / LF14 | 上传表单无效；请求体过大 | 重新打开上传表单或减小照片。 |
| LF15 / LF16 | 数据库暂不可用；服务发生其他错误 | 查看服务终端日志，并检查数据目录权限与占用。 |
| LF17 / LF18 / LF19 | 跨站表单提交；分页参数无效；页面不存在 | 从本站上传页重新操作，或返回物品列表。 |
| LF20 | 删除确认信息缺失、无效或与物品不符 | 从对应物品的详情页重新进入 Delete item。 |

校验失败时，页面会显示错误编号和提示，普通文本字段会尽量保留。浏览器要求重新选择照片。新增错误时在 `Problems.kt` 定义编号、状态、提示和字段，并在触发位置使用 `InputProblem`。

## 修改源码和运行测试

用 IntelliJ IDEA 打开 `pom.xml`，选择 JDK 17 或以上。在 Maven 的 Lifecycle 中运行 **clean** 和 **package**，或使用终端执行 `mvn clean package`。新 JAR 位于 `target/lost-found.jar`。运行 `java -jar target/lost-found.jar` 即可测试改动；要让 Start.cmd 使用新版本，先停止服务，再用新 JAR 更新 `app/lost-found.jar`。

本机有 Python 3.9+ 且能调用 Java 17+ 时，在项目根目录运行：

```powershell
py -3 tests/test_feature.py
```

测试默认启动打包好的 `app/lost-found.jar`，自动分配测试端口，验证名称和标签搜索、详情跳转、图片读取、错误提示、重复提交、分页以及重启后记录保留。若终端中的 java 版本较旧，可用环境变量 `JAVA_EXE` 指定正确的 java.exe；测试重新编译的 JAR 时可设置 `LOST_FOUND_JAR` 为其绝对路径。
