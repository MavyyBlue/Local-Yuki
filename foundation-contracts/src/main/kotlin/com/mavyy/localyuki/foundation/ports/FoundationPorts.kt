package com.mavyy.localyuki.foundation.ports

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.contracts.Proposal

/** Supply only bounded app-owned query/result DTOs to the reader. */
fun interface AuthorityReader<in Query : Any, Result : Any> {
    fun read(query: Query): FoundationResult<Result>
}

/** Only an appropriately owned domain implementation can be wired to this port. */
fun interface AuthorityMutator<in Command : Any, Result : Any> {
    fun mutate(command: Command): FoundationResult<Result>
}

/** Independent of AuthorityMutator; advisory output cannot execute itself. */
fun interface AdvisoryPort<in Input : Any, Output : Any> {
    fun propose(input: Input): FoundationResult<Proposal<Output>>
}
