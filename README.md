ThelemaLib 是一个为 NeoForge 模组开发提供工具库的模组，包含配方系统、数据持久化、区域管理、流体注册、工具提示构建等模块。

---

## 模块导航

### Recipe 配方系统

| 模块 | 说明 |
|------|------|
| [Recipe 概述](recipe) | 基于原版扩展的 `t_` 配方类型，支持动态输出 |
| [Handle 执行块](handle) | 操作列表的完整参考手册 |
| [Handle 元信息字段](meta) | 控制操作上下文的 `range`、`input`、`input_new_item` |
| [条件系统](conditions) | 分支条件类型完整列表 |

### Data 数据系统

| 模块 | 说明 |
|------|------|
| [LevelMap](levelmap) | 基于 `SavedData` 的持久化 Map，自动同步至客户端 |
| [类型注册](type-registry) | `KeyRegistry` 与 `ValueRegistry` 自定义类型注册教程 |
| [客户端使用](levelmap-client) | `LevelMapClient` 读取同步数据 |

### 工具类

| 模块 | 说明 |
|------|------|
| [ContainerHelper](container-helper) | 以箱子界面打开任意 `IItemHandler` |
| [BreakManager](break-manager) | 带进度破坏的方块裂纹动画管理 |
| [DamageQueue](damage-queue) | 绕开无敌帧的伤害排队调度 |
| [ExactMove](exact-move) | 实体精确移动到目标坐标 |
| [FluidCreator](fluid-creator) | Builder 风格的一站式流体注册 |

### 区域系统

| 模块 | 说明 |
|------|------|
| [Area 区域系统](area) | 立体区域管理，自定义行为，自动持久化 |

### 工具提示

| 模块 | 说明 |
|------|------|
| [ToolTip 工具提示](tooltip) | 链式多行文本构建器，支持条件分支与样式 |
