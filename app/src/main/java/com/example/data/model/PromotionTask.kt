package com.example.data.model

enum class TaskPlatform {
    WHATSAPP_STATUS,
    INSTAGRAM_STORY,
    TELEGRAM_STORY,
    YOUTUBE_VIDEO,
    INSTAGRAM_REEL
}

enum class PromoStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}

data class MicroTaskSubmission(
    val id: String,
    val platform: TaskPlatform,
    val submittedAt: Long,
    val initialViewCount: Int,
    val finalViewCount: Int,
    val status: PromoStatus = PromoStatus.PENDING_REVIEW,
    val rewardUsdt: Double = 0.0,
    val notes: String = ""
)

data class VideoPromotionSubmission(
    val id: String,
    val platform: TaskPlatform,
    val videoUrl: String,
    val channelOrHandle: String,
    val submittedAt: Long,
    val estimatedViews: Int = 0,
    val status: PromoStatus = PromoStatus.PENDING_REVIEW,
    val rewardUsdt: Double = 0.0,
    val reviewerFeedback: String? = null
)
