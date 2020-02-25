import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.template.flows.AccountsDealFlow;
import com.template.states.AccountsDealState;
import kotlin.Unit;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.node.NotaryInfo;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.common.internal.ParametersUtilitiesKt;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class AccountsDealFlowTest {
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters()
            .withNetworkParameters(ParametersUtilitiesKt.testNetworkParameters(ImmutableList.of(), 4))
            .withCordappsForAllNodes(ImmutableList.of(
                    TestCordapp.findCordapp("com.template.contracts"),
                    TestCordapp.findCordapp("com.template.states"),
                    TestCordapp.findCordapp("com.template.flows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.accounts.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.accounts.workflows")
            )));
    private final StartedMockNode a = network.createNode();
    private final StartedMockNode b = network.createNode();
    private final StartedMockNode c = network.createNode();

    public AccountsDealFlowTest() {
        ImmutableList.of(a, b, c).forEach(it -> {
            it.registerInitiatedFlow(AccountsDealFlow.AccountsDealFlowResponder.class);
        });
    }

    @Before
    public void setup() {
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

    @Test
    public void dealWithAccountsOnDifferentNodesTest() throws ExecutionException, InterruptedException {
        // setup accounts to use
        CreateAccount flowA1 = new CreateAccount("Node A Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureA1 = a.startFlow(flowA1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefA1 = futureA1.get();
        AccountInfo accountInfoA = accountStateAndRefA1.getState().getData();
        assert accountInfoA.getName().equals("Node A Account 1");

        CreateAccount flowB1 = new CreateAccount("Node B Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureB1 = b.startFlow(flowB1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefB1 = futureB1.get();
        AccountInfo accountInfoB = accountStateAndRefB1.getState().getData();
        assert accountInfoB.getName().equals("Node B Account 1");

        CreateAccount flowC1 = new CreateAccount("Node C Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureC1 = c.startFlow(flowC1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefC1 = futureC1.get();
        AccountInfo accountInfoC = accountStateAndRefC1.getState().getData();
        assert accountInfoC.getName().equals("Node C Account 1");

        for (int i = 2; i <= 5; i++) {

            CreateAccount flow1 = new CreateAccount("Node A Account " + i);
            CordaFuture<StateAndRef<? extends AccountInfo>> future1 = a.startFlow(flow1);
            network.runNetwork();
            StateAndRef<? extends AccountInfo> results1 = future1.get();
            AccountInfo accountInfo1 = results1.getState().getData();
            assert accountInfo1.getName().equals("Node A Account " + i);

            CreateAccount flow2 = new CreateAccount("Node B Account " + i);
            CordaFuture<StateAndRef<? extends AccountInfo>> future2 = b.startFlow(flow2);
            network.runNetwork();
            StateAndRef<? extends AccountInfo> results2 = future2.get();
            AccountInfo accountInfo2 = results2.getState().getData();
            assert accountInfo2.getName().equals("Node B Account " + i);

            CreateAccount flow3 = new CreateAccount("Node C Account " + i);
            CordaFuture<StateAndRef<? extends AccountInfo>> future3 = c.startFlow(flow3);
            network.runNetwork();
            StateAndRef<? extends AccountInfo> result3 = future3.get();
            AccountInfo accountInfo3 = result3.getState().getData();
            assert accountInfo3.getName().equals("Node C Account " + i);

        }

        // Share AccountInfos

        Party aParty = a.getServices().getMyInfo().getLegalIdentities().get(0);
        Party bParty = b.getServices().getMyInfo().getLegalIdentities().get(0);
        Party cParty = c.getServices().getMyInfo().getLegalIdentities().get(0);

        ShareAccountInfo flowA2 = new ShareAccountInfo((StateAndRef<AccountInfo>) accountStateAndRefA1, ImmutableList.of(bParty, cParty));
        CordaFuture<Unit> futureA2 = a.startFlow(flowA2);
        network.runNetwork();
        futureA2.get();

        ShareAccountInfo flowB2 = new ShareAccountInfo((StateAndRef<AccountInfo>) accountStateAndRefB1, ImmutableList.of(aParty, cParty));
        CordaFuture<Unit> futureB2 = b.startFlow(flowB2);
        network.runNetwork();
        futureB2.get();

        ShareAccountInfo flowC2 = new ShareAccountInfo((StateAndRef<AccountInfo>) accountStateAndRefC1, ImmutableList.of(aParty, bParty));
        CordaFuture<Unit> futureC2 = c.startFlow(flowC2);
        network.runNetwork();
        futureC2.get();

        // for debugging
        List<StateAndRef<AccountInfo>> aTemp = a.getServices().cordaService(KeyManagementBackedAccountService.class).allAccounts();
        List<StateAndRef<AccountInfo>> bTemp = b.getServices().cordaService(KeyManagementBackedAccountService.class).allAccounts();
        List<StateAndRef<AccountInfo>> cTemp = c.getServices().cordaService(KeyManagementBackedAccountService.class).allAccounts();

        // Do the deal: buyer A (Initiator), seller B, broker C
        AccountsDealFlow.AccountsDealFlowInitiator flowA3 = new AccountsDealFlow.AccountsDealFlowInitiator(
                accountInfoA.getIdentifier().getId(),
                accountInfoB.getIdentifier().getId(),
                accountInfoC.getIdentifier().getId(),
                "Buy some Sausages - A Initiates");
        CordaFuture<SignedTransaction> futureA3 = a.startFlow(flowA3);
        network.runNetwork();
        SignedTransaction resultA3 = futureA3.get();
        AccountsDealState deal1 = (AccountsDealState) resultA3.getCoreTransaction().getOutputStates().get(0);
        assert deal1.getDeal().equals("Buy some Sausages - A Initiates");

        UUID buyerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getBuyer().getOwningKey());
        UUID sellerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getSeller().getOwningKey());
        UUID brokerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getBroker().getOwningKey());
        assert accountInfoA.getIdentifier().getId().equals(buyerUUID1);
        assert accountInfoB.getIdentifier().getId().equals(sellerUUID1);
        assert accountInfoC.getIdentifier().getId().equals(brokerUUID1);

        // Do the deal: buyer A, seller B (Initiator), broker C

        AccountsDealFlow.AccountsDealFlowInitiator flowB3 = new AccountsDealFlow.AccountsDealFlowInitiator(
                accountInfoA.getIdentifier().getId(),
                accountInfoB.getIdentifier().getId(),
                accountInfoC.getIdentifier().getId(),
                "Buy some Sausages - B Initiates");
        CordaFuture<SignedTransaction> futureB3 = b.startFlow(flowB3);
        network.runNetwork();
        SignedTransaction resultB3 = futureB3.get();
        AccountsDealState deal2 = (AccountsDealState) resultB3.getCoreTransaction().getOutputStates().get(0);
        assert deal2.getDeal().equals("Buy some Sausages - B Initiates");

        UUID buyerUUID2 = b.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal2.getBuyer().getOwningKey());
        UUID sellerUUID2 = b.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal2.getSeller().getOwningKey());
        UUID brokerUUID2 = b.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal2.getBroker().getOwningKey());
        assert accountInfoA.getIdentifier().getId().equals(buyerUUID2);
        assert accountInfoB.getIdentifier().getId().equals(sellerUUID2);
        assert accountInfoC.getIdentifier().getId().equals(brokerUUID2);

        // Do the deal: buyer A, seller B, broker C (Initiator)

        AccountsDealFlow.AccountsDealFlowInitiator flowC3 = new AccountsDealFlow.AccountsDealFlowInitiator(
                accountInfoA.getIdentifier().getId(),
                accountInfoB.getIdentifier().getId(),
                accountInfoC.getIdentifier().getId(),
                "Buy some Sausages - C Initiates"
        );
        CordaFuture<SignedTransaction> futureC3 = c.startFlow(flowC3);
        network.runNetwork();
        SignedTransaction resultC3 = futureC3.get();
        AccountsDealState deal3 = (AccountsDealState) resultC3.getCoreTransaction().getOutputStates().get(0);
        assert deal3.getDeal().equals("Buy some Sausages - C Initiates");

        UUID buyerUUID3 = c.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal3.getBuyer().getOwningKey());
        UUID sellerUUID3 = c.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal3.getSeller().getOwningKey());
        UUID brokerUUID3 = c.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal3.getBroker().getOwningKey());
        assert accountInfoA.getIdentifier().getId().equals(buyerUUID3);
        assert accountInfoB.getIdentifier().getId().equals(sellerUUID3);
        assert accountInfoC.getIdentifier().getId().equals(brokerUUID3);

    }

    @Test
    public void dealWithAccountsOnSameNodeTest() throws ExecutionException, InterruptedException {
        // set up accounts to use
        CreateAccount flowA1 = new CreateAccount("Node A Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureA1 = a.startFlow(flowA1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefA1 = futureA1.get();
        AccountInfo accountInfoA1 = accountStateAndRefA1.getState().getData();
        assert accountInfoA1.getName().equals("Node A Account 1");

        CreateAccount flowA2 = new CreateAccount("Node A Account 2");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureA2 = a.startFlow(flowA2);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefA2 = futureA2.get();
        AccountInfo accountInfoA2 = accountStateAndRefA2.getState().getData();
        assert accountInfoA2.getName().equals("Node A Account 2");

        CreateAccount flowA3 = new CreateAccount("Node A Account 3");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureA3 = a.startFlow(flowA3);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefA3 = futureA3.get();
        AccountInfo accountInfoA3 = accountStateAndRefA3.getState().getData();
        assert accountInfoA3.getName().equals("Node A Account 3");

        // Setup additional accounts
        for (int i = 4; i <= 5; i++) {

            CreateAccount flow1 = new CreateAccount("Node A Account " + i);
            CordaFuture<StateAndRef<? extends AccountInfo>> future1 = a.startFlow(flow1);
            network.runNetwork();
            StateAndRef<? extends AccountInfo> result1 = future1.get();
            AccountInfo accountInfo1 = result1.getState().getData();
            assert accountInfo1.getName().equals("Node A Account " + i);
        }

        // Note, no need to share keys as all on the same node

        // for debug
        List<StateAndRef<AccountInfo>> aTemp = a.getServices().cordaService(KeyManagementBackedAccountService.class).allAccounts();

        // Do the deal: buyer A (Initiator), seller A, broker A

        AccountsDealFlow.AccountsDealFlowInitiator flowA4 = new AccountsDealFlow.AccountsDealFlowInitiator(
                accountInfoA1.getIdentifier().getId(),
                accountInfoA2.getIdentifier().getId(),
                accountInfoA3.getIdentifier().getId(),
                "Buy some Sausages - A Initiates"
        );
        CordaFuture<SignedTransaction> futureA4 = a.startFlow(flowA4);
        network.runNetwork();
        SignedTransaction resultA4 = futureA4.get();
        AccountsDealState deal1 = (AccountsDealState) resultA4.getCoreTransaction().getOutputStates().get(0);
        assert deal1.getDeal().equals("Buy some Sausages - A Initiates");

        UUID buyerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getBuyer().getOwningKey());
        UUID sellerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getSeller().getOwningKey());
        UUID brokerUUI1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getBroker().getOwningKey());
        assert accountInfoA1.getIdentifier().getId().equals(buyerUUID1);
        assert accountInfoA2.getIdentifier().getId().equals(sellerUUID1);
        assert accountInfoA3.getIdentifier().getId().equals(brokerUUI1);

    }

    @Test
    public void dealWithMixedLocalAndRemoteAccounts() throws ExecutionException, InterruptedException {

        // set up accounts to use

        CreateAccount flowA1 = new CreateAccount("Node A Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureA1 = a.startFlow(flowA1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefA1 = futureA1.get();
        AccountInfo accountInfoA1 = accountStateAndRefA1.getState().getData();
        assert  accountInfoA1.getName().equals("Node A Account 1");

        CreateAccount flowA2 = new CreateAccount("Node A Account 2");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureA2 = a.startFlow(flowA2);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefA2 = futureA2.get();
        AccountInfo accountInfoA2 = accountStateAndRefA2.getState().getData();
        assert  accountInfoA2.getName().equals("Node A Account 2");

        CreateAccount flowB1 = new CreateAccount("Node B Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureB1 = b.startFlow(flowB1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefB1 = futureB1.get();
        AccountInfo accountInfoB1 = accountStateAndRefB1.getState().getData();
        assert  accountInfoB1.getName().equals("Node B Account 1");

        // Setup additional accounts

        for (int i = 3; i <= 5; i++) {
            CreateAccount flow1 = new CreateAccount("Node A Account " + i);
            CordaFuture<StateAndRef<? extends AccountInfo>> future1 = a.startFlow(flow1);
            network.runNetwork();
            StateAndRef<? extends AccountInfo> result1 = future1.get();
            AccountInfo accountInfo1 = result1.getState().getData();
            assert accountInfo1.getName().equals("Node A Account " + i);
        }

        // Share AccountInfos

        Party aParty = a.getServices().getMyInfo().getLegalIdentities().get(0);
        Party bParty = b.getServices().getMyInfo().getLegalIdentities().get(0);
        Party cParty = c.getServices().getMyInfo().getLegalIdentities().get(0);

        ShareAccountInfo flowA3 = new ShareAccountInfo((StateAndRef<AccountInfo>) accountStateAndRefA1, ImmutableList.of(bParty));
        CordaFuture<Unit> futureA3 = a.startFlow(flowA3);
        network.runNetwork();
        futureA3.get();

        ShareAccountInfo flowA4 = new ShareAccountInfo((StateAndRef<AccountInfo>) accountStateAndRefA2, ImmutableList.of(bParty));
        CordaFuture<Unit> futureA4 = a.startFlow(flowA4);
        network.runNetwork();
        futureA4.get();

        ShareAccountInfo flowB3 = new ShareAccountInfo((StateAndRef<AccountInfo>) accountStateAndRefB1, ImmutableList.of(aParty));
        CordaFuture<Unit> futureB3 = b.startFlow(flowB3);
        network.runNetwork();
        futureB3.get();

        // for debug
        List<StateAndRef<AccountInfo>> aTemp = a.getServices().cordaService(KeyManagementBackedAccountService.class).allAccounts();
        List<StateAndRef<AccountInfo>> bTemp = b.getServices().cordaService(KeyManagementBackedAccountService.class).allAccounts();

        // Do the deal: buyer A (Initiator), seller A, broker B
        AccountsDealFlow.AccountsDealFlowInitiator flowA5 = new AccountsDealFlow.AccountsDealFlowInitiator(
                accountInfoA1.getIdentifier().getId(),
                accountInfoA2.getIdentifier().getId(),
                accountInfoB1.getIdentifier().getId(),
                "Buy some Sausages - A Initiates"
        );
        CordaFuture<SignedTransaction> futureA5 = a.startFlow(flowA5);
        network.runNetwork();
        SignedTransaction resultA5 = futureA5.get();
        AccountsDealState deal1 = (AccountsDealState) resultA5.getCoreTransaction().getOutputStates().get(0);
        assert deal1.getDeal().equals("Buy some Sausages - A Initiates");

        UUID buyerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getBuyer().getOwningKey());
        UUID sellerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getSeller().getOwningKey());
        UUID brokerUUID1 = a.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal1.getBroker().getOwningKey());
        assert accountInfoA1.getIdentifier().getId().equals(buyerUUID1);
        assert accountInfoA2.getIdentifier().getId().equals(sellerUUID1);
        assert accountInfoB1.getIdentifier().getId().equals(brokerUUID1);

        AccountsDealFlow.AccountsDealFlowInitiator flowB5 = new AccountsDealFlow.AccountsDealFlowInitiator(
                accountInfoA1.getIdentifier().getId(),
                accountInfoA2.getIdentifier().getId(),
                accountInfoB1.getIdentifier().getId(),
                "Buy some Sausages - B Initiates"
        );
        CordaFuture<SignedTransaction> futureB5 = b.startFlow(flowB5);
        network.runNetwork();
        SignedTransaction resultB5 = futureB5.get();
        AccountsDealState deal2 = (AccountsDealState) resultB5.getCoreTransaction().getOutputStates().get(0);
        assert deal2.getDeal().equals("Buy some Sausages - B Initiates");

        UUID buyerUUID2 = b.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal2.getBuyer().getOwningKey());
        UUID sellerUUID2 = b.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal2.getSeller().getOwningKey());
        UUID brokerUUID2 = b.getServices().cordaService(KeyManagementBackedAccountService.class).accountIdForKey(deal2.getBroker().getOwningKey());
        assert accountInfoA1.getIdentifier().getId().equals(buyerUUID2);
        assert accountInfoA2.getIdentifier().getId().equals(sellerUUID2);
        assert accountInfoB1.getIdentifier().getId().equals(brokerUUID2);
    }
}
