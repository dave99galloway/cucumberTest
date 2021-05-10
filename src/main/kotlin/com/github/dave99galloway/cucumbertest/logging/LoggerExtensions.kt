package com.github.dave99galloway.cucumbertest.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.bridge.SLF4JBridgeHandler
import kotlin.reflect.full.companionObject

/**
 * based on https://www.baeldung.com/kotlin/logging
 */
@Suppress("unused")
inline fun <reified T> T.logger(): Lazy<Logger> {
    return lazy {
        getLogger(getClassForLogging(T::class.java))
    }
}

inline fun <reified T> getClassForLogging(javaClass: Class<T>): Class<*> {
    return javaClass.enclosingClass?.takeIf {
        it.kotlin.companionObject?.java == javaClass
    } ?: javaClass
}

/**
 * https://stackoverflow.com/questions/9117030/jul-to-slf4j-bridge
 */
fun resetLoggers() {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
}