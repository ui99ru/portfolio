package com.tinvestlite.data

/** Lightweight result wrapper so the UI can render loading/error/data states. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data
