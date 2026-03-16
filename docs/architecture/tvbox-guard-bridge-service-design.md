# TVBox Guard Bridge Service 设计草案

状态日期：2026-03-14

本文档用于明确 Kototoro 在 `Guard/JAR` TVBox 源上的下一阶段方案。

结论先行：

- `Guard` 源的本地 Android 执行路径已经进入 `CRASH_NATIVE + JNI DETECTED ERROR` 区域
- 继续追加 Kotlin/Java 层宿主补丁的收益已经很低
- 下一步应切换到 `远程执行/桥接服务` 方案

## 一、为什么切到桥接服务

当前本地结论已经比较确定：

- `Init.init(context)` 预初始化已成功
- `DexNative.getCacheDir()` 空上下文问题已跨过
- `Guard` 源仍在构造阶段触发 `CRASH_NATIVE`
- `ApplicationExitInfo` 已返回 `JNI DETECTED ERROR IN APPLICATION`

这说明问题已经不再是普通的 Java 宿主字段、类加载顺序或 `Context` 传递问题，而是 `Guard so/JNI` 与当前本地运行环境存在更深层的不兼容。

继续本地硬对齐的问题：

- 调试成本高
- 崩溃粒度在 native/JNI 层
- 可维护性差
- 对应用稳定性有持续风险

切换桥接服务的收益：

- 客户端只处理标准 HTTP 协议
- `Guard/JAR` 执行环境可以独立迭代
- 可以集中做限流、缓存、沙箱和诊断
- 兼容失败不再直接拖垮客户端进程

## 二、LunaTV 能提供什么启发

LunaTV 不是本地 Android `Guard` runtime 的解决方案，但它证明了三件事：

1. `spider.jar` 可以由服务端统一管理
2. TVBox 源 API 可以由服务端统一代理和重写
3. 兼容性问题可以通过“服务端桥接 + 客户端仅消费标准 TVBox 配置”来降低

参考点：

- [`TVBOX.md`](/E:/kototoro_demo/LunaTV/docs/integration/TVBOX.md)
- [`spiderJar.ts`](/E:/kototoro_demo/LunaTV/src/lib/spiderJar.ts)
- [`route.ts`](/E:/kototoro_demo/LunaTV/src/app/api/tvbox/route.ts)

但 LunaTV 本身没有解决当前这个“Android 客户端本地执行 Guard so 崩溃”的问题。它给出的正确方向是：

- 不要让客户端直接承担 Guard 执行
- 让服务端承担 `spider/jar/guard` 相关复杂性

## 三、目标

新增一条 `Guard Bridge Runtime` 路径，使 Kototoro 对 `type = 3` / `api = csp_*` / `Guard` 源支持如下行为：

- 客户端不再直接实例化 `Guard` spider
- 客户端通过桥接服务执行 `home/homeVod/category/detail/search/play/proxy`
- 客户端继续复用现有 `TVBoxSpiderRuntime` 接口和视频播放模型

## 四、总体架构

### 1. 客户端

保留现有 runtime 分流，但新增远程桥接 runtime：

- `type = 4` -> `TVBoxQuickJsSpiderRuntime`
- 普通可本地跑的 `type = 3/csp_*` -> `TVBoxJarSpiderIsolatedRuntime`
- `Guard` 或已知本地 fatal 源 -> `TVBoxGuardBridgeRuntime`

### 2. 桥接服务

桥接服务负责：

- 接收标准化 TVBox 请求
- 使用服务端运行环境执行 `Guard/JAR`
- 返回标准化结果给 Kototoro

### 3. 执行环境

服务端需要具备以下之一：

- 真正可执行 TVBox `spider/jar/guard` 的宿主
- 或二次封装后的 Box 风格执行器

这里的关键不是“提供 spider.jar 下载”，而是“真正替客户端执行 Guard”。

## 五、最小协议

建议采用单端点 + 动作分发，先压缩实现复杂度。

### 1. Endpoint

建议：

- `POST /api/tvbox-bridge/execute`

### 2. Request

建议字段：

```json
{
  "sourceId": "JSON_TVBOX_XXXX",
  "sourceName": "斗鱼┃直播",
  "sourceConfig": "{...}",
  "action": "home",
  "timeoutMs": 20000,
  "categoryId": null,
  "page": 1,
  "query": null,
  "itemId": null,
  "flag": null,
  "playId": null,
  "proxySpec": null,
  "headers": {},
  "queryParameters": {}
}
```

说明：

