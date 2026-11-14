import { MatchMode } from '../core/CardScript';

/** 仅统一界面文案；JSON 字段与引擎枚举保持不变。 */
export class MatchingLabels {
  /** 固定回复限制说明；不改变匹配配置。 */
  static readonly fixedReplyText: string = '回复仍为固定数据，不会重算密文或动态签名。';

  /** 按 ordered 读取顺序或内容模式名称；显示文字不作为持久化值。 */
  static execution(ordered: boolean): string {
    return ordered ? '顺序匹配' : '内容匹配';
  }

  /** 读取执行模式解释，帮助区分当前步骤匹配和首条内容匹配。 */
  static executionHelp(ordered: boolean): string {
    return ordered ? '只检查当前步骤，匹配成功后进入下一条；未匹配不推进，全部完成后返回未匹配回复。'
      : '每次从头查找第一条符合条件的规则，不要求按固定顺序发送命令。';
  }

  /** 将稳定 MatchMode 枚举映射为可显示名称。 */
  static mode(mode: MatchMode): string {
    if (mode === MatchMode.EXACT) {
      return '完整匹配';
    }
    if (mode === MatchMode.PREFIX) {
      return '前缀匹配';
    }
    return '掩码匹配';
  }

  /** 按匹配模式读取解释文案，枚举仍用于实际匹配和保存。 */
  static modeHelp(mode: MatchMode): string {
    if (mode === MatchMode.EXACT) {
      return '命令的每个字节和总长度都必须一致。';
    }
    if (mode === MatchMode.PREFIX) {
      return '只比较规则中填写的命令前缀，后续内容和长度可以不同。';
    }
    return '命令长度必须一致，按掩码逐位比较：FF 比较整个字节，00 忽略整个字节。';
  }

  /** 展示两种执行方式及边界的说明弹窗；查看说明不重置会话。 */
  static showExecutionHelp(): void {
    AlertDialog.show({
      title: '执行方式',
      message: `${MatchingLabels.execution(false)}\n${MatchingLabels.executionHelp(false)}\n\n` +
        `${MatchingLabels.execution(true)}\n${MatchingLabels.executionHelp(true)}\n\n` +
        '顺序匹配适合严格回放，也可让相同命令在不同步骤返回不同回复。会话重置后从第一条开始。',
      buttons: [{ value: '知道了', action: (): void => {} }]
    });
  }
}
