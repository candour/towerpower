package com.messark.hawkerrush.model

data class Settings(
    val hapticEnabled: Boolean = true,
    val highScores: List<HighScore> = emptyList(),
    val showTutorials: Boolean = true,
    val shownTutorials: Set<String> = emptySet()
)
