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
  # dex 的字符串表是明文 UTF-8，-a 让 grep 把二进制当文本处理
  if cat "$WORK"/classes*.dex | grep -a -q -F "$symbol"; then
    echo "  OK   $symbol"
  else
    echo "  MISS $symbol"
    FAILED=1
  fi
done

if [ "$FAILED" -ne 0 ]; then
  echo "::error::release 包中缺少必需类，R8 keep 规则未生效"
  exit 1
fi

# 顺带确认日志确实被裁掉了（-assumenosideeffects 生效），
# 否则 release 里残留调试日志，属于信息泄漏面
if cat "$WORK"/classes*.dex | grep -a -q -F 'ML Kit process failed'; then
  echo "  note: 仍能找到被 Log.e 使用的字符串（这是预期的：Log.e 被保留）"
fi

echo "release 包关键类检查通过"
