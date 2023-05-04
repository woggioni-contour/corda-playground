package network.contour.corda.playground.big.attachment.workflow

import net.corda.core.crypto.SecureHash
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import org.hibernate.annotations.NamedQueries
import org.hibernate.annotations.NamedQuery
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Lob
import javax.persistence.ManyToOne
import javax.persistence.Table
import javax.persistence.Transient

const val CONTOUR_DATABASE_PREFIX = "contour_"

object ContourAttachmentSchema {
    const val attachmentEntityName = "ContourAttachment"
    const val attachmentChunkEntityName = "ContourAttachmentChunk"
}

object ContourAttachmentSchemaV1 : MappedSchema(
        schemaFamily = ContourAttachmentSchema.javaClass,
        version = 1,
        mappedTypes = listOf(ContourAttachment::class.java, ContourAttachment.Chunk::class.java)
) {
    override val migrationResource = "contour-attachment.changelog-master"

    const val attachmentByHash = "attachmentByHash"
    const val chunksByAttachmentId = "chunksByAttachmentId"
}

@Entity(name = ContourAttachmentSchema.attachmentEntityName)
@Table(
    name = "${CONTOUR_DATABASE_PREFIX}attachment",
    indexes = [
        Index(unique = true, columnList = "hash_algorithm,hash_bytes")
    ]
)
@NamedQueries(
    NamedQuery(
        name = ContourAttachmentSchemaV1.attachmentByHash,
        query = "SELECT attachment FROM ${ContourAttachmentSchema.attachmentEntityName} attachment " +
                "WHERE attachment.hash_algorithm = $1 AND attachment.hash_bytes = ?2"
    )
)
@CordaSerializable
data class ContourAttachment(
    @Id
    @GeneratedValue
    val id: Int,

    @Column(name = "hash_algorithm", updatable = false, nullable = false)
    val algorithm: String,

    @Column(name = "hash_bytes", updatable = false, nullable = false)
    val bytes : ByteArray,

    @Column(name = "filename", updatable = false, nullable = true)
    val filename: String? = null,

    @Column(name = "complete", updatable = true, nullable = false)
    var complete: Boolean = false
) {
    @Transient
    val secureHash = SecureHash.HASH(algorithm, bytes)

    @Entity(name = ContourAttachmentSchema.attachmentChunkEntityName)
    @Table(name = "${CONTOUR_DATABASE_PREFIX}attachment_chunk")
    @NamedQueries(
        NamedQuery(
            name = ContourAttachmentSchemaV1.chunksByAttachmentId,
            query = "SELECT chunk FROM ${ContourAttachmentSchema.attachmentChunkEntityName} chunk " +
                    "WHERE chunk.attachment.id = $1"
        )
    )
    @CordaSerializable
    data class Chunk(
        @Id
        @GeneratedValue
        @Column(name = "id")
        val id : Int,

        @Lob
        @Column(name = "data", nullable = false)
        val data : ByteArray,

        @ManyToOne(fetch = FetchType.EAGER)
        val attachment: ContourAttachment
    )
}
