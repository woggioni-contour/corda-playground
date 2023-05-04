package network.contour.serialization.playground

import net.corda.core.internal.JDK1_2_CLASS_FILE_FORMAT_MAJOR_VERSION
import net.corda.core.internal.JDK8_CLASS_FILE_FORMAT_MAJOR_VERSION
import net.corda.core.internal.createInstancesOfClassesImplementing
import net.corda.core.internal.readFully
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationCustomSerializer
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.serialization.withWhitelist
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.amqpMagic
import java.nio.file.Files
import java.nio.file.Paths
import java.util.ServiceLoader

private class AMQPParametersSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
    override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
        return magic == amqpMagic && target == SerializationContext.UseCase.P2P
    }
}


private fun serializeData() {
    _contextSerializationEnv.set(SerializationEnvironment.with(
        SerializationFactoryImpl().apply {
            registerScheme(AMQPParametersSerializationScheme())
        },
        AMQP_P2P_CONTEXT.copy(preventDataLoss = true))
    )
    val personService = ServiceLoader.load(PersonService::class.java).first()
    System.getProperty("serialized.data.file").let {
        Paths.get(it)
    }.let {
        Files.newOutputStream(it)
    }.use { outputStream ->
        outputStream.write(personService.create().serialize().bytes)
    }
}

private fun deserializeData() {
    _contextSerializationEnv.set(SerializationEnvironment.with(
        SerializationFactoryImpl().apply {
            registerScheme(AMQPParametersSerializationScheme())
        },
        AMQP_P2P_CONTEXT.copy(preventDataLoss = true))
    )
    val serializers = try {
        createInstancesOfClassesImplementing(ClassLoader.getSystemClassLoader(), SerializationCustomSerializer::class.java,
            JDK1_2_CLASS_FILE_FORMAT_MAJOR_VERSION..JDK8_CLASS_FILE_FORMAT_MAJOR_VERSION)
    } catch (ex: UnsupportedClassVersionError) {
        throw RuntimeException()
    }
    val whitelistedClasses = ServiceLoader.load(SerializationWhitelist::class.java, ClassLoader.getSystemClassLoader())
        .flatMap(SerializationWhitelist::whitelist)
    val serializationContext = SerializationFactory.defaultFactory.defaultContext
        .withPreventDataLoss()
        .withClassLoader(ClassLoader.getSystemClassLoader())
        .withWhitelist(whitelistedClasses)
        .withCustomSerializers(serializers)
        .withoutCarpenter()
    System.getProperty("serialized.data.file").let {
        Paths.get(it)
    }.let {
        Files.newInputStream(it)
    }.use { inputStream ->
        val person = inputStream.readFully().deserialize<Person>(context = serializationContext)
        println(person)
    }
}

fun main(vararg args: String) {

    for(arg in args) {
        when(arg) {
            "serialize" -> serializeData()
            "deserialize" -> deserializeData()
            else -> throw RuntimeException("Unsupported command '$arg'")
        }
    }


}