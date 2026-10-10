package io.github.issisx.alienflow

import org.junit.Assert.*
import org.junit.Test

class RealtimeCapturePolicyTest {
    @Test fun dictationUsesOnePeerOwnedMicrophoneDemandWithoutHardwareCallEffects() {
        assertFalse(RealtimeCapturePolicy.USE_INDEPENDENT_CLIENT_RECORDING_DEMAND)
        assertFalse(RealtimeCapturePolicy.USE_HARDWARE_ACOUSTIC_ECHO_CANCELER)
        assertFalse(RealtimeCapturePolicy.USE_HARDWARE_NOISE_SUPPRESSOR)
    }
}
