package network.contour.serialization.playground

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Department(val name : String)

@CordaSerializable
data class PersonImpl(override val name: String, override val surname: String, val department : Department?) : Person

class PersonServiceImpl : PersonService {
    override fun create() = PersonImpl("John", "Doe", Department("Engineering"))
}
