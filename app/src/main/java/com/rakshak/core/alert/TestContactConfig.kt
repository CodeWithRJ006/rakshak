package com.rakshak.core.alert

/**
 * Singleton configuration for the Hackathon Test Contact.
 * This is used for physical device testing without hardcoding real numbers in the repo.
 */
object TestContactConfig {
    @Volatile
    var testContactNumber: String = "HACKATHON_TEST_CONTACT"
}
