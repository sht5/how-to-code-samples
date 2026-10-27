package dev.upbooker.core

import java.time.LocalDate

/**
 * Used by "Dry run": exercises the whole flow (alarm, SMS capture, retries,
 * notifications) without talking to Maccabi. To finish the login, text this
 * phone a message containing the SMS keyword and a code, e.g. "מכבי 123456".
 */
class FakeMaccabiClient(
    private val config: BookingConfig,
    private val log: (String) -> Unit,
) : MaccabiClient {
    override suspend fun requestOtp(idNumber: String) {
        log("DRY RUN: text this phone \"${config.smsKeyword} 123456\" from another phone")
    }

    override suspend fun verifyOtp(idNumber: String, code: String) = Session("dry-run")

    override suspend fun findClasses(session: Session, date: LocalDate) = listOf(
        GymClass(
            id = "dry-run",
            name = "${config.activityKeyword} (dry run)",
            studio = config.studioKeyword.ifBlank { "Studio" },
            start = date.atTime(config.classTime),
        )
    )

    override suspend fun myBookings(session: Session) = emptyList<GymClass>()

    override suspend fun book(session: Session, gymClass: GymClass) = BookResult.Booked
}
