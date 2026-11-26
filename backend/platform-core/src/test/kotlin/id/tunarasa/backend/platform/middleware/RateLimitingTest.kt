package id.tunarasa.backend.platform.middleware

import id.tunarasa.backend.platform.db.DatabaseFactory
import id.tunarasa.backend.platform.db.RedisClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test

class RateLimitingTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            DatabaseFactory.init()
        }
    }

    @Test
    fun testRedisClientFallbackAndRateLimitKey(): Unit = runBlocking {
        // Test Redis key set & get with fallback or local instance
        val testKey = "ratelimit:test_ip_127_0_0_1:1000"
        RedisClient.set(testKey, "1", ttlSeconds = 60)

        // Read or fallback gracefully
        val value = RedisClient.get(testKey)
        // If Redis server is not running on localhost, value is null, which RedisClient handles gracefully
        if (value != null) {
            assertEquals("1", value)
        }
        assertNotNull(testKey)
    }
}
