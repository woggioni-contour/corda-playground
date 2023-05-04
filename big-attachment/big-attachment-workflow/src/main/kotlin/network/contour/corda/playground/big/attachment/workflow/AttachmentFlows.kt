package network.contour.corda.playground.big.attachment.workflow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.internal.toPath
import net.corda.core.node.ServiceHub
import net.corda.core.utilities.toHexString
import net.corda.core.utilities.unwrap
import net.corda.node.services.persistence.NodeAttachmentService
import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest


object AttachmentFlows {

    enum class ChunkType(val value : Byte) {
        LAST(0), OTHER(1)
    }

    private class ChunkInputStream(
        buffer : ByteArray,
        offset : Int,
        size : Int,
       private val chunkType : ChunkType) : ByteArrayInputStream(buffer, offset, size) {

        private var index = 0

        override fun read(): Int {
            return if(index == 0) {
                ++index
                chunkType.value.toInt()
            } else {
                super.read()
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return if(index == 0 && len > 0) {
                ++index
                b[off] = chunkType.value
                return super.read(b, off + 1, len -1) + 1
            } else {
                super.read()
            }
        }
    }

    private val log = newLogger()

    private fun foo(md : MessageDigest, bytes : ByteArray, temporaryFile : String) {
        md.update(bytes, 1, bytes.size - 1)
        Files.newOutputStream(Paths.get(URI(temporaryFile)), StandardOpenOption.APPEND).use {
            it.write(bytes, 1, bytes.size - 1)
        }
    }

    @Suspendable
    private fun bar(serviceHub : ServiceHub, sender : FlowSession) : ByteArray {
        val temporaryFile = Files.createTempFile("contour", ".tmp").toUri().toString()
        return try {
            val fileName = sender.receive<Array<String>>().unwrap { it[0] }
            val md = MessageDigest.getInstance("MD5")
            while(true) {
                val bytes = sender.receive<ByteArray>().unwrap { it }
                log.trace("Received ${bytes.size} bytes chunk from ${sender.counterparty.name}")
                foo(md, bytes, temporaryFile)
                if(bytes[0] == ChunkType.LAST.value) break
            }
            val digest = md.digest()
            serviceHub.withEntityManager {
                val attachment = NodeAttachmentService.DBAttachment(
                        digest.toHexString(),
                        Files.readAllBytes(URI(temporaryFile).toPath()),
                        filename = fileName
                )
                persist(attachment)
                detach(attachment)
            }
            digest
        } finally {
            URI(temporaryFile).toPath()
                .takeIf { Files.exists(it) }
                ?.let(Files::delete)
        }
    }

    @Suspendable
    fun receiveAttachment(serviceHub : ServiceHub, sender : FlowSession) {
        val digest = bar(serviceHub, sender)
        sender.send(digest)
    }

    @Suspendable
    fun sendAttachment(destination : FlowSession, maxMessageSize : Int,
                       source : ByteArray, fileName : String?) : String {
        destination.send(arrayOf(fileName))
        val maxChunkSize = maxMessageSize - 1
        val chunks : Int = (source.size + maxChunkSize - 1) / maxChunkSize
        for(cursor in 0 until chunks) {
            val start = cursor * maxChunkSize
            val limit = (start + maxChunkSize).coerceAtMost(source.size)
            val chunkSize = limit - start
            val chunkType = if(cursor == chunks - 1) ChunkType.LAST else ChunkType.OTHER
            val chunk = ByteArray(chunkSize + 1)
            chunk[0] = chunkType.value
            for(i in 0 until chunkSize) {
                chunk[1 + i] = source[start + i]
            }
            log.trace("Sending $chunkSize bytes chunk to ${destination.counterparty.name}")
            destination.send(chunk)
        }
        return destination.receive<ByteArray>().unwrap(ByteArray::toHexString)
    }

    @InitiatingFlow
    @StartableByRPC
    class SendInitiator(private val destination : Party, private val source : ByteArray, private val filename : String?) : FlowLogic<String>() {

        @Suspendable
        override fun call() : String {
            val maxMessageSize = serviceHub.networkParameters.maxMessageSize
            log.info("Sending ${source.size} bytes attachment to ${destination.name}")
            val other = initiateFlow(destination)
            return sendAttachment(other, maxMessageSize, source, filename)
        }
    }

    @InitiatedBy(SendInitiator::class)
    class SendResponder(private val other : FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            receiveAttachment(serviceHub, other)
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class GetInitiator(private val source : Party, private val id : String) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            log.info("Retrieving attachment with id $id from ${source.name}")
            val session = initiateFlow(source)
            session.send(id)
            receiveAttachment(serviceHub, session)
        }
    }

    @InitiatedBy(GetInitiator::class)
    class GetResponder(private val other : FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val maxMessageSize = serviceHub.networkParameters.maxMessageSize
            val id = other.receive<String>().unwrap { it }
            val result = serviceHub.withEntityManager {
                find(NodeAttachmentService.DBAttachment::class.java, id)
            } ?: throw FlowException("Attachment with id '$id' not found")
            sendAttachment(other, maxMessageSize, result.content, result.filename)
        }
    }
}