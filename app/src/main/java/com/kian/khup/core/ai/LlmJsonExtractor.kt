package com.kian.khup.core.ai

/**
 * 从 LLM 输出中提取首个完整 JSON 对象。
 *
 * 容错：
 * - 去掉 ```json ... ``` 等 markdown 包裹
 * - 跳过开头的解释性文字，定位到第一个 `{`
 * - 扫描配对的 `}`，正确忽略字符串字面量内的括号与转义字符
 *
 * 失败返回 null（让 Generator 走 fallback，不暴露给用户）。
 */
internal object LlmJsonExtractor {

    fun extract(raw: String): String? {
        if (raw.isBlank()) return null
        val cleaned = raw
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")
        val start = cleaned.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escape = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (escape) {
                escape = false
                continue
            }
            if (inString) {
                when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return cleaned.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
