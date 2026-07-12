package com.bittokazi.ktor.gateway.common

sealed class CallResult<T, E> {
    data class Success<T, E>(
        val outcome: T,
    ) : CallResult<T, E>()

    data class Failure<T, E>(
        val errorCode: E,
        val errorMessage: String = "",
        val cause: Throwable? = null,
    ) : CallResult<T, E>()
}
