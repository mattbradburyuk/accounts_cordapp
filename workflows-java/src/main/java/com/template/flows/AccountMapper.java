package com.template.flows;

import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import net.corda.core.flows.FlowSession;
import net.corda.core.identity.AnonymousParty;

/**
 * Convenient class to wrap up the data related to one actor:
 *  - AccountInfo,
 *  - Specific key being used, and
 *  - Session to talk to the host.
 */
public class AccountMapper {
    private final AccountInfo accountInfo;
    private final AnonymousParty anonParty;
    private FlowSession sessionToHost = null;

    public AccountMapper(AccountInfo accountInfo, AnonymousParty anonParty) {
        this.accountInfo = accountInfo;
        this.anonParty = anonParty;
    }

    public AccountInfo getAccountInfo() {
        return accountInfo;
    }

    public AnonymousParty getAnonParty() {
        return anonParty;
    }

    public FlowSession getSessionToHost() {
        return sessionToHost;
    }

    public void setSessionToHost(FlowSession sessionToHost) {
        this.sessionToHost = sessionToHost;
    }
}
