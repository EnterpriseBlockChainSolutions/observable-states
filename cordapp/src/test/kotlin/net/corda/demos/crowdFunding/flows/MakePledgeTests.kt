package net.corda.demos.crowdFunding.flows

import net.corda.core.contracts.TransactionResolutionException
import net.corda.core.utilities.getOrThrow
import net.corda.demos.crowdFunding.structures.Campaign
import net.corda.demos.crowdFunding.structures.Pledge
import net.corda.finance.POUNDS
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MakePledgeTests : CrowdFundingTest() {
    @Test
    fun `successfully make a pledge and broadcast the updated campaign state to all parties`() {
        // Campaign.
        val rogersCampaign = Campaign(
                name = "Roger's Campaign",
                target = 1000.POUNDS,
                manager = A.legalIdentity(),
                deadline = fiveSecondsFromNow
        )

        // Start a new campaign.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val createCampaignTransaction = A.start(startCampaignFlow).getOrThrow()

        // Extract the state from the transaction.
        val campaignState = createCampaignTransaction.tx.outputs.single().data as Campaign
        val campaignId = campaignState.linearId

        // Make a pledge from PartyB to PartyA for £100.
        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, campaignId, broadcastToObservers = true)
        val acceptPledgeTransaction = B.start(makePledgeFlow).getOrThrow()

        logger.info("New campaign started")
        logger.info(createCampaignTransaction.toString())
        logger.info(createCampaignTransaction.tx.toString())

        logger.info("PartyB pledges £100 to PartyA")
        logger.info(acceptPledgeTransaction.toString())
        logger.info(acceptPledgeTransaction.tx.toString())

        //Extract the states from the transaction.
        val campaignStateRefAfterPledge = acceptPledgeTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterPledge = acceptPledgeTransaction.tx.outputsOfType<Campaign>().single()
        val newPledgeStateRef = acceptPledgeTransaction.tx.outRefsOfType<Pledge>().single().ref
        val newPledge = acceptPledgeTransaction.tx.outputsOfType<Pledge>().single()

        val aCampaignAfterPledge = A.transaction { A.services.loadState(campaignStateRefAfterPledge).data }
        val bCampaignAfterPledge = B.transaction { B.services.loadState(campaignStateRefAfterPledge).data }
        val cCampaignAfterPledge = C.transaction { C.services.loadState(campaignStateRefAfterPledge).data }
        val dCampaignAfterPledge = D.transaction { D.services.loadState(campaignStateRefAfterPledge).data }
        val eCampaignAfterPledge = E.transaction { E.services.loadState(campaignStateRefAfterPledge).data }

        // All parties should have the same updated Campaign state.
        assertEquals(1,
                setOf(
                        campaignAfterPledge,
                        aCampaignAfterPledge,
                        bCampaignAfterPledge,
                        cCampaignAfterPledge,
                        dCampaignAfterPledge,
                        eCampaignAfterPledge
                ).size
        )

        val aNewPledge = A.transaction { A.services.loadState(newPledgeStateRef).data } as Pledge
        val bNewPledge = B.transaction { B.services.loadState(newPledgeStateRef).data } as Pledge
        val cNewPledge = C.transaction { C.services.loadState(newPledgeStateRef).data } as Pledge
        val dNewPledge = D.transaction { D.services.loadState(newPledgeStateRef).data } as Pledge
        val eNewPledge = E.transaction { E.services.loadState(newPledgeStateRef).data } as Pledge

        // All parties should have the same Pledge state.
        assertEquals(1,
                setOf(
                        newPledge,
                        aNewPledge,
                        bNewPledge,
                        cNewPledge,
                        dNewPledge,
                        eNewPledge
                ).size
        )

        // Only A and B should know the identity of the pledger (who is B in this case).
        assertEquals(B.legalIdentity(), A.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(B.legalIdentity(), B.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, C.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, D.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, E.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))

        network.waitQuiescent()
    }

    @Test
    fun `successfully make a pledge without broadcasting the updated campaign state to all parties`() {
        // Campaign.
        val rogersCampaign = Campaign(
                name = "Roger's Campaign",
                target = 1000.POUNDS,
                manager = A.legalIdentity(),
                deadline = fiveSecondsFromNow // We shut the nodes down before the EndCampaignFlow is run though.
        )

        // Start a new campaign.
        val startCampaignFlow = StartCampaign(rogersCampaign)
        val createCampaignTransaction = A.start(startCampaignFlow).getOrThrow()

        // Extract the state from the transaction.
        val campaignState = createCampaignTransaction.tx.outputs.single().data as Campaign
        val campaignId = campaignState.linearId

        // Make a pledge from PartyB to PartyA for £100 but don't broadcast it to everyone else.
        val makePledgeFlow = MakePledge.Initiator(100.POUNDS, campaignId, broadcastToObservers = false)
        val acceptPledgeTransaction = B.start(makePledgeFlow).getOrThrow()

        logger.info("New campaign started")
        logger.info(createCampaignTransaction.toString())
        logger.info(createCampaignTransaction.tx.toString())

        logger.info("PartyB pledges £100 to PartyA")
        logger.info(acceptPledgeTransaction.toString())
        logger.info(acceptPledgeTransaction.tx.toString())

        //Extract the states from the transaction.
        val campaignStateRefAfterPledge = acceptPledgeTransaction.tx.outRefsOfType<Campaign>().single().ref
        val campaignAfterPledge = acceptPledgeTransaction.tx.outputsOfType<Campaign>().single()
        val newPledgeStateRef = acceptPledgeTransaction.tx.outRefsOfType<Pledge>().single().ref
        val newPledge = acceptPledgeTransaction.tx.outputsOfType<Pledge>().single()

        val aCampaignAfterPledge = A.transaction { A.services.loadState(campaignStateRefAfterPledge).data }
        val bCampaignAfterPledge = B.transaction { B.services.loadState(campaignStateRefAfterPledge).data }
        assertFailsWith(TransactionResolutionException::class) { C.transaction { C.services.loadState(campaignStateRefAfterPledge) } }
        assertFailsWith(TransactionResolutionException::class) { D.transaction { D.services.loadState(campaignStateRefAfterPledge) } }
        assertFailsWith(TransactionResolutionException::class) { E.transaction { E.services.loadState(campaignStateRefAfterPledge) } }

        // Only PartyA and PartyB should have the updated campaign state.
        assertEquals(1, setOf(campaignAfterPledge, aCampaignAfterPledge, bCampaignAfterPledge).size)

        val aNewPledge = A.transaction { A.services.loadState(newPledgeStateRef).data } as Pledge
        val bNewPledge = B.transaction { B.services.loadState(newPledgeStateRef).data } as Pledge
        assertFailsWith(TransactionResolutionException::class) { C.transaction { C.services.loadState(newPledgeStateRef) } }
        assertFailsWith(TransactionResolutionException::class) { D.transaction { D.services.loadState(newPledgeStateRef) } }
        assertFailsWith(TransactionResolutionException::class) { E.transaction { E.services.loadState(newPledgeStateRef) } }

        // Only PartyA and PartyB should have the updated campaign state.
        assertEquals(1, setOf(newPledge, aNewPledge, bNewPledge).size)

        // Only A and B should know the identity of the pledger (who is B in this case). Of course, the others won't know.
        assertEquals(B.legalIdentity(), A.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(B.legalIdentity(), B.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger))
        assertEquals(null, C.transaction { C.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger) })
        assertEquals(null, D.transaction { D.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger) })
        assertEquals(null, E.transaction { E.services.identityService.wellKnownPartyFromAnonymous(newPledge.pledger) })

        network.waitQuiescent()
    }
}