# Pose Studio 0.1.6 验收记录

日期：2026-09-27。正式 JAR SHA-256：`6E9D3F59446ACA4134F8D484CDA663E302AD41BF370D9BA9DA0EF6B40023A2C5`。

游戏测试仅使用 `E:\minecrate\.minecraft\versions\1.20.1-Forge_47.4.22 Vanilla-tacz` 原实例，保留当前模组、资源包、光影和用户按键绑定。通过其 PCL Forge profile 离线启动 StudioTest，创建新的测试世界；未使用真实账号、未在 PCL GUI 登录、未打开用户已有世界。

| 验收层 | 状态 | 结果 |
|---|---|---|
| Java 编译、正式 JAR 和夹具重混淆 | PASS | 离线构建 |
| 现有网格数学 / JSON 单元测试 | PASS | 10 项 |
| 中英资源键、格式参数和无固定 F4–F8 提示 | PASS | 129 个对应资源键 |
| 实际按键返回编辑、临时改键、标准视角、三轴多选、十次撤回 | PASS | 116 项游戏检查 |
| 玩家双层皮肤、四肘膝、骷髅可见头部、目录、冻结和 Capture 回归 | PASS | 116 项游戏检查 |
| 画面冻结、拒绝超限、玩家 / 狼 / 马整体移动和网络原子性 | PASS | 114 项游戏检查 |
| 多种代表模型、不同部件、TaCZ / LrTactical 与八对象场景 | PASS | 100 项游戏检查 |
| 原选项和全部原配置文件恢复、测试夹具 / 临时皮肤包清理 | PASS | 四轮逐文件 SHA 一致 |
| 专用多人服务器现场验收 | NOT VERIFIED | 本次为集成服务器网络验证 |
| 所有注册 Mod 模型及其他整合包 | NOT VERIFIED | 只测代表模型 |
| Redo、时间轴、动画、IK | NOT APPLICABLE | 不在静态摆拍范围 |

操作专项调用 Minecraft KeyboardHandler 的实际事件入口，使用实例 F4 启动，测试 F7 / Esc / F8 返回和临时 F10 / F11 / F12 提示变化；F10 返回、F11 Capture 动作实际执行，长按编辑键重复事件不会来回切换。前 / 后 / 左 / 右视角检查 Actor 相对朝向、焦点与 Pose 保留。玩家、马、TaCZ 靶车组三个世界轴均实际发送到集成服务器；其他两轴保持不变，全组位移相同，正面 Z 深度手柄可用，两次鼠标拖动事件只记一次撤回。12 次数值编辑保留最后 10 次，逐步撤回到第 2 次结果，第 11 次撤回不再修改模型。

核心回归按当前用户资源包配置执行。与上版验收时相比，用户现在关闭 FA+Player，保留 Fresh Animations 与 FA+All Extensions；没有为了通过检查重新启用旧包。验收读取原选项中的资源包列表并对照实际加载列表，玩家检查当前真实肢体网格和双层皮肤，生物仍检查 Fresh Animations 的已有结构。

代表模型包括狼、马、鸡、兔子、苦力怕、末影人、骷髅、TaCZ 靶车和 LrTactical 烟雾弹；内部节点不可访问的投掷物保留 Actor 编辑降级。八对象场景验证主渲染与 Capture，不声称全部 Mod 类型通用兼容或固定帧率。上限仍为默认 8、配置 5–10，玩家计入同维度共享预算。

中间失败保留但不算 PASS：`build/instance-check-20260927-023642-default` 发现 hideGui 下 Forge GUI Post 不触发，修复为原生 GUI 收尾提示；`build/instance-check-20260927-023950-default` 旧测试硬编码 FA+Player 为必选；`build/instance-check-20260927-024243-default` 资源包名的 § 字符通过 Java 参数文件出现编码差异，改用 UTF-8 期望列表文件；`build/instance-check-20260927-024507-default` 旧验收只记录 CEM 子网格，补齐原版肢体的相同诊断记录并按实际平面或 3D 体素外层验证，没有改动变形算法。最终四轮使用同一正式 JAR，结果及配置恢复记录见 `release/evidence/0.1.6/validation-summary.json`。

证据截图包括操作专项的自由摄影返回提示、无 UI Capture、四个标准视角与三个轴的多选场景；其他回归截图和每项检查位于相应 evidence 子目录。全部项目、构建、发行、备份和测试产物在 E 盘。
