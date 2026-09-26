## 0.1.7 收工验收

2026-09-27：新增整体自由旋转与独立非生物预算完成，最终构建、12 项单元测试和指定实例四轮游戏验收通过。见 [收工验收](ACCEPTANCE-0.1.7.md) 与 [功能说明](BUGFIX-0.1.7.md)。后续停止功能扩展。

## 0.1.6 最终验收

2026-09-27：10 项单元测试及指定实例四轮游戏验收通过。详情见 [0.1.6 验收](ACCEPTANCE-0.1.6.md) 与 [操作修复说明](BUGFIX-0.1.6.md)。以下保留历史记录。

# MVP 验收记录

日期：2026-09-26。目标：静态场景摄影，不包含动画系统。

## 0.1.5 最终验收

2026-09-27：10 项单元测试通过；最终正式 JAR 在指定原实例的核心回归 116 项、启动与位移专项 128 项、代表模型与八对象场景 100 项均通过。详情见 [0.1.5 验收](ACCEPTANCE-0.1.5.md) 与 [修复说明](BUGFIX-0.1.5.md)。下面保留旧版验收历史。

## 历史结论

v0.1.4：仅使用用户指定的 `E:\minecrate\.minecraft\versions\1.20.1-Forge_47.4.22 Vanilla-tacz` 包验证，不使用其他游戏包。先加载同一实例的全部配置、资源包及选项复现 0.1.3：FA+Player 的实际肢体位于 EMFModelPartCustom 子节点，弯曲顶点数为 0。旧版骨骼存在并不能证明可见模型已发生动作。

最终在原实例目录、新建验收世界、离线玩家中分别使用双色 64×64 测试皮肤和该目录的 `ETF_player_skin_printout.png`；两轮各通过 **98 项游戏内检查**，10 项单元测试通过。保存图测试使用临时测试资源包覆盖测试夹具纹理，不改变正式模组或用户皮肤文件。原 FA+Player、Fresh Animations、EMF、Skin Layers 3D、Oculus / Embeddium 和光影设置保留。

新增检查逐个通过实际 StudioScreen 节点点击和数值“应用”按钮修改左肘、右肘、左膝、右膝，比较下一帧底层及外层实际网格签名；再通过“撤回”按钮确认两层恢复。每个关节也验证正面 X 旋转环的拖动，并确认一次撤回恢复整个包含多个 mouseDragged 事件的手势。六个外层在位置、旋转、缩放编辑后的矩阵最大差均为 0。主画面实际提交底层与外层弯曲顶点合计 4,160 个，显示的模型类型确认为 EMF 自定义子节点。

配置清理检查：原 `options.txt` 的 SHA-256 恢复，所有原配置文件哈希一致；测试夹具 JAR 和临时皮肤包移出。原实例只留一个正式 `posestudio-0.1.4.jar`。原 0.1.3 位于 `E:\PoseStudio\build\instance-check-20260926-195726-default\mods`。已有用户世界未打开；新建的 PoseAcceptance 测试世界保留在实例的 saves 目录。证据见 `release/evidence/0.1.4`。

本次没有使用真实账号认证或 PCL 图形界面登录，使用该 PCL 实例的 Forge 启动配置离线启动。验证结论限定这套包的当前模型与配置，不声称任意 CEM、其他独立骨骼库或其他包均兼容。

v0.1.3 双层皮肤与鼠标控制：10 项单元测试通过。使用显式的 64×64 蓝色底层 / 橙白外层皮肤，classic 与 slim 分别测试普通 Forge 和原 Vanilla-tacz 完整模组列表的光影环境，每个组合通过 42 项游戏内检查。四个肢体的外层实际参与连续弯曲，普通外层各记录 520 个顶点；Skin Layers 3D classic 四肢合计 6,832 个外层顶点，slim 合计 6,384 个。3D 环境底层连续网格每帧记录 2,080 个顶点，普通双层环境底层加外层合计 4,160 个。已人工检查实际前视和侧视截图，外层随肘膝连续弯曲。

鼠标检查覆盖中央视口滚轮缩放、中键拖动缩放、空白处左拖转动、右拖平移、列表滚轮隔离、缩放上下界，以及摄影机操作不改变 Actor。原有实体冻结、Gizmo、服务端变换、JSON、捕获和退出恢复回归均通过。中英两种界面分别在上述环境中运行。原实例仅用于读取和复制模组及必要渲染配置；世界与全部测试文件在 E 盘工程的独立运行目录中。证据位于 `release/evidence/0.1.3`，细节见 `BUGFIX-0.1.3.md`。

兼容结论限定已测试的 Skin Layers 3D 1.11.2、Oculus 1.8.0、Embeddium 0.3.31、ComplementaryShaders v4.7.2 和原实例的默认模型路径。类似 ModelPart 皮肤层可以使用通用适配；其他独立网格实现、特殊模型资源包和未来版本没有逐一验收。

v0.1.2 光影兼容修复：9 项单元测试通过。最终发行包在 Forge 47.4.22、原 Vanilla-tacz 实例的完整模组 JAR 列表和 ComplementaryShaders v4.7.2 光影下通过 28 项游戏内检查；在 Forge 47.4.10 无额外渲染优化模组、英文界面下也通过 28 项。完整组合测试在独立目录使用新建世界，复制原模组和必要渲染设置，其他配置使用测试目录默认值。修复后记录 2,080 个实际弯曲网格顶点，并确认共享模型临时变换栈没有遗留。详细原因与边界见 `BUGFIX-0.1.2.md`。

