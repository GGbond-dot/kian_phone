package com.kian.khup.core.classification

import com.kian.khup.core.data.db.entities.DerivedResult
import com.kian.khup.core.data.db.entities.Event
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleNotificationClassifier @Inject constructor() {

    fun classify(event: Event): DerivedResult {
        val title = event.title.orEmpty()
        val body = listOfNotNull(event.text, event.bigText, event.subText).joinToString(" ")
        val combined = "$title $body"
        val lower = combined.lowercase(Locale.ROOT)
        val packageName = event.packageName
        val channel = event.channelId.orEmpty().lowercase(Locale.ROOT)

        val classification = when {
            isVerification(combined) -> "验证码"
            packageName == "com.tencent.mm" -> classifyTencent(title, body, isWeChat = true)
            packageName == "com.tencent.mobileqq" -> classifyTencent(title, body, isWeChat = false)
            packageName in financePackages -> "消费信息"
            hasAny(combined, financeKeywords) -> "消费信息"
            packageName in algorithmPackages || hasAny(lower, algorithmKeywords) -> "算法推送"
            packageName in workPackages || hasAny(lower, workKeywords) -> "工作"
            hasAny(lower, promoKeywords) || hasAny(channel, promoKeywords) -> "推广"
            packageName in socialPackages -> "社交"
            else -> "其他"
        }

        return DerivedResult(
            eventId = event.eventId,
            classification = classification,
            priority = priorityFor(classification, lower),
            summary = buildSummary(event),
            entities = null,
            processedAt = System.currentTimeMillis(),
            modelVersion = MODEL_VERSION,
        )
    }

    private fun isVerification(text: String): Boolean =
        text.contains("验证码") && verificationCodeRegex.containsMatchIn(text)

    private fun classifyTencent(title: String, body: String, isWeChat: Boolean): String {
        val finMarkers = if (isWeChat) wechatFinanceMarkers else qqFinanceMarkers
        if (finMarkers.any { title.contains(it) || body.contains(it) }) return "消费信息"
        val promoMarkers = if (isWeChat) wechatPromoMarkers else qqPromoMarkers
        if (promoMarkers.any { title.contains(it) || body.contains(it) }) return "推广"
        return "社交"
    }

    private fun priorityFor(classification: String, text: String): Int =
        when {
            classification == "验证码" -> 3
            classification == "工作" && hasAny(text, urgentKeywords) -> 3
            classification == "工作" -> 2
            classification == "社交" -> 1
            classification == "消费信息" -> 1
            else -> 0
        }

    private fun buildSummary(event: Event): String {
        val title = event.title?.takeIf { it.isNotBlank() }
        val body = event.text?.takeIf { it.isNotBlank() }
        return when {
            title != null && body != null -> "$title：$body"
            title != null -> title
            body != null -> body
            else -> event.packageName
        }.take(48)
    }

    private fun hasAny(text: String, keywords: Set<String>): Boolean =
        keywords.any { text.contains(it) }

    companion object {
        const val MODEL_VERSION = "rules-v2"

        private val verificationCodeRegex = Regex("""\d{4,8}""")

        private val socialPackages = setOf(
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.instagram.android",
        )

        private val workPackages = setOf(
            "com.alibaba.android.rimet",
            "com.zmzx.college.search",
            "com.yiban.app",
            "com.nowcoder.app.florida",
        )

        private val algorithmPackages = setOf(
            "com.ss.android.ugc.aweme",
            "com.xingin.xhs",
            "com.zhihu.android",
            "com.sina.weibo",
            "com.kuaishou.nebula",
        )

        private val financePackages = setOf(
            "com.eg.android.AlipayGphone",
            "com.unionpay",
            "com.icbc",
            "com.icbc.im",
            "cmb.pb",
            "com.cmbchina.ccd.pluto.cmbActivity",
            "com.chinamworld.main",
            "com.android.bankabc",
            "com.chinamworld.bocmbci",
            "com.bankcomm.Bankcomm",
            "cn.com.spdb.mobilebank.per",
            "com.cmbc.cc.mbank",
        )

        private val wechatFinanceMarkers = setOf(
            "微信支付", "转账", "收款", "红包", "到账", "已退款", "退款",
        )

        private val wechatPromoMarkers = setOf(
            "订阅号消息", "订阅号", "公众号",
        )

        private val qqFinanceMarkers = setOf(
            "QQ钱包", "qq钱包", "转账", "红包", "到账", "已退款",
        )

        private val qqPromoMarkers = setOf(
            "QQ订阅", "公众号",
        )

        private val financeKeywords = setOf(
            "已入账", "信用卡", "账单", "还款", "余额变动", "消费提醒", "扣款", "支付成功",
        )

        private val promoKeywords = setOf(
            "广告",
            "推广",
            "优惠",
            "促销",
            "立减",
            "会员",
            "限时",
            "折扣",
            "直播",
            "上新",
        )

        private val workKeywords = setOf(
            "会议",
            "审批",
            "任务",
            "日程",
            "作业",
            "课程",
            "考试",
            "打卡",
            "签到",
            "ddl",
            "deadline",
        )

        private val algorithmKeywords = setOf(
            "推荐",
            "热榜",
            "热门",
            "为你",
            "精选",
            "可能感兴趣",
        )

        private val urgentKeywords = setOf(
            "紧急",
            "马上",
            "立即",
            "截止",
            "逾期",
            "deadline",
        )
    }
}
