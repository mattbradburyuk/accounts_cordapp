package com.template;

import com.google.common.collect.ImmutableList;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.driver.Driver;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class DriverBasedTest {
    TestIdentity bankA = new TestIdentity(new CordaX500Name("BankA", "", "GB"));
    TestIdentity bankB = new TestIdentity(new CordaX500Name("BankB", "", "US"));

    @Test
    public void nodeTest() {
        Driver.driver(new DriverParameters().withIsDebug(true).withStartNodesInProcess(true), driver -> {
            // start up nodes based on test identity
            List<NodeHandle> startedNodes = ImmutableList.of(bankA, bankB).stream()
                    .map(it -> {
                        try {
                            return driver.startNode(new NodeParameters().withProvidedName(it.getName())).get();
                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                        return null;
                    })
                    .collect(Collectors.toList());

            // checks if the started nodes are named correctly, trivial test
            assert startedNodes.stream()
                    .map(it -> it.getNodeInfo().getLegalIdentities().get(0).getName())
                    .collect(Collectors.toList())
                    .containsAll(ImmutableList.of(bankA.getName(), bankB.getName()));

            return null;
        });
    }
}
