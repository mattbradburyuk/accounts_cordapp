import com.google.common.collect.ImmutableList;
import com.r3.corda.lib.accounts.contracts.states.AccountInfo;
import com.r3.corda.lib.accounts.workflows.flows.CreateAccount;
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo;
import com.r3.corda.lib.accounts.workflows.services.KeyManagementBackedAccountService;
import com.template.flows.AccountsDealFlow.AccountsDealFlowInitiator;
import com.template.flows.AccountsDealFlow.AccountsDealFlowResponder;
import com.template.flows.GetDealsByAccountFlow;
import com.template.flows.GetDealsByKeyFlow;
import com.template.states.AccountsDealState;
import kotlin.Unit;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.common.internal.ParametersUtilitiesKt;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.MockNetworkParameters;
import net.corda.testing.node.StartedMockNode;
import net.corda.testing.node.TestCordapp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class GetDealsFlowTest {
    private final MockNetwork network = new MockNetwork(new MockNetworkParameters()
            .withNetworkParameters(ParametersUtilitiesKt.testNetworkParameters(Collections.EMPTY_LIST, 4))
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

    public GetDealsFlowTest() {
        ImmutableList.of(a, b, c).forEach(it -> {
            it.registerInitiatedFlow(AccountsDealFlowResponder.class);
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
    public void getDealsForAccount() throws ExecutionException, InterruptedException {
        // setup accounts to use
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

        CreateAccount flowB1 = new CreateAccount("Node B Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureB1 = b.startFlow(flowB1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefB1 = futureB1.get();
        AccountInfo accountInfoB1 = accountStateAndRefB1.getState().getData();
        assert accountInfoB1.getName().equals("Node B Account 1");

        CreateAccount flowC1 = new CreateAccount("Node C Account 1");
        CordaFuture<StateAndRef<? extends AccountInfo>> futureC1 = c.startFlow(flowC1);
        network.runNetwork();
        StateAndRef<? extends AccountInfo> accountStateAndRefC1 = futureC1.get();
        AccountInfo accountInfoC1 = accountStateAndRefC1.getState().getData();
        assert accountInfoC1.getName().equals("Node C Account 1");

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
        KeyManagementBackedAccountService aTemp = a.getServices().cordaService(KeyManagementBackedAccountService.class);
        KeyManagementBackedAccountService bTemp = b.getServices().cordaService(KeyManagementBackedAccountService.class);

        // Do the deal: buyer A (Initiator), seller A, broker B

        AccountsDealFlowInitiator flowA5 = new AccountsDealFlowInitiator(
                accountInfoA1.getIdentifier().getId(),
                accountInfoA2.getIdentifier().getId(),
                accountInfoB1.getIdentifier().getId(),
                "Buy some Sausages - A Initiates");
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

        // Do the deal: buyer A, seller A, broker B (Initiator)
        AccountsDealFlowInitiator flowB5 = new AccountsDealFlowInitiator(
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

        // Query the vault by account UUID

        // Check A has both deals

        GetDealsByAccountFlow flowA6 = new GetDealsByAccountFlow(ImmutableList.of(accountInfoA1.getIdentifier().getId()));
        CordaFuture<List<StateAndRef<AccountsDealState>>> futureA6 = a.startFlow(flowA6);
        network.runNetwork();
        List<StateAndRef<AccountsDealState>> resultA6 = futureA6.get();
        System.out.println("MB: " + resultA6);

        List<AccountsDealState> statesReturnedA6 = resultA6.stream().map(it -> it.getState().getData()).collect(Collectors.toList());
        List<String> dealsReturnedA6 = statesReturnedA6.stream().map(AccountsDealState::getDeal).collect(Collectors.toList());

        assert (dealsReturnedA6.size() == 2);
        assert dealsReturnedA6.contains("Buy some Sausages - A Initiates");
        assert dealsReturnedA6.contains("Buy some Sausages - B Initiates");

        // Check C doesn't see either deal
        GetDealsByAccountFlow flowC6 = new GetDealsByAccountFlow(Collections.singletonList(accountInfoC1.getIdentifier().getId()));
        CordaFuture<List<StateAndRef<AccountsDealState>>> futureC6 = c.startFlow(flowC6);
        network.runNetwork();
        List<StateAndRef<AccountsDealState>> resultC6 = futureC6.get();
        System.out.println("MB: " + resultC6);

        List<AccountsDealState> statesReturnedC6 = resultC6.stream().map(it -> it.getState().getData()).collect(Collectors.toList());
        List<String> dealsReturnedC6 = statesReturnedC6.stream().map(it -> it.getDeal()).collect(Collectors.toList());

        assert dealsReturnedC6.isEmpty();

        // Query the vault by buyer key for deal1

        // Check A1 Key returns both deals

        GetDealsByKeyFlow flowA7 = new GetDealsByKeyFlow(deal1.getBuyer().getOwningKey());
        CordaFuture<List<StateAndRef<AccountsDealState>>> futureA7 = a.startFlow(flowA7);
        network.runNetwork();
        List<StateAndRef<AccountsDealState>> resultA7 = futureA7.get();
        System.out.println("MB: " + resultA7);

        List<AccountsDealState> statesReturnedA7 = resultA7.stream().map(it -> it.getState().getData()).collect(Collectors.toList());
        List<String> dealsReturnedA7 = statesReturnedA7.stream().map(it -> it.getDeal()).collect(Collectors.toList());

        assert dealsReturnedA7.size() == 2;
        assert dealsReturnedA7.contains("Buy some Sausages - A Initiates");
        assert dealsReturnedA7.contains("Buy some Sausages - B Initiates");
    }
}
