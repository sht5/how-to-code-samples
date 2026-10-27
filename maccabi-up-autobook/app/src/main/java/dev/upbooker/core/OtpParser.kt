package dev.upbooker.core

object OtpParser {
    private val CODE = Regex("""(?<!\d)(\d{4,8})(?!\d)""")

    /**
     * Returns the login code if the SMS looks like it came from Maccabi, i.e. [keyword]
     * appears in the sender name or the body.
     */
    fun extract(sender: String?, body: String, keyword: String): String? {
        val fromMaccabi = keyword.isNotBlank() &&
            ((sender?.contains(keyword, ignoreCase = true) ?: false) ||
                body.contains(keyword, ignoreCase = true))
        if (!fromMaccabi) return null
        return CODE.find(body)?.groupValues?.get(1)
    }
}
