package com.template.states

import com.template.contracts.AccountsDealContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty


@BelongsToContract(AccountsDealContract::class)
data class AccountDealState(val buyer: AnonymousParty, val seller: AnonymousParty, val broker: AnonymousParty, val deal: String): ContractState {

    override val participants: List<AbstractParty> = listOf(buyer,seller, broker)

}