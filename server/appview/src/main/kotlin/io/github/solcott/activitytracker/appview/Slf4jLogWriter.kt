package io.github.solcott.activitytracker.appview

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import org.slf4j.LoggerFactory

/** Sends Kermit logs from the shared KMP modules to SLF4J, so they reach Logback under their tag as the logger name. */
class Slf4jLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val logger = LoggerFactory.getLogger(tag.ifEmpty { DEFAULT_LOGGER })
        when (severity) {
            Severity.Verbose -> logger.trace(message, throwable)
            Severity.Debug -> logger.debug(message, throwable)
            Severity.Info -> logger.info(message, throwable)
            Severity.Warn -> logger.warn(message, throwable)
            Severity.Error,
            Severity.Assert -> logger.error(message, throwable)
        }
    }

    private companion object {
        const val DEFAULT_LOGGER = "kermit"
    }
}
