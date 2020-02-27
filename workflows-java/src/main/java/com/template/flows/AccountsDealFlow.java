package com.template.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.UtilitiesKt;
import com.r3.corda.lib.accounts.workflows.flows.RequestKeyForAccount;
import com.r3.corda.lib.accounts.workflows.services.AccountService;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.template.contracts.AccountsDealContract;
import com.template.states.AccountsDealState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.StatesToRecord;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class AccountsDealFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class AccountsDealFlowInitiator extends FlowLogic<SignedTransaction> {
        private final UUID buyerAccountUUID;
        private final UUID sellerAccountUUID;
        private final UUID brokerAccountUUID;
        private final String deal;

        public AccountsDealFlowInitiator(UUID buyerAccountUUID, UUID sellerAccountUUID, UUID brokerAccountUUID, String deal) {
            this.buyerAccountUUID = buyerAccountUUID;
            this.sellerAccountUUID = sellerAccountUUID;
            this.brokerAccountUUID = brokerAccountUUID;
            this.deal = deal;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Get AccountInfos
            AccountService accountService = getServiceHub().cordaService(KeyManagementBackedAccountService.class);
            StateAndRef<AccountInfo> buyerAccountStateAndRef = accountService.accountInfo(buyerAccountUUID);
            StateAndRef<AccountInfo> sellerAccountStateAndRef = accountService.accountInfo(sellerAccountUUID);
            StateAndRef<AccountInfo> brokerAccountStateAndRef = accountService.accountInfo(brokerAccountUUID);

            if(buyerAccountStateAndRef == null) throw new FlowException("buyerAccount not found");
            if(sellerAccountStateAndRef == null) throw new FlowException("sellerAccount not found");
            if(brokerAccountStateAndRef == null) throw new FlowException("brokerAccount not found");

            AccountInfo buyerAccountInfo = buyerAccountStateAndRef.getState().getData();
            AccountInfo sellerAccountInfo = sellerAccountStateAndRef.getState().getData();
            AccountInfo brokerAccountInfo = brokerAccountStateAndRef.getState().getData();

            // Get anonymousParty/keys
            AnonymousParty buyerAnon = subFlow(new RequestKeyForAccount(buyerAccountInfo));
            AnonymousParty sellerAnon = subFlow(new RequestKeyForAccount(sellerAccountInfo));
            AnonymousParty brokerAnon = subFlow(new RequestKeyForAccount(brokerAccountInfo));

            // maps for accounts
            AccountMapper buyerMap = new AccountMapper(buyerAccountInfo, buyerAnon);
            AccountMapper sellerMap = new AccountMapper(sellerAccountInfo, sellerAnon);
            AccountMapper brokerMap = new AccountMapper(brokerAccountInfo, brokerAnon);
            List<AccountMapper> accountMaps = Arrays.asList(buyerMap, sellerMap, brokerMap);

            // create a list of Keys To Share ie keys created by this node
            List<InfoToRegisterKey> keysToShare = accountMaps.stream().map(it -> new InfoToRegisterKey(
                    it.getAnonParty().getOwningKey(),
                    it.getAccountInfo().getHost(),
                    it.getAccountInfo().getIdentifier().getId()
            )).collect(Collectors.toList());

            // establish FlowSessions with account host which are not us and send keysToShare
            Party myNode = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            // Set FlowSessions for counter-parties and share keys
            for (AccountMapper acct : accountMaps) {
                if (!acct.getAccountInfo().getHost().equals(myNode)) {
                    acct.setSessionToHost(initiateFlow(acct.getAccountInfo().getHost()));
                    acct.getSessionToHost().send(keysToShare);
                }
            }

            // create and verify Transaction
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addCommand(new AccountsDealContract.Commands.CreateDeal(), buyerAnon.getOwningKey(), sellerAnon.getOwningKey(), brokerAnon.getOwningKey())
                    .addOutputState(new AccountsDealState(buyerAnon, sellerAnon, brokerAnon, deal), AccountsDealContract.ID);

            txBuilder.verify(getServiceHub());

            // This node sign
            List<PublicKey> localKeysToSign = accountMaps.stream().filter(it -> it.getAccountInfo().getHost().equals(myNode))
                    .map(it -> it.getAnonParty().getOwningKey())
                    .collect(Collectors.toList());
            SignedTransaction locallySignedTx = getServiceHub().signInitialTransaction(txBuilder, localKeysToSign);

            // Other nodes sign
            List<TransactionSignature> otherSignatures = new ArrayList<>();
            for (AccountMapper acct : accountMaps) {
                if (!acct.getAccountInfo().getHost().equals(myNode)) {
                    otherSignatures.add(subFlow(new CollectSignatureFlow(locallySignedTx, acct.getSessionToHost(), ImmutableList.of(acct.getAnonParty().getOwningKey()))).get(0));
                }
            }

            SignedTransaction fullySignedTx = locallySignedTx.withAdditionalSignatures((List<TransactionSignature>) otherSignatures);

            // Finalise

            List<FlowSession> sessions = accountMaps.stream().filter(it -> !it.getAccountInfo().getHost().equals(myNode))
                    .map(AccountMapper::getSessionToHost).collect(Collectors.toList());

            return subFlow(new FinalityFlow(fullySignedTx, sessions));
        }
    }

    @InitiatedBy(AccountsDealFlowInitiator.class)
    public static class AccountsDealFlowResponder extends FlowLogic<Void> {
        private final FlowSession otherPartySession;

        public AccountsDealFlowResponder(FlowSession otherPartySession) {
            this.otherPartySession = otherPartySession;
        }

        @Suspendable
        @Override
        public Void call() throws FlowException {
            // Receive info to register keys created on other nodes
            List<InfoToRegisterKey> infoToRegisterKeys = otherPartySession.receive(List.class).unwrap(it -> it);
            infoToRegisterKeys.forEach(it -> {
                getServiceHub().getIdentityService().registerKey(it.getPublicKey(), it.getParty(), it.getExternalId());
            });

            SignedTransaction stx = subFlow(new SignTransactionFlow(otherPartySession) {
                @Override
                @Suspendable
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {
                    AccountService accountService = UtilitiesKt.getAccountService(this);
                    requireThat(req -> {
                        AccountsDealState deal = (AccountsDealState) stx.getCoreTransaction().getOutputs().get(0).getData();
                        StateAndRef<AccountInfo> buyerAccount = accountService.accountInfo(deal.getBuyer().getOwningKey());
                        StateAndRef<AccountInfo> sellerAccount = accountService.accountInfo(deal.getSeller().getOwningKey());
                        StateAndRef<AccountInfo> brokerAccount = accountService.accountInfo(deal.getBuyer().getOwningKey());

                        // check the Responding Node knows about the Accounts used in the transaction
                        req.using("The responder can resolve buyer's Account from the buyer's Pubic Key", buyerAccount != null);
                        req.using("The responder can resolve seller's Account from the seller's Pubic Key", sellerAccount != null);
                        req.using("The responder can resolve broker's Account from the seller's Pubic Key", brokerAccount != null);
                        return null;
                    });
                }
            });

            subFlow(new ReceiveFinalityFlow(otherPartySession, stx.getId(), StatesToRecord.ALL_VISIBLE));
            return null;
        }
    }
}