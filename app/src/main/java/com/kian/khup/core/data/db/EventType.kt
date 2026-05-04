package com.kian.khup.core.data.db

/** 事件类型枚举（架构文档 §4）。Room 通过 TypeConverter 序列化为字符串。 */
enum class EventType {
    NOTIFICATION_POSTED,      // 通知到达
    NOTIFICATION_REMOVED,     // 通知被划掉/撤回
    APP_FOREGROUND_START,     // 进入前台
    APP_FOREGROUND_END,       // 离开前台
    APP_OPEN_COUNT,           // 打开次数（聚合）
    DINGTALK_TODO,
    DINGTALK_MEETING,
    CHAOXING_HOMEWORK,
    CHAOXING_SIGN_IN,
    CHAOXING_EXAM,
}
