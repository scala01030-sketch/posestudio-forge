# v0.1.4：FA+Player / EMF 子模型、关节操作与单次撤回

日期：2026-09-26。所有工程、工具链、构建、备份和证据在 E 盘。

## 问题与复现

指定实例：`E:\minecrate\.minecraft\versions\1.20.1-Forge_47.4.22 Vanilla-tacz`。

已安装 JAR 哈希与正式 0.1.3 一致，问题不是旧版本。实际资源包包括 FA+All_Extensions 1.8.1、FreshAnimations 1.10.4、FA+Player 1.1、CleanGlass 和 Continuity。上一版只有完整 JAR 列表与必要渲染配置的测试，没有加载这套玩家 CEM 资源模型，不能覆盖用户的实际情况。

复现时基础肢体是无 cube 的 EMFModelPartVanilla，实际几何位于 EMFModelPartCustom 子节点。四个关节已显示，但实际弯曲顶点数为 0。旧变形只处理肢体自身 cube，遗漏了子节点；EMF 也覆写了 compile，绕过普通 ModelPart.compile 挂钩。EMF 动画与外层独立缩放还可能继续改变两层相对位置。

## 修改

- 按 ModelPart 身份传递肢体归属，子节点的几何使用该肢体的共享连续弯曲。
- 可选 EMF compile 桥接保留原 CEM 几何、纹理、UV 和材质，不替换模型。
- 冻结期间暂停当前角色的 EMF animate；底层和外层共享位置、旋转及缩放快照，临时字段正常恢复。
- 小节点优先于交叉旋转环；旋转平面侧向时支持屏幕拖动，避免肘膝 X 环从正面无法操作。
- 默认隐藏辅助连线、Pose 包围框和玩家 CEM 内部节点，只保留主要关节与当前轴的旋转环。右侧 X / Y / Z 按钮选轴，顶部“辅助”可展开完整显示。
- 肘膝不再显示不可编辑的局部位置框。
- 顶部“撤回”和 Ctrl+Z 撤回最后一次调整，整段拖动只占一次，不增加动画系统或多步历史。

## 实际实例测试

`tools/instance-smoke.ps1` 使用原实例目录和 Forge 启动配置，离线玩家，不读取账号登录凭据。仅新建 PoseAcceptance 世界，没有打开用户已有存档。

先以双色双层皮肤测试，再使用实例已保存的 `ETF_player_skin_printout.png`。测试皮肤由独立 verification-fixtures 夹具提供，保存图通过临时资源包覆盖夹具贴图；正式 JAR 不含夹具，也不替换用户皮肤。两轮各通过 98 项检查，10 项单元测试通过。

针对四个关节分别验证真实节点点击、数值应用、底层及外层网格变化、数值撤回、正面 X 环拖动、多个拖动事件的一次撤回以及渲染网格恢复。六个外层的矩阵最大误差均为 0，实际弯曲网格合计 4,160 个顶点。

原 options 和 config 文件恢复且哈希一致，测试 JAR 和临时资源包移出，原实例只保留 0.1.4 正式 JAR。原 0.1.3 备份在 `E:\PoseStudio\build\instance-check-20260926-195726-default\mods`。

测试证据：`release/evidence/0.1.4/actual-instance-checker` 与 `actual-instance-saved-skin`。新建测试世界留在实例 saves，便于需要时查看。测试后姿势会恢复，测试世界不是持久场景保存。

本次没有用 PCL 图形界面登录或真实账号认证；直接按其实例 Forge 配置离线启动。游戏测试全部来自同一指定包。其他包、任意 CEM 资源模型及未知独立渲染库未宣称通过。类似未知模型仍通过可访问层级适配。
