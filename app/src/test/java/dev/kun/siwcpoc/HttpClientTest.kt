package dev.kun.siwcpoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.InvocationTargetException

class HttpClientTest {
    private fun read(stream: InputStream?): String {
        val method = HttpClient::class.java.getDeclaredMethod("readLimited", InputStream::class.java)
        method.isAccessible = true
        return method.invoke(HttpClient, stream) as String
    }

    @Test fun fragmentedUtf8BodyIsReadThroughEof() {
        val expected = "Xin chào từ Fold3! ".repeat(1200)
        val bytes = expected.toByteArray(Charsets.UTF_8)
        val stream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 7))
        }
        assertEquals(expected, read(stream))
        assertEquals(-1, stream.read())
    }

    @Test fun emptyBodyIsHandled() {
        assertEquals("", read(ByteArrayInputStream(byteArrayOf())))
        assertEquals("(empty response)", read(null))
    }

    @Test fun oversizedBodyIsRejectedWithoutEchoingIt() {
        try {
            read(ByteArrayInputStream(ByteArray(1_048_577) { 'x'.code.toByte() }))
            throw AssertionError("Oversized body was accepted")
        } catch (e: InvocationTargetException) {
            assertTrue(e.cause is IllegalArgumentException)
            assertEquals("HTTP response exceeds size limit", e.cause?.message)
        }
    }
}
