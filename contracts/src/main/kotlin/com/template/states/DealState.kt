package com.template.states

import com.template.contracts.DealContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party

@BelongsToContract(DealContract::class)
data class DealState(val buyer: Party, val seller: Party, val deal: String): ContractState{

    override val participants: List<AbstractParty> = listOf(buyer,seller)

}