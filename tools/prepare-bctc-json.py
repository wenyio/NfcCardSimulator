"""把用户提供的标准卡包转换为通用 EXACT/PREFIX 规则，不向应用加入专用执行逻辑。"""
import argparse
import copy
import json
from pathlib import Path


def convert(pack):
    result = copy.deepcopy(pack)
    for card in result['cards']:
        rules = card['rules']
        if card.get('ordered', False) or any(r.get('match', 'EXACT') != 'EXACT' for r in rules):
            raise ValueError(f"{card['id']}: 请使用完整匹配的原始卡包，避免重复转换")
        prefixes = []
        heads = {}
        gac = []
        for rule in rules:
            command = rule['command']
            head = command[:8] if len(command) == 8 else command[:10]
            heads.setdefault(head, []).append(rule)
            if command.startswith('80AE'):
                gac.append(rule)
        for head, candidates in heads.items():
            replies = {r['response'] for r in candidates}
            if len(replies) > 1 and head[:8] != '00A40400':
                raise ValueError(f"{card['id']}: {head} 存在多回复冲突，须人工拆分")
            for rule in candidates:
                prefix = head
                if len(replies) > 1:
                    # 同头 SELECT 用完整 AID 消歧义，允许 Le 不同或省略。
                    aid_chars = int(rule['command'][8:10], 16) * 2
                    if not 10 <= aid_chars <= 32 or len(rule['command']) < 10 + aid_chars:
                        raise ValueError(f"{card['id']}: 无法提取 SELECT AID")
                    prefix = rule['command'][:10 + aid_chars]
                entry = copy.deepcopy(rule)
                entry.update(command=prefix, match='PREFIX', label=rule.get('label', '') + ' · 命令头兼容')
                if not any(r['command'] == prefix and r['response'] == entry['response'] for r in prefixes):
                    prefixes.append(entry)
        if gac:
            if len({r['response'] for r in gac}) != 1 or gac[0]['response'] == '6D00':
                raise ValueError(f"{card['id']}: GENERATE AC 回退存在回复歧义，须人工核对")
            entry = copy.deepcopy(gac[0])
            entry.update(command='80AE', match='PREFIX', label='GENERATE AC · 前 2 字节回退')
            prefixes.append(entry)
        # 完整参考命令优先，兼容命令头其次，80AE 回退位于末尾。
        card['rules'] = rules + prefixes
        card['ordered'] = False
        card['fallback'] = '6D00'
        metadata = card.setdefault('metadata', {})
        metadata['warnings'] = [
            '完整命令优先；其余通过通用 PREFIX 规则兼容前 4/5 字节及 GENERATE AC 回退。',
            '同头 SELECT 按完整 AID 区分回复，避免原工具 HashMap 顺序导致歧义。',
            '回复均为原固定数据，不重算签名或密文；完整终端交易仍需实测。'
        ]
        metadata.setdefault('scenario', {})['conditions'] = '规则说明见 warnings；金额和原 PDOL/CDOL 仅供核对。'
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    source = json.loads(args.source.read_text(encoding='utf-8-sig'))
    output = convert(source)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f"{len(output['cards'])} 张卡，{sum(len(c['rules']) for c in output['cards'])} 条规则：{args.output}")
