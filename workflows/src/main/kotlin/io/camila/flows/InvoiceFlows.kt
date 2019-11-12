package io.camila.flows


import co.paralleluniverse.fibers.Suspendable
import com.google.common.collect.ImmutableList
import io.camila.agreement.Agreement
import io.camila.flows.PayInvoice.Initiator.Companion.getInvoiceByLinearId
import io.camila.invoice.Invoice
import io.camila.invoice.InvoiceContract
import io.camila.invoice.InvoiceContract.Companion.INVOICE_CONTRACT_ID
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

// *********
// * Create Invoice Flow *
// *********

object CreateInvoiceFlow {
    @StartableByRPC
    @InitiatingFlow
    @Suspendable
    class Invoicer(val invoiceNumber: String,
                  val invoiceName: String,
                  val billingReason: String,
                  val amountDue: Int,
                  val amountPaid: Int,
                  val amountRemaining: Int,
                  val subtotal: Int,
                  val total: Int,
                  val dueDate: String,
                  val periodStartDate: String,
                  val periodEndDate: String,
                  val paid: Boolean?,
                  val active: Boolean?,
                  val createdAt: String?,
                  val lastUpdated: String?,
                  val otherParty: Party) : FlowLogic<SignedTransaction>() {

        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new Agreement.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */


        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Generate an unsigned transaction.
            val me = ourIdentityAndCert.party
            val active = false
            val paid = false
            val time = LocalDateTime.now()
            val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            val formatted = time.format(formatter)
            val createdAt = formatted
            val lastUpdated = formatted
            val agreementReference = serviceHub.vaultService.queryBy<Agreement>().states.single()
            val reference = agreementReference.referenced()
            val invoiceState = Invoice(invoiceNumber, invoiceName, billingReason, amountDue, amountPaid, amountRemaining, subtotal, total, me, otherParty, dueDate, periodStartDate, periodEndDate, paid, active, createdAt, lastUpdated)
            val txCommand = Command(InvoiceContract.Commands.CreateInvoice(), invoiceState.participants.map { it.owningKey })
            progressTracker.currentStep = VERIFYING_TRANSACTION
            val txBuilder = TransactionBuilder(notary)
                    .addReferenceState(reference)
                    .addOutputState(invoiceState, INVOICE_CONTRACT_ID)
                    .addCommand(txCommand)

            txBuilder.verify(serviceHub)
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)


            val otherPartyFlow = initiateFlow(otherParty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(fullySignedTx, listOf(otherPartyFlow), FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Invoicer::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Invoice transaction." using (output is Invoice)
                    val invoice = output as Invoice
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}



// *********
// * Pay Invoice Flow *
// *********



object PayInvoice {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val linearId: UniqueIdentifier,
                    private val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining Obligation from vault.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING)


            fun getInvoiceByLinearId(linearId: UniqueIdentifier): StateAndRef<Invoice> {
                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                        null,
                        ImmutableList.of(linearId),
                        Vault.StateStatus.UNCONSUMED, null)

                return getService(nodeName).proxy().vaultService.queryBy<Invoice>(queryCriteria).states.singleOrNull()
                        ?: throw FlowException("Invoice with id $linearId not found.")
            }

