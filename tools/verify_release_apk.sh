#!/usr/bin/env bash
# 校验 release APK 里关键类没有被 R8 裁掉。
#
# 为什么需要这个：R8 把 kotlinx.serialization 生成的 serializer 裁掉时，
# 编译、打包、签名全部成功——只有运行时第一次读写历史才会崩。
# CI 没有设备，跑不了 instrumented test，但 dex 里的类名是明文字符串，
# 可以直接查。这不是完整的运行时验证，但能挡住最常见的一类裁剪事故。
#
# 用法：verify_release_apk.sh <apk 路径>
set -euo pipefail

APK="${1:?需要 APK 路径}"
if [ ! -f "$APK" ]; then
  echo "::error::APK 不存在：$APK"
  exit 1
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

unzip -o -q "$APK" 'classes*.dex' -d "$WORK"

DEX_COUNT=$(find "$WORK" -name 'classes*.dex' | wc -l)
if [ "$DEX_COUNT" -eq 0 ]; then
  echo "::error::APK 中找不到 classes*.dex"
  exit 1
fi
echo "共 $DEX_COUNT 个 dex"

# 逐个检查：这些类的名字一旦消失，说明 keep 规则没生效
REQUIRED=(
  'HistoryEntry$$serializer'
  'ReadingLayout'
  'TranslateStyle'
  'ModelOption'
  'com.papertong.app.MainActivity'
)

FAILED=0
for symbol in "${REQUIRED[@]}"; do
  # dex 的字符串表是明文 UTF-8，-a 让 grep 把二进制当文本处理。
  # 不要写成 cat ... | grep -q：grep -q 匹配到就退出，cat 收到 SIGPIPE
  # 返回 141，在 pipefail 下整条管道被判失败，明明找到了也会报 MISS。
  match=$(grep -a -o -F "$symbol" "$WORK"/classes*.dex | head -n 1 || true)
  if [ -n "$match" ]; then
    echo "  OK   $symbol"
  else
    echo "  MISS $symbol"
    FAILED=1
  fi
done

# 失败时把 dex 里 papertong 相关类名打印出来，便于判断是"
# 被裁掉"还是"被混淆成短名"——这两者的修法完全不同
if [ "$FAILED" -ne 0 ]; then
  echo "--- 含 papertong 的字符串样本 ---"
  grep -a -o -E '[A-Za-z0-9_./$]*[Pp]aper[Tt]ong[A-Za-z0-9_./$]*' "$WORK"/classes*.dex \
    | sort -u | head -n 60 || true
  echo "--- serializer 相关字符串样本 ---"
  grep -a -o -E '[A-Za-z0-9_./$]*serializer[A-Za-z0-9_./$]*' "$WORK"/classes*.dex \
    | sort -u | head -n 40 || true
fi

if [ "$FAILED" -ne 0 ]; then
  echo "::error::release 包中缺少必需类，R8 keep 规则未生效"
  exit 1
fi

# Log.e 被刻意保留，这里只作提示，不作为失败条件
if grep -a -q -F 'ML Kit process failed' "$WORK"/classes*.dex; then
  echo "  note: Log.e 的字符串仍在（预期：release 保留 Log.w/Log.e 便于排查）"
fi

echo "release 包关键类检查通过"
