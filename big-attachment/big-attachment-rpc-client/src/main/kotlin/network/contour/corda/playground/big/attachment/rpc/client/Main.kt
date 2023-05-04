package network.contour.corda.playground.big.attachment.rpc.client

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.readFully
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.toHexString
import network.contour.corda.playground.big.attachment.workflow.AttachmentFlows
import network.contour.corda.playground.big.attachment.workflow.newLogger
import java.io.FilterInputStream
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.SecureRandom

class RandomInputStream(seed : ByteArray? = null) : InputStream() {
    private val random = SecureRandom.getInstance("NativePRNGNonBlocking").apply {
        seed?.let(::setSeed)
    }
    override fun read() = random.nextInt() and Int.MAX_VALUE

    override fun read(b: ByteArray): Int {
        random.nextBytes(b)
        return b.size
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        for(i in off until off + len) {
            b[i] = random.nextInt().toByte()
        }
        return len
    }
}


class LimitedInputStream(source : InputStream, private val limit : Int) : FilterInputStream(source) {
    private var count : Int = 0

    override fun read(): Int {
        return if(count == limit) -1
        else {
            ++count
            super.read()
        }
    }

    override fun read(b: ByteArray): Int {
        return if(count == limit) -1
        else {
            val toBeRead = (limit - count).coerceAtMost(b.size)
            super.read(b, 0, toBeRead).also {
                count += it
            }
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return if(count == limit) -1
        else {
            val toBeRead = (limit - count).coerceAtMost(b.size - off).coerceAtMost(len)
            super.read(b, off, toBeRead).also {
                count += it
            }
        }
    }
}

object Main {

    private val log = newLogger()

    @JvmStatic
    fun main(vararg args : String) {
        val hostAndPort = NetworkHostAndPort("localhost", 10006)
        log.debug("Trying to connect to $hostAndPort")
        val client = CordaRPCClient(hostAndPort)
        val md = MessageDigest.getInstance("MD5")
        client.start("admin", "password").use { conn ->
            val proxy = conn.proxy
            val destination = CordaX500Name.parse("O=Bob,L=New York,C=US")
            val destinationParty = proxy.networkMapSnapshot().find {
                it.legalIdentities.first().name == destination
            }?.let { it.legalIdentities.first() } ?: throw RuntimeException()
            val size = 0x1000
            val flowHandle = DigestInputStream(LimitedInputStream(RandomInputStream(), size), md).use { inputStream ->
                log.debug("Sending attachment with random bytes")
                proxy.startFlow(
                        AttachmentFlows::SendInitiator,
                        destinationParty,
                        inputStream.readFully(),
                        null
                )
            }
            val digest = flowHandle.returnValue.get()
            val hashOfSentData = md.digest().toHexString()
            if(digest != hashOfSentData) throw RuntimeException("Hash mismatch: $digest != $hashOfSentData")
            proxy.startFlow(AttachmentFlows::GetInitiator, destinationParty, digest).returnValue.get()
        }
    }
}