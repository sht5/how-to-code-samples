package dev.upbooker.core

import okhttp3.OkHttpClient
import java.time.LocalDate

class ApiNotMappedException(what: String) :
    Exception("$what is not implemented yet: map the upapp API first (docs/CAPTURE.md)")

/**
 * The real upapp client. Each method needs the endpoint, headers and body
 * recorded in step 1 (docs/CAPTURE.md). Until then every call fails with a clear
 * message, so a scheduled run notifies you instead of doing anything unexpected.
 */
class MaccabiApiClient(
    @Suppress("unused") private val http: OkHttpClient = OkHttpClient(),
) : MaccabiClient {
    override suspend fun requestOtp(idNumber: String) = throw ApiNotMappedException("Requesting the login SMS")

    override suspend fun verifyOtp(idNumber: String, code: String): Session =
        throw ApiNotMappedException("Verifying the SMS code")

    override suspend fun findClasses(session: Session, date: LocalDate): List<GymClass> =
        throw ApiNotMappedException("Listing classes")

    override suspend fun myBookings(session: Session): List<GymClass> =
        throw ApiNotMappedException("Listing your bookings")

    override suspend fun book(session: Session, gymClass: GymClass): BookResult =
        throw ApiNotMappedException("Booking a class")
}
