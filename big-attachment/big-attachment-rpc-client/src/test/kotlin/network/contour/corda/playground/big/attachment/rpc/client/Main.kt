package network.contour.corda.playground.big.attachment.rpc.client

import net.corda.core.internal.readFully
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class InputStreamTest {

    @Test
    fun foo() {
        val limit = 0x40_000_000
        LimitedInputStream(RandomInputStream(), limit).use {
            Assertions.assertEquals(limit, it.readFully().size)
        }
    }
}