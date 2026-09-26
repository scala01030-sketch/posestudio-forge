# Pose Studio 0.1.5 验收记录

日期：2026-09-27。正式 JAR SHA-256：`BD30C2E96D0B918DEA6AE235E7667156464DB58F41AC8AE8E735CD3664B3544B`。

所有最终游戏验收使用同一份正式 JAR，且仅在指定实例 `E:\minecrate\.minecraft\versions\1.20.1-Forge_47.4.22 Vanilla-tacz` 内执行。保留该包模组、原资源包与光影设置；通过 PCL Forge profile 离线启动 StudioTest，使用新建测试世界。未打开用户原有世界，未使用真实账号或 PCL 图形界面登录。

| 验收层 | 状态 | 结果 |
|---|---|---|
| 构建与重混淆 | PASS | `studio.ps1 test build verificationJar --offline` |
| 单元测试 | PASS | 10 项 |
| 双语资源键与格式参数一致 | PASS | 122 个中英对应键 |
| 原皮肤、四个关节、骷髅头部、实体目录与核心回归 | PASS | 116 项实际游戏检查 |
| 启动批量冻结、拒绝超限、包含玩家的多选拖动与撤回 | PASS | 128 项实际游戏检查 |
| 多种代表模型、变化部位、真实显示资产及八对象场景 | PASS | 100 项实际游戏检查 |
| 原 options 与配置恢复、测试夹具和临时皮肤包清理 | PASS | 三轮原选项 SHA 与全部原配置文件 SHA 一致 |
| 专用服务器 / 多客户端现场验收 | NOT VERIFIED | 当前使用集成服务器验证网络与锁 |
| 任意外部骨骼库与全部 Mod 实体 | NOT VERIFIED | 仅代表模型，不声称通用适配 |
| 多客户端共享 Pose | NOT IMPLEMENTED | Pose 为本地摄影状态 |
| 时间轴、关键帧、播放、IK、自制渲染器 | NOT APPLICABLE | 本版范围明确排除 |

核心回归比较实际渲染的双层皮肤网格签名和四个肘膝动作；骷髅头部检查的是 Fresh Animations 的可见 skull 路径，而非空 head 标记。启动专项以主画面快照验证六个可见生物和第一人称玩家一起冻结，镜头后方对象排除，超限拒绝留下零个服务器锁。Actor 测试使用玩家、狼和马，包含左拖 / 右拖、XYZ 数值、整组相同位移、Pose 和 Camera 不变、一个撤回恢复全组、切换模式结束拖动及无效批次不部分移动。

代表模型：狼、马、鸡、兔子、苦力怕、末影人，另有核心回归中的骷髅。TaCZ 靶车验证已有 MinecartModel 部件编辑；LrTactical 烟雾弹验证原生 `ThrowableId` 显示资产、Actor 变换、冻结、撤回与移除，内部 ModelPart 编辑为明确降级。未测试每个注册类型，也未重新设计这些实体的骨骼。

同画面八对象验证使用玩家、狼、马、鸡、兔子、苦力怕、末影人和 TaCZ 靶车。第一次最终同画面断言失败，前后重叠的布局可能触发原包遮挡剔除；失败保留在 `build/instance-check-20260927-010042-default`，不记作 PASS。复测把八对象分开站位，保留 EntityCulling 和原光影，检查各自实际主渲染记录与 Capture 截图；正式模组 JAR 没有因此修改。复测状态见 `diverse/eight-actor-render-status.txt`。

默认预算 8、配置范围 5–10、玩家也计入，同维度共享。核心检查实际将服务端预算分别设为 5、8、10，确认冻结和新增对象合并计数，重复冻结不额外占位，超限被拒绝。普通未参与的背景实体继续按 Minecraft 正常渲染；这不是帧率保证。

正式实例只留 `posestudio-0.1.5.jar`。0.1.4 旧版备份保留在 `E:\PoseStudio\build\instance-check-20260927-000356-default\mods\posestudio-0.1.4.jar`，发行目录也保留旧版。新建 PoseAcceptance / PoseDiverse / PoseEntryMove 测试世界保留在原实例 saves 内，用户已有世界未打开。详细路径、哈希、选项恢复与各轮结果见 `evidence/0.1.5/validation-summary.json`。

最终证据：`core/acceptance.txt`、`entry-move/acceptance.txt`、`entry-move/group-left-drag.png`、`diverse/acceptance.txt`、`diverse/model-results.txt`、各模型 before / pose / capture 截图、八对象 Capture 截图、游戏日志、构建日志与测试 XML。中间失败日志仍在 E 盘 build，不作为通过证据。
