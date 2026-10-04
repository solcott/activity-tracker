package io.github.solcott.activitytracker.appview

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger as LogbackLogger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import co.touchlab.kermit.Logger
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.slf4j.LoggerFactory

class Slf4jLogWriterTest {
    private val logback = LoggerFactory.getLogger(TAG) as LogbackLogger
    private val appender = ListAppender<ILoggingEvent>().apply { start() }
    private val kermit = Logger(loggerConfigInit(Slf4jLogWriter()), TAG)

    init {
        logback.level = Level.TRACE
        logback.addAppender(appender)
    }

    @AfterTest
    fun cleanUp() {
        logback.detachAppender(appender)
    }

    @Test
    fun `kermit logs reach logback under the tag with the mapped level`() {
        val error = IllegalStateException("boom")

        kermit.v { "verbose" }
        kermit.d { "debug" }
        kermit.i { "info" }
        kermit.w(error) { "warn" }
        kermit.e { "error" }
        kermit.a { "assert" }

        assertEquals(
            listOf(
                Level.TRACE to "verbose",
                Level.DEBUG to "debug",
                Level.INFO to "info",
                Level.WARN to "warn",
                Level.ERROR to "error",
                Level.ERROR to "assert",
            ),
            appender.list.map { it.level to it.formattedMessage },
        )
        assertEquals(TAG, appender.list.first().loggerName)
        assertEquals("boom", appender.list.single { it.level == Level.WARN }.throwableProxy.message)
    }

    private companion object {
        const val TAG = "Slf4jLogWriterTest"
    }
}