v0.1.1 双语更新：95 个语言资源键在简体中文和英语中完整对应。分别使用 `zh_cn`、`en_us` 启动实际 Forge 客户端，两种语言各通过 25 项游戏内检查，9 项单元测试通过。检查包含实际加载语言、按钮翻译、自定义关节翻译、未知 Mod 骨骼保留原名、服务端错误键在客户端翻译，以及原有 20 项静态摆拍流程。中文和英文截图分别位于发行目录的 `evidence/zh_cn` 与 `evidence/en_us`。

中文界面的按钮、骨骼列表、数值属性和底部提示已通过实际截图检查。保存的 JSON 骨骼键保持不变。其他语言回退英语；第三方实体名称和未知骨骼由对应模组的语言资源或原始名称决定。

v0.1.0 初始发行 JAR 使用 Minecraft Java 1.20.1 / Forge 47.4.10 / Java 17 的普通 `forgeclient` 启动入口，通过新建超平坦单人世界进行验收。构建重映射成功，9 项单元测试通过，20 项游戏内检查通过。

测试夹具是单独注册的 Mod 实体，使用独立 MobRenderer + HierarchicalModel，并在 setupAnim 中持续生成 idle 旋转。它从 Minecraft 的已有部件层级烘焙模型，用于验证通用 Adapter，不包含在发行 JAR 中。

## 用户要求对应结果

| 验收项 | 结果 | 证据和范围 |
|---|---|---|
| 进入 / 退出 Studio | PASS | 服务端确认后进入，退出恢复 HUD 和相机设置。 |
| 选择玩家并编辑肩、肘、髋、膝 | PASS | 本地玩家、四个新增关节、已有 arm / leg 部件。 |
| 肘膝连续圆弧形变 | PASS | 连续扫掠网格数学测试、实际发行包侧视截图。 |
| 原版实体已有 ModelPart | PASS | 猪的 head、body 和四肢；实际修改头部 Z 旋转。 |
| 兼容 Mod 实体已有模型结构 | PASS | 独立测试 Mod renderer 的 root 和命名 children，实际修改 root X 旋转。 |
| Actor 整体移动与旋转 | PASS | 数值变换经过客户端→服务端→客户端，检查位置和 Yaw。 |
| 冻结并保持 Pose | PASS | 非玩家 tickCount 不变、NoAI / NoGravity 生效，部件静态值在渲染时重新应用。 |
| 独立 XYZ 摄影机 | PASS | 实际 Camera 坐标与玩家身体位置不同。 |
| Pitch / Yaw / Roll / FOV | PASS | 实际 Pitch/Yaw，相机状态和倾斜截图验证 Roll/FOV。 |
| 摄影机穿块 | PASS | Camera 位于超平坦世界的实体方块内部，位置不被碰撞修正。 |
| 全部编辑辅助隐藏 | PASS | Capture 中无 screen / HUD；节点、线、gizmo、选择框均不绘制。 |
| 使用 Minecraft 当前环境截图 | PASS | Screenshot.takeScreenshot 读取实际 Minecraft framebuffer。 |
| 可拖动旋转 Gizmo | PASS | 用实际投影拾取旋转环并拖动，验证选中部件的数值发生改变。 |
| JSON 保存 / 读取 | PASS | 游戏内和单元测试 round trip，校验类型、数值、文件名、数组和大小。 |
| 正常退出恢复原状态 | PASS | 服务端锁释放，NoAI / NoGravity 和玩家位置恢复。 |
| Q / E 相机输入隔离 | PASS | 相机 E 输入不会触发玩家背包，Studio 中丢弃普通游戏动作点击。 |

## 自动化输出

- 构建：`release/evidence/final-build.log`。
- 发行包普通 Forge 启动日志：`release/evidence/production-validation.log`。
- 20 条游戏内 PASS：`release/evidence/acceptance.txt`。
- 单元测试 XML：`release/evidence/unit-tests/`（BendMath 4 项，PoseSerializer 5 项）。

截图：

- `01-player-editor.png`：玩家编辑器和节点。
- `01b-player-side.png`：玩家肘膝侧视、连续弯曲和手持物。
- `02-vanilla-editor.png`：原版模型部件编辑。
- `03-mod-editor.png`：测试 Mod 的已有层级。
- `04-capture.png`：隐藏编辑器、Roll 构图和正常世界渲染。

构建命令：

```powershell
Set-Location E:\PoseStudio
.\studio.ps1 test build verificationJar --offline
```

开发环境游戏内验收：

```powershell
.\studio.ps1 runClient -Pacceptance --offline
```

发行包验收由 `tools/production-smoke.ps1` 读取已有 Forge 1.20.1 启动配置和库，使用全新的 `build/production-run` 目录。只向这个测试目录复制发行 JAR 和夹具 JAR；不加载原实例的 Mod、存档或账号配置。需要完整的本地 Forge profile 才能使用该脚本。

## 未验证与明确不实现

| 项目 | 状态 |
|---|---|
| 第三方 Shader / Oculus / Embeddium / 大型整合包 | NOT VERIFIED |
| 真实第三方 Mod 的特殊装备和附加层 | NOT VERIFIED |
| GeckoLib 等不同骨骼库 | NOT IMPLEMENTED；安全降级为 Actor 编辑 |
| 多人专用服务器现场测试 | NOT VERIFIED；已在集成服务器验证网络与服务端冻结 |
| 多客户端共享自定义 Pose | NOT IMPLEMENTED；Pose 是本地摄影状态 |
| 超出已加载区块的摄影机自动加载世界 | NOT IMPLEMENTED |
| 崩溃后持久锁快照恢复 | NOT IMPLEMENTED |
| 时间轴、关键帧、动画播放、IK、物理、自制世界渲染器 | NOT APPLICABLE；明确排除在本版本范围外 |

测试结果只覆盖这里列出的环境；没有将可访问 ModelPart 的实现原则表述成“兼容所有 Mod”。
