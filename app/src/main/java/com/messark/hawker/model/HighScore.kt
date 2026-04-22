package com.messark.hawker.model

data class HighScore(
    val score: Int,
    val wave: Int,
    val date: String // ISO8601 string
)
