package com.mavyy.localyuki.foundation.contracts

sealed interface FoundationResult<out T : Any> {
    data class Success<out T : Any>(val value: T) : FoundationResult<T>
    data class Unavailable(val reason: UnavailableReason) : FoundationResult<Nothing>
    data class Failure(val category: FailureCategory) : FoundationResult<Nothing>
}

enum class UnavailableReason { NOT_IMPLEMENTED, DEPENDENCY_UNAVAILABLE, CAPABILITY_UNAVAILABLE }
enum class FailureCategory { INVALID_INPUT, REJECTED, CONFLICT, INTERNAL_FAILURE }