            fun resolveIdentity(abstractParty: AbstractParty): Party {
                return getService(nodeName).proxy().identityService.requireWellKnownPartyFromAnonymous(abstractParty)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {

            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = Initiator.Companion.PREPARATION
            val invoiceToPay = getInvoiceByLinearId(linearId)
            val inputInvoice = invoiceToPay.state.data

            val partyIdentity = resolveIdentity(inputInvoice.party)
            val counterpartyIdentity = resolveIdentity(inputInvoice.counterparty)

            // Stage 3. This flow can only be initiated by the current recipient.
            check(partyIdentity == ourIdentity) {
                throw FlowException("Pay Invoice flow must be initiated by the counterparty.")
            }

            // Stage 4. Check we have enough cash to settle the requested amount.
            val cashBalance = serviceHub.getCashBalance(amount.token)
            val amountLeftToPay = inputInvoice.amountRemaining
            check(cashBalance.quantity > 0L) {
                throw FlowException("Counterpary has no ${amount.token} to pay the invoice.")
            }
            check(cashBalance >= amount) {
                throw FlowException("Borrower has only $cashBalance but needs $amount to pay the invoice.")
            }
            check(amountLeftToPay >= amount) {
                throw FlowException("There's only $amountLeftToPay left to pay but you pledged $amount.")
            }

            // Stage 5. Create a pay command.
            val payCommand = Command(
                    InvoiceContract.Commands.PayInvoice(),
                    inputInvoice.participants.map { it.owningKey })

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.currentStep = BUILDING
            val builder = TransactionBuilder(notary)
                    .addInputState(invoiceToPay)
                    .addCommand(payCommand)

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            // We pay cash to the lenders obligation key.
            val lenderPaymentKey = inputInvoice.party
            val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, builder, amount, lenderPaymentKey)

            // Stage 8. Only add an output obligation state if the obligation has not been fully settled.
            val amountRemaining = amountLeftToPay - amount
            if (amountRemaining > Amount.zero(amount.token)) {
                val outputObligation = inputInvoice.pay(amount)
                builder.addOutputState(outputObligation, INVOICE_CONTRACT_ID)
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashSigningKeys + inputInvoice.counterparty.owningKey)

            // Stage 10. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            val session = initiateFlow(partyIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashSigningKeys + inputInvoice.counterparty.owningKey,
                    COLLECTING.childProgressTracker())
            )

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING

            // Finalising the transaction.
            return subFlow(FinalityFlow(stx, listOf(otherPartySession), CreateInvoiceFlow.Invoicer.Companion.FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Invoice transaction." using (output is Invoice)
                    val invoice = output as Invoice
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}


// *********
// * Factor Invoice Flow *
// *********



object FactorInvoice {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(private val linearId: UniqueIdentifier,
                    private val amount: Amount<Currency>,
                    private val borrower: Party,
                    private val lender: Party) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining Obligation from vault.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING)


            fun getInvoiceByLinearId(linearId: UniqueIdentifier): StateAndRef<Invoice> {
                val queryCriteria = QueryCriteria.LinearStateQueryCriteria(
                        null,
                        ImmutableList.of(linearId),
                        Vault.StateStatus.UNCONSUMED, null)

                return serviceHub.vaultService.queryBy<Invoice>(queryCriteria).states.singleOrNull()
                        ?: throw FlowException("Invoice with id $linearId not found.")
            }

            fun resolveIdentity(abstractParty: AbstractParty): Party {
                return serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {

            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = Initiator.Companion.PREPARATION
            val invoiceToFactor = getInvoiceByLinearId(linearId)
            val inputInvoice = invoiceToFactor.state.data

            val borrowerIdentity = resolveIdentity(borrower)
            val lenderIdentity = resolveIdentity(lender)
            val invoiceReference = serviceHub.vaultService.queryBy<Invoice>().states.single()
            val reference = invoiceReference.referenced()

            // Stage 3. This flow can only be initiated by the current recipient.
            check(borrowerIdentity == ourIdentity) {
                throw FlowException("Factor Invoice flow must be initiated by the party.")
            }

            // Stage 4. Check we have enought to issue the loan based on the requested loan amount.
            val cashBalance = serviceHub.getCashBalance(amount.token)
            val amountLeftToPay = inputInvoice.amountRemaining
            check(cashBalance.quantity > 0) {
                throw FlowException("Lender has no ${amount.token} to factor the invoice.")
            }
            check(cashBalance >= amount) {
                throw FlowException("Borrower has only $cashBalance but needs $amount to pay the invoice.")
            }
            check(amountLeftToPay >= amount) {
                throw FlowException("There's only $amountLeftToPay left to pay but you pledged $amount.")
            }

            // Stage 5. Create a pay command.
            val factorCommand = Command(
                    InvoiceContract.Commands.FactorInvoice(),
                    inputInvoice.participants.map { it.owningKey })

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.currentStep = BUILDING
            val builder = TransactionBuilder(notary)
                    .addReferenceState(reference)
                    .addInputState(invoiceToFactor)
                    .addCommand(factorCommand)

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            // We pay cash to the lenders obligation key.
            val borrowerPaymentKey = borrower
            val (_, cashSigningKeys) = Cash.generateSpend(serviceHub, builder, amount, borrowerPaymentKey)

            // Stage 8. Add a Loan Output State with a Reference State to the Invoice
            val amountRemaining = amountLeftToPay - amount
            if (amountRemaining > Amount.zero(amount.token)) {
                val outputLoan = inputInvoice.pay(amount)
                builder.addOutputState(outputLoan, INVOICE_CONTRACT_ID)
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashSigningKeys + inputInvoice.party.owningKey)

            // Stage 10. Get the Lender's signature.
            progressTracker.currentStep = COLLECTING
            val session = initiateFlow(lenderIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashSigningKeys + lender.owningKey,
                    COLLECTING.childProgressTracker())
            )

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING

            // Finalising the transaction.
            return subFlow(FinalityFlow(stx, listOf(otherPartySession), CreateInvoiceFlow.Invoicer.Companion.FINALISING_TRANSACTION.childProgressTracker()))
        }
    }


    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an Invoice transaction." using (output is Invoice)
                    val invoice = output as Invoice
                }
            }

            val txId = subFlow(signTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherSideSession = otherPartySession, expectedTxId = txId))
        }
    }

}