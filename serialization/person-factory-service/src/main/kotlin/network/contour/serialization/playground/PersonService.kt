package network.contour.serialization.playground

interface Person {
    val name: String
    val surname: String
}

interface PersonService {
    fun create() : Person
}