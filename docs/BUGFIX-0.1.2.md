# v0.1.2：光影整合包模型等待与姿势渲染兼容修复

日期：2026-09-26。

## 触发实例与复现

用户实例：`E:\minecrate\.minecraft\versions\1.20.1-Forge_47.4.22 Vanilla-tacz`。

渲染组合包括 Forge 47.4.22、Oculus 1.8.0、Embeddium 0.3.31、EntityCulling 1.10.5、Entity Model Features 3.2.4、Entity Texture Features 7.1、Skin Layers 3D 1.11.2；光影为该实例选用的 ComplementaryShaders v4.7.2。

在项目的独立测试目录、新建超平坦世界中复现。旧版能读取 43 个玩家模型部件，已有骨骼值能初始化，但画面节点为空，肘膝连续网格未执行。旧版回归检查在 `Player rendered nodes and continuous mesh path` 失败。等待更久不会改变这一渲染路径。

## 原因

1. 旧版 ActorState 默认显示“等待角色渲染”，ModelPart 检查只在冻结实体进入 EntityRenderDispatcher.render 后执行。未冻结、不可见或被剔除的实体也显示同一个等待提示，造成无限加载的误导。
2. Oculus 的 `net.irisshaders.iris.compat.sodium.mixin.copyEntity.ModelPartMixin.onRender` 在 ModelPart.render 的 HEAD 提前取消原方法，转交 `me.jellysquid.mods.sodium.client.render.immediate.model.EntityRenderer.render`。这一批量路径绕过了旧版依赖的 render 方法内部节点捕获位置及 compile 网格挂钩。
3. 光影的主画面和阴影渲染会多次渲染同一实体；旧版在每次进入渲染时清空节点，也未区分投影矩阵的来源。

EMF 的覆盖方法是排查过的潜在兼容点，但本次默认模型复现中实际读取到的肢体是普通 ModelPart，直接触发路径是 Oculus/Embeddium 的批量渲染。最终修复不依赖 EMF 私有方法。

## 修复范围

- 选择 Actor 时立即同步检查 renderer 和可访问 ModelPart，模型读取结果不再依赖下一次画面渲染。
- 未冻结时明确提示“模型已读取；请点击‘冻结’开始编辑”。不支持的 renderer 立即显示不支持提示。
- 在优化渲染模组存在时，仅让冻结且属于当前 Adapter 的 ModelPart 使用逐部件渲染。仍使用 Minecraft 原有 PoseStack、VertexConsumer、光照、材质、模型 Cube UV 和世界渲染环境；非 Studio 实体继续由原优化路径处理。
- ModelPartMixin 优先级为 900，使最终合成后的 HEAD 防护在优化模组的提前取消之前执行。优先级顺序已通过导出的实际合成字节码和实际游戏检查验证。
- 节点在 translateAndRotate 后捕获；原有 Pose 数值与共享模型状态的恢复逻辑保留。
- 通过可选 Iris/Oculus 公共 API 判断阴影 pass，阴影不清空或覆盖编辑器节点、主画面投影矩阵。
- 冻结后超过 3 秒未收到可见节点渲染时给出明确提示，而不是继续显示“等待渲染”。

## 验证与边界

- 9 项单元测试通过。
- 已在原实例渲染组合 + Complementary 光影中通过 28 项游戏内检查。
- 检查新增了“冻结前即可完成模型发现”和“主画面实际提交弯曲肢体顶点”，并检查共享 ModelPart 临时状态栈完全恢复，不只检查骨骼列表或状态标记。修复后主画面记录 2,080 个弯曲顶点，肘膝侧视截图可见连续形变。
- 使用原实例的完整 JAR 列表另外验证；测试只复制模组和必要的渲染设置，其他模组使用独立测试目录的默认设置。使用新建世界，不加载用户存档。完整组合和无渲染优化模组的最终结果见随发行包提供的验收记录。
- 未逐一验证 OptiFine、任意第三方 CEM 资源模型、特殊附加渲染层或其他整合包。未知模型继续按可访问 ModelPart 适配，无法安全访问时降级。
- 没有增加时间轴、动画系统或新的世界渲染器。

所有工程、工具链、构建、运行目录和证据均位于 E:\PoseStudio。
