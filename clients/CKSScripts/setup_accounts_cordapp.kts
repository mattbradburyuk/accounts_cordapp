import helpers.*
import net.corda.core.node.services.vault.*
import net.corda.core.contracts.*
import net.corda.core.node.services.*
import net.corda.core.flows.*

import com.template.flows.*
import com.template.states.*
import com.template.contracts.*
import net.corda.core.messaging.CordaRPCOps
import com.r3.corda.lib.accounts.contracts.states.AccountInfo
import net.corda.core.identity.Party
import com.r3.corda.lib.accounts.workflows.flows.RequestAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.ShareAccountInfo
import com.r3.corda.lib.accounts.workflows.flows.AccountInfoByKey
import java.util.*
import net.corda.core.transactions.SignedTransaction
import com.r3.corda.lib.accounts.workflows.flows.*



// set up and check proxies

println("Connect to notary: ")
val n = connect(10003)!! as CordaRPCOps
println("proxy n connected to ${n?.nodeInfo()?.legalIdentitiesAndCerts} node\n")

println("Connect to PartyA: ")
val a = connect(10006)!! as CordaRPCOps
println("proxy a connected to ${a?.nodeInfo()?.legalIdentitiesAndCerts} node\n")

println("Connect to PartyB: ")
val b = connect(10009)!! as CordaRPCOps
println("proxy b connected to ${b?.nodeInfo()?.legalIdentitiesAndCerts} node\n")

println("Connect to PartyC: ")
val c = connect(10012)!! as CordaRPCOps
println("proxy c connected to ${c?.nodeInfo()?.legalIdentitiesAndCerts} node\n")


val proxies = listOf(n,a,b,c)


// set up dummy accounts - Uses Accounts SDK CreateAccount Flow

fun createDummyAccounts() {
    // 3 accounts for node A
    for (i in 1..3) {
        // start a flow
        a.start(CreateAccount::class.java, "Account A$i")
    }
    // 3 accounts for node B
    for (i in 1..3) {
        // start a flow
        b.start(CreateAccount::class.java, "Account B$i")
    }
    // 3 accounts for node C
    for (i in 1..3) {
        // start a flow
        c.start(CreateAccount::class.java, "Account C$i")
    }

    hostedAccounts()

}

// getting accounts

// todo: make this output as an object

fun hostedAccounts() {
    println("Accounts hosted by notary: ${n.start(OurAccounts::class.java).names()}")
    println("Accounts hosted by PartyA: ${a.start(OurAccounts::class.java).names()}")
    println("Accounts hosted by PartyB: ${b.start(OurAccounts::class.java).names()}")
    println("Accounts hosted by PartyC: ${c.start(OurAccounts::class.java).names()}")
}

fun visibleAccounts() {
    println("Accounts visible to notary: ${n.start(AllAccounts::class.java).names()}")
    println("Accounts visible to PartyA: ${a.start(AllAccounts::class.java).names()}")
    println("Accounts visible to PartyB: ${b.start(AllAccounts::class.java).names()}")
    println("Accounts visible to PartyC: ${c.start(AllAccounts::class.java).names()}")
}


// get names of accounts

fun List<StateAndRef<AccountInfo>>.names(): List<String>{
    return this.map {it.state.data.name}
}

fun StateAndRef<AccountInfo>.id(): UUID{
    return this.state.data.identifier.id

}


// get AccountInfo by name

fun CordaRPCOps.getAccountInfoByName(name: String): StateAndRef<AccountInfo>{

    val accountList = this.start(AllAccounts::class.java)
    for (a in accountList!!){
        if (a.state.data.name == name) return a
    }
    throw Exception("No Account called '$name' know to this node")
}

// get UUID by Name

fun CordaRPCOps.getAccountUUIDByName(name: String): UUID{

    val accountList = this.start(AllAccounts::class.java)
    for (a in accountList!!){
        if (a.state.data.name == name) return a.state.data.identifier.id
    }
    throw Exception("No Account called '$name' know to this node")
}


// Do deals

fun CordaRPCOps.createDealByName(buyerName: String, sellerName: String, deal: String): SignedTransaction{

    val buyerUUID = this.getAccountInfoByName(buyerName).state.data.identifier.id
    val sellerUUID = this.getAccountInfoByName(sellerName).state.data.identifier.id

    val fhandle = this.startFlowDynamic(AccountsDealFlow::class.java, buyerUUID, sellerUUID, deal)
    val result = fhandle.returnValue.get()

    //  what happens if transaction fails? => throws exception

    return result
}

// inspect deals

fun CordaRPCOps.getDealsForAccountByName(accountName: String): List<AccountDealState>{

    val accountUUID = this.getAccountInfoByName(accountName).state.data.identifier.id

    val fhandle = this.startFlowDynamic(GetDealsByAccountFlow::class.java, listOf(accountUUID))
    val result = fhandle.returnValue.get()
    return result.map { it.state.data}
}

fun CordaRPCOps.printDealsForAccount(accountName: String): String{

    val accountUUID = this.getAccountInfoByName(accountName).state.data.identifier.id

    val fhandle = this.startFlowDynamic(GetDealsByAccountFlow::class.java, listOf(accountUUID))
    val deals = fhandle.returnValue.get().map{ it.state.data}

    var output = "\n"
    for (deal in deals){

        val fh1 = this.startFlowDynamic(AccountInfoByKey::class.java, deal.buyer.owningKey)
        val buyerAccount = fh1.returnValue.get()!!.state.data as AccountInfo
        val buyer = buyerAccount.name

        val fh2 = this.startFlowDynamic(AccountInfoByKey::class.java, deal.seller.owningKey)
        val sellerAccount = fh2.returnValue.get()!!.state.data as AccountInfo
        val seller = sellerAccount.name

        val fh3 = this.startFlowDynamic(AccountInfoByKey::class.java, deal.broker.owningKey)
        val brokerAccount = fh3.returnValue.get()!!.state.data as AccountInfo
        val broker = brokerAccount.name

        output = output + "deal: \"${deal.deal}\",  with\n    buyer: $buyer,\n    seller: $seller,\n    broker: $broker\n\n"
    }

    return output
}
