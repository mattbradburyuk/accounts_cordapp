package com.template.contracts

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction


class AccountsDealContract: Contract {

    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.AccountsDealContract"
    }

    interface Commands : CommandData {
        class CreateDeal : Commands
    }


    override fun verify(tx: LedgerTransaction) {

    }


}