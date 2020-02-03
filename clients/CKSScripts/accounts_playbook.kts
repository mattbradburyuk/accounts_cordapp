

/**
 * This playbook give the commands which when run in order will give a demo of the accounts_cordapp
 *
 * Before starting the CKS, you should (for Mac):
 * - run './gradlew clean' to clear down your nodes
 * - run './gradlew deployNodes'
 * - start your Corda nodes using 'runnodes.sh'
 *
 * To start the shell run  './startup.sh'
 *
 * To set up the shell for accounts run >>> :load scripts/setup_accounts_cordapp.kts
 *
 * This will:
 *  - Load required imports
 *  - Setup proxies called a, b, c, n for each of the nodes
 *  - Give you a proxy.start() function which can act a general flow starter
 *  - Give you a set of helper functions, see setup_accounts_cordapp.kts to see what they are
 *
 *
 * Note, refrain from creating accounts before going through this playbook as it requires a clean node to flow through properly
 *
 */

// Create  and show dummy accounts (this will error if the accounts have already been created)
// Show different nodes have different account

createDummyAccounts()

// Use SDK API 'OurAccounts' to get a list of accoutns

val nAcs = n.start(OurAccounts::class.java)
val aAcs = a.start(OurAccounts::class.java)
val bAcs = b.start(OurAccounts::class.java)
val cAcs = c.start(OurAccounts::class.java)

aAcs.pretty()

val A1 = aAcs[0]
val A2 = aAcs[1]
val A3 = aAcs[2]
val B1 = bAcs[0]
val B2 = bAcs[1]
val B3 = bAcs[2]
val C1 = cAcs[0]
val C2 = cAcs[1]
val C3 = cAcs[2]

// We can see that each item in the List is a StateRef containing AccountInfo for the account
A1

// We can make this easier to ready with the pretty() function (provided in setup_accounts_cordapp.kts)
A1.pretty()


// We can show just the account info
A1.state.data.pretty()


// We need to make sure that each of the parties for the transaction know about the accounts who's key's are participating on the transaction, otherwide won't know who they are dealing with. Let's see what happens when we don't.

// First, who can see which accounts,

visibleAccounts()

// We'll try to create the deal

// Note: The a.start() is a generic flow starter function provided in provided in setup_accounts_cordapp.kts

a.start(AccountsDealFlow::class.java, A1.id(), B1.id(), C1.id(), "A1 sell stuff to B1 via broker C1 - Initiated by partyA")

// This Fails because partyA doesn't know about Account B1 or C1 hence throws an exception

// partyA request B1 AccountInfo from partyB using SDK RequestAccountInfo API
a.start(RequestAccountInfo::class.java, B1.id(), b.party())

// Try again
a.start(AccountsDealFlow::class.java, A1.id(), B1.id(), C1.id(), "A1 sell stuff to B1 via broker C1 - Initiated by partyA")

// partyA request C1 AccountInfo from partyC
a.start(RequestAccountInfo::class.java, C1.id(), c.party())
visibleAccounts()


// Try again to create the deal
a.start(AccountsDealFlow::class.java, A1.id(), B1.id(), C1.id(), "A1 sell stuff to B1 via broker C1 - Initiated by partyA")

// now fails because when responding
// partyB doesn't know about A1 and C1
// partyC doesn't know about A1 and B1
// This time as partyA has info on all accounts, lets get partyA to share the accountInfos
// using the SDK ShareAccountInfo API call

a.start(ShareAccountInfo::class.java, A1, listOf(b.party(), c.party()))
a.start(ShareAccountInfo::class.java, B1, listOf(c.party()))
a.start(ShareAccountInfo::class.java, C1, listOf(b.party()))

// Now the right nodes have visibility of the right accounts
visibleAccounts()

// The deal should now work
a.start(AccountsDealFlow::class.java, A1.id(), B1.id(), C1.id(), "A1 sell stuff to B1 via broker C1 - Initiated by partyA")

// Show the deal
// replace with SDK call? No, need to tailor queryCriteria

val deals = a.getDealsForAccountByName("Account A1")
deals.pretty()


// show resolution of CIs
val buyerKey = deals[0].buyer.owningKey
val sellerKey = deals[0].seller.owningKey
val brokerKey = deals[0].broker.owningKey

// a and b resolving the buyers key  *** SDK ??? **
a.start(AccountInfoByKey::class.java, buyerKey)!!.pretty()
b.start(AccountInfoByKey::class.java, buyerKey)!!.pretty()



// We have a helper to resolve identities and print all the deals

a.printDealsForAccount("Account A1")


// Let's share all of the other account infos so we don't hit exceptions because nodes don't know about accounts

a.start(ShareAccountInfo::class.java, A2, listOf(b.party(), c.party()))
a.start(ShareAccountInfo::class.java, A3, listOf(b.party(), c.party()))
b.start(ShareAccountInfo::class.java, B2, listOf(a.party(), c.party()))
b.start(ShareAccountInfo::class.java, B3, listOf(a.party(), c.party()))
c.start(ShareAccountInfo::class.java, C2, listOf(a.party(), b.party()))
c.start(ShareAccountInfo::class.java, C3, listOf(a.party(), b.party()))
visibleAccounts()

// Now let's try a deal with all accounts on one node

b.start(AccountsDealFlow::class.java, B1.id(), B2.id(), B3.id(), "B1 sell stuff to B2 via broker B3 - Initiated by partyB")
b.printDealsForAccount("Account B1")

// note: a cannot see the new deal when it searches on "Account B1":
a.printDealsForAccount("Account B1")


// Let's try the mixed case where a deal has 2 Accounts on one node and one Account on a different node


c.start(AccountsDealFlow::class.java, C1.id(), B2.id(), B3.id(), "C1 sell stuff to B2 via broker B3 - Initiated by partyC")
c.printDealsForAccount("Account C1")

// However, searching on node A for "Account C1" only returns the deals node a knows about
a.printDealsForAccount("Account C1")


// Note: the cordap allows any actor to create a deal with any combination of accounts even those on another node




