# Camila CLM CorDapp

Camila is a Contract Lifecycle Management (CLM) network for inter-firm process automation.

### Camila CLM Network Setup


1) Install the Camila CLM CorDapp locally via Git:

```bash

git clone https://github.com/dappsinc/camila-cordapp

```

2) Deploy the Nodes


```bash

cd camila-cordapp && gradlew.bat deployNodes (Windows) OR ./gradlew deployNodes (Linux)

```

3) Run the Nodes

```bash

cd workflows
cd build 
cd nodes
runnodes.bat (Windows) OR ./runnodes (Linux)

```
4) Run the Spring Boot Server

```bash

cd ..
cd ..
cd server
../gradlew.bat bootRun -x test (Windows) OR ../gradlew bootRun -x test

```
The Camila CLM Network API Swagger will be running at http://localhost:8080/swagger-ui.html#/ in your browser

To change the name of your `organisation` or any other parameters, edit the `node.conf` file and repeat the above steps.

### Joining the Network

Add the following to the `node.conf` file:

`compatibilityZoneUrl="http://dapps.network:8080"`

This is the current network map and doorman server URL

1) Remove Existing Network Parameters and Certificates

```bash

cd build
cd nodes
cd Dapps
rm -rf persistence.mv.db nodeInfo-* network-parameters certificates additional-node-infos

```

2) Download the Network Truststore

```bash

curl -o /var/tmp/network-truststore.jks http://dsoa.network:8080//network-map/truststore

```

3) Initial Node Registration

```bash

java -jar corda.jar --initial-registration --network-root-truststore /var/tmp/network-truststore.jks --network-root-truststore-password trustpass

```
4) Start the Node

```bash

java -jar corda.jar

```


#### Node Configuration

- Corda version: Corda 4
- Vault SQL Database: PostgreSQL
- Cloud Service Provider: GCP
- JVM or Kubernetes (Docker)

### CLM Network States

Agreement States are transferred between party and counterparty on the network.

#### Agreements

```jsx

// *********
// * Agreement State *
// *********

data class Agreement(val agreementNumber: String,
                     val agreementName: String,
                     val agreementStatus: AgreementStatus,
                     val agreementType: AgreementType,
                     val totalAgreementValue: Int,
                     val party: Party,
                     val counterparty: Party,
                     val agreementStartDate: Date,
                     val agreementEndDate: Date,
                     val active: Boolean,
                     val createdAt: Instant,
                     val lastUpdated: Instant,
                     override val linearId: UniqueIdentifier = UniqueIdentifier()) 


```

The Agreement has the following business `flows` that can be called:

- `CreateAgreement` - Create an Agreement between your organization and a known counterparty on the DSOA
- `ActivateAgreement` - Activate the Agreement between your organization and a counterparty on the DSOA
- `TerminateAgreement` - Terminate an existing or active agreement
- `RenewAgreement` - Renew an existing agreement that is or is about to expire
- `ExpireAgreement` - Expire a currently active agreement between you and a counterparty

The `Agreement Status` and `Agreement Type` enums are listed as follows:

```jsx


@CordaSerializable
enum class AgreementStatus {
    REQUEST, APPROVAL_REQUIRED, APPROVED, IN_REVIEW, ACTIVATED, INEFFECT, REJECTED, RENEWED, TERMINATED, AMENDED, SUPERSEDED, EXPIRED
}

@CordaSerializable
enum class AgreementType {
    NDA, MSA, SLA, SOW
}


```


Roadmap:

The Junction State of the Agreement and the Line Item is the Agreement Line Item. 

In order to cope with the increased complexity that multiple state types introduce, we can use the concepts of high cohesion and low coupling.

The `Agreement` and the `Agreement Line Item` are bounded together by Command to that the creation of the states via a transaction occure simultaneously as well as a StateRef in the child state property.


The `Agreement Line Item` state is as follows:


```jsx

// ****************************
// * Agreement Line Item State *
// ****************************



data class AgreementLineItem (val agreement: Agreement,
                              val agreementNumber: String,
                              val agreementLineItemName: String,
                              val agreementLineItemStatus: AgreementLineItemStatus,
                              val agreementLineItemValue: Int,
                              val party: Party,
                              val counterparty: Party,
                              val lineItem: LineItem,
                              val active: Boolean,
                              val createdAt: String,
                              val lastUpdated: String,
                              override val linearId: UniqueIdentifier = UniqueIdentifier()) : LinearState, ContractState {

    override val participants: List<AbstractParty> get() = listOf(party, counterparty)

}

```
