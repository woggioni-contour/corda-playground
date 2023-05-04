package network.contour.corda.playground.big.attachment.workflow

import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.newLogger() : Logger = LoggerFactory.getLogger(T::class.java)