- `sourceConfig` 直接传 Kototoro 已存储的标准化 TVBox 配置
- 桥接服务自行解析 `site/root/meta`
- 这样可以避免客户端和服务端分别维护两套源结构

### 3. Response

建议复用当前客户端已有的返回形态：

```json
{
  "payload": "...",
  "statusCode": 200,
  "contentType": "application/json",
  "headers": {},
  "body": "",
  "redirectUrl": null,
  "errorCode": null,
  "errorMessage": null
}
```

### 4. 错误码

建议最少包含：

- `unsupported_source`
- `upstream_timeout`
- `upstream_execution`
- `upstream_proxy_error`
- `auth_failed`
- `rate_limited`

客户端侧可继续保留：

- `fatal_native_crash`

但它只用于本地 runtime，桥接服务不需要暴露 JNI/native 细节。

## 六、客户端接入点

### 1. 新增 Runtime

建议新增：

- `TVBoxGuardBridgeRuntime`

职责：

- 实现 `TVBoxSpiderRuntime`
- 将 `getList/getDetails/getPages/getFilterOptions` 映射到桥接服务
- 维持与当前 `TVBoxJarSpiderIsolatedRuntime` 相同的模型输出

### 2. RuntimeFactory 调整

在 [`TVBoxSpiderRuntimeFactory.kt`](/E:/kototoro_demo/Kototoro/app/src/main/kotlin/org/skepsun/kototoro/core/parser/tvbox/TVBoxSpiderRuntimeFactory.kt) 中新增分支：

- 已知 `Guard` 源优先走 bridge
- 已知本地 fatal 的 `sourceId` 走 bridge
- 非 Guard 仍保留本地 runtime

### 3. 本地失败后的自动切换

建议策略：

- 第一次本地检测到 `fatal_native_crash`
- 记入本地缓存或配置
- 后续直接走 `TVBoxGuardBridgeRuntime`

这样既保留本地能力，也避免对同源反复 native 崩溃。

## 七、桥接服务能力边界

### V1 必做

- `home`
- `home_vod`
- `category`
- `detail`
- `search`
- `play`

### V1 可暂缓

- `proxy`

理由：

- 先让大多数 Guard 源能“进首页、进详情、拿播放地址”
- 复杂 `proxyLocal` 可以在第二阶段补

## 八、安全要求

桥接服务不能做成裸开放接口。

最少要求：

- Token 鉴权
- 源白名单
- 请求频率限制
- 单请求超时
- 单源并发限制
- 结果缓存
- 执行日志

建议：

- 不允许客户端提交任意外部 JAR URL 并即时执行
- 只允许执行已导入且已记录的 TVBox 源

## 九、缓存建议

### 客户端缓存

- `play` 结果短缓存
- `detail/home` 结果短缓存
- `fatal_native_crash -> bridge` 决策缓存

### 服务端缓存

- `spider/jar` 下载缓存
- `home/category/detail/play` 结果缓存
- 单飞控制

## 十、实施顺序

### 阶段 1

- 新增桥接协议文档
- 新增客户端配置项：桥接服务地址、token
- 新增 `TVBoxGuardBridgeRuntime`

### 阶段 2

- 仅对 `Guard` 源启用桥接
- 本地普通 jar 保持不变

### 阶段 3

- 增加自动降级：
  - 本地 fatal -> 自动桥接

### 阶段 4

- 评估是否将更多 `csp_*` 源迁移到桥接

## 十一、为什么这条路线比继续本地 Guard 对齐更合适

原因不是“远程更时髦”，而是当前证据已经表明：

- 本地 Guard 崩溃是 JNI/native 级
- 不是单纯的类加载顺序问题
- 不是单纯的 `Context` 注入问题
- 不是单纯的 `DexClassLoader` 策略问题

在这种情况下，继续本地硬对齐会把大量时间消耗在不可预测的 native 行为上。

桥接服务至少有三个直接优势：

- 把客户端从高风险 native 执行里解耦
- 让 Guard 兼容问题集中在服务端收敛
- 让 Kototoro 在产品层面尽快恢复“可用”

## 十二、当前建议

建议正式选型：

- **Kototoro 本地 runtime**
  - 继续服务普通 TVBox 源
- **Guard 源**
  - 进入桥接执行路线
- **产品提示**
  - 明确说明：Guard 源需要桥接服务支持

这比继续在客户端本地追 `JNI DETECTED ERROR` 更符合当前阶段的投入产出比。
