package com.kian.khup.core.ai

/**
 * API 通道发出 prompt 前的正则脱敏层。
 * 只覆盖结构化敏感字段,聊天内容 / 好友名等非结构化数据需要业务层用 ID 化处理。
 */
object PromptRedactor {

    private val rules: List<Pair<Regex, String>> = listOf(
        // 验证码上下文优先,避免里面的 4-8 位数字被其他规则误命中
        Regex("(验证码|校验码|动态码|verification code|otp)[^\\d]{0,8}(\\d{4,8})", RegexOption.IGNORE_CASE)
            to "$1 [验证码]",
        // 邮箱
        Regex("\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b") to "[邮箱]",
        // 身份证(18 位,末位可能是 X)
        Regex("(?<![\\dXx])\\d{17}[\\dXx](?![\\dXx])") to "[身份证]",
        // 手机号(中国大陆 11 位,1 开头)
        Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)") to "[手机号]",
        // IPv4
        Regex("(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)") to "[IP]",
    )

    fun redact(text: String): String =
        rules.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }
}
