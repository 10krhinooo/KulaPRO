package com.example.kulapro.data.repository

/**
 * Outcome of a repository call.
 *
 * Repositories return this rather than throwing, so every caller is forced by the compiler to
 * consider the failure branch. The first version dropped errors entirely: a failed write
 * showed a success toast and navigated away regardless.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val message: String, val cause: Throwable? = null) : Result<Nothing>
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onFailure(action: (String) -> Unit): Result<T> {
    if (this is Result.Failure) action(message)
    return this
}
