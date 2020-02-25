package com.template.contracts;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

public class AccountsDealContract implements Contract {
    // Used to identify our contract when building a transaction.
    public static String ID = "com.template.contracts.AccountsDealContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

    }

    public interface Commands extends CommandData {
        class CreateDeal implements Commands {}
    }
}
