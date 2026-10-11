package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class CrashJournalTest {
    @Test fun reportExposesSourceFramesWithoutPrivateExceptionMessages() {
        val e = IllegalStateException("secret-key-from-device and private dictated sentence")
        e.stackTrace = arrayOf(StackTraceElement(
            "io.github.issisx.alienflow.RealtimeTransport",
            "openSocket", "RealtimeTransport.kt", 78))
        val report = CrashReportFormatter.render("websocket.failure", e)
        assertTrue(report.contains("websocket.failure"))
        assertTrue(report.contains("RealtimeTransport.kt:78"))
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertFalse(report.contains("secret-key"))
        assertFalse(report.contains("private dictated"))
    }

    @Test fun reportHandlesNonAppFramesWithoutIncludingExceptionMessages() {
        val e = RuntimeException("Authorization: Bearer should-never-appear")
        e.stackTrace = arrayOf(StackTraceElement("android.os.Handler", "dispatchMessage", "Handler.java", 99))
        val report = CrashReportFormatter.render("service.promoting", e)
        assertTrue(report.contains("Handler.java:99"))
        assertFalse(report.contains("Bearer"))
        assertFalse(report.contains("should-never-appear"))
    }
}
