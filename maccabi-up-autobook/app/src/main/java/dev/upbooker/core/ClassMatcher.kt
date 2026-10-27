package dev.upbooker.core

import java.time.LocalDate
import java.time.LocalTime

object ClassMatcher {
    fun matches(gymClass: GymClass, config: BookingConfig): Boolean =
        gymClass.name.contains(config.activityKeyword, ignoreCase = true) &&
            (config.studioKeyword.isBlank() || gymClass.studio.contains(config.studioKeyword, ignoreCase = true))

    fun find(classes: List<GymClass>, config: BookingConfig, date: LocalDate, time: LocalTime): GymClass? =
        classes.firstOrNull {
            matches(it, config) && it.start.toLocalDate() == date && it.start.toLocalTime() == time
        }
}
