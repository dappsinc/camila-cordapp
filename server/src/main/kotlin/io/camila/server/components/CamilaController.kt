/**
 *   Copyright 2020, Dapps Incorporated.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */


package io.camila.server.components

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController
import sun.security.timestamp.TSResponse
import org.springframework.web.bind.annotation.PostMapping
import com.github.manosbatsis.corbeans.spring.boot.corda.config.NodeParams
import io.camila.*
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import java.util.*
import javax.annotation.PostConstruct
import org.springframework.security.oauth2.client.OAuth2ClientContext
import org.springframework.security.oauth2.client.OAuth2RestTemplate
import org.springframework.security.oauth2.client.resource.OAuth2ProtectedResourceDetails
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.CrossOrigin
import io.camila.agreement.*
import io.camila.chat.Chat
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.AttachmentId
import net.corda.core.node.services.vault.AttachmentQueryCriteria
import net.corda.core.node.services.vault.Builder
import net.corda.core.utilities.getOrThrow
import org.springframework.core.io.InputStreamResource
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.web.multipart.MultipartFile
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest


/**
 * Carmen API Endpoints
 */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
@RestController
@RequestMapping("/api/{nodeName}")
class CamilaController() {

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }


    protected lateinit var defaultNodeName: String

    @Autowired
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    protected lateinit var services: Map<String, CamilaService>

    @PostConstruct
    fun postConstruct() {
        // if single node config, use the only node name as default, else reserve explicitly for cordform
        defaultNodeName = if (services.keys.size == 1) services.keys.first() else NodeParams.NODENAME_CORDFORM
        logger.debug("Auto-configured RESTful services for Corda nodes:: {}, default node: {}", services.keys, defaultNodeName)
    }

    /**
     * Handle both "api/sendMessage" and "api/message/{nodeName}" by using `cordform` as the default
     * node name to support optional dedicated server per node when using `runnodes`.
     */
    fun getService(optionalNodeName: Optional<String>): CamilaService {
        val nodeName = if (optionalNodeName.isPresent) optionalNodeName.get() else defaultNodeName
        return this.services.get("${nodeName}NodeService")
                ?: throw IllegalArgumentException("Node not found: $nodeName")
    }


    /** Maps an Agreement to a JSON object. */

    private fun Agreement.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "agreementNumber" to agreementNumber,
                "agreementName" to agreementName,
                "party" to party.name.organisation,
                "counterparty" to counterparty.name.organisation,
                "agreementType" to agreementType.toString(),
                "agreementStatus" to agreementStatus.toString(),
                "agreementStartDate" to agreementStartDate,
                "agreementEndDate" to agreementEndDate,
                "totalAgreementValue" to totalAgreementValue.toString(),
                "agreementHash" to agreementHash,
                "active" to active.toString(),
                "createdAt" to createdAt.toString(),
                "lastUpdated" to lastUpdated.toString(),
                "linearId" to linearId.toString())
    }


    /** Maps an Chat to a JSON object. */

    private fun Chat.Message.toJson(): Map<String, String> {
        return kotlin.collections.mapOf(
                "id" to id.toString(),
                "body" to body,
                "to" to to.name.organisation,
                "from" to from.name.organisation,
                "sentReceipt" to sentReceipt.toString(),
                "deliveredReceipt" to deliveredReceipt.toString(),
                "fromMe" to fromMe.toString(),
                "time" to time.toString())
    }

    /** Returns a list of existing Messages. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/getMessages", produces = arrayOf("application/json"))
    @ApiOperation(value = "Get Baton Messages")
    fun getMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Chat.Message::class.java).states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Get Messages by UserId */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/getMessages/userId", produces = arrayOf("application/json"))
    @ApiOperation(value = "Get Baton Messages by userId")
    fun getMessagesByUserId(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Chat.Message::class.java).states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Returns a list of received Messages. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/getReceivedMessages", produces = arrayOf("application/json"))
    @ApiOperation(value = "Get Received Baton Messages")
    fun getRecievedMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Chat.Message::class.java).states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }

    /** Returns a list of Sent Messages. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/getSentMessages", produces = arrayOf("application/json"))
    @ApiOperation(value = "Get Sent Baton Messages")
    fun getSentMessages(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val messageStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Chat.Message::class.java).states
        val messageStates = messageStateAndRefs.map { it.state.data }
        return messageStates.map { it.toJson() }
    }


    /** Send Message*/


    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/sendMessage")
    @ApiOperation(value = "Send a message to the target party")
    fun sendMessage(@PathVariable nodeName: Optional<String>,
                    @ApiParam(value = "The target party for the message")
                    @RequestParam(required = true) to: String,
                    @ApiParam(value = "The user Id for the message")
                    @RequestParam(required = true) userId: String,
                    @ApiParam(value = "The message text")
                    @RequestParam("body") body: String): ResponseEntity<Any?> {

        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'body' can not be null.\n")
        }

        if (to == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'recipient' missing or has wrong format.\n")
        }

        if (userId == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'userId' missing or has wrong format.\n")
        }


        val (status, message) = try {

            val result = getService(nodeName).sendMessage(to, userId, body)

            HttpStatus.CREATED to mapOf<String, String>(
                    "body" to "$body",
                    "to" to "$to",
                    "userId" to "$userId"
            )

        } catch (e: Exception) {
            logger.error("Error sending message to ${to}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Returns a list of existing Agreements. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/getAgreements")
    @ApiOperation(value = "Get Agreements")
    fun getAgreements(@PathVariable nodeName: Optional<String>): List<Map<String, String>> {
        val agreementStateAndRefs = this.getService(nodeName).proxy().vaultQuery(Agreement::class.java).states
        val agreementStates = agreementStateAndRefs.map { it.state.data }
        return agreementStates.map { it.toJson() }
    }


    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/getAllAgreements")
    @ApiOperation(value = "Get All Agreements")
    fun agreements(@PathVariable nodeName: Optional<String>): List<Agreement> {
        val agreementStatesAndRefs = this.getService(nodeName).proxy().vaultQuery(Agreement::class.java).states
        return agreementStatesAndRefs
                .map { agreementStateAndRef -> agreementStateAndRef.state.data }
                .map { state ->

                    Agreement(state.party.name.organisation,
                              state.counterparty.name.organisation,
                              state.agreementName,
                              state.agreementStatus,
                              state.agreementType,
                              state.totalAgreementValue,
                              state.party,
                              state.counterparty,
                              state.agreementStartDate,
                              state.agreementEndDate,
                              state.active,
                              state.createdAt,
                              state.lastUpdated,
                              state.linearId)
                }
    }


    /** Creates an Agreement. */

    /** Searchable PDF is mapped by agreement linearId **/
    /** Endpoint setup in BaaR OCR tool and State is created **/

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/createAgreement")
    @ApiOperation(value = "Create Agreement")
    fun createAgreement(@PathVariable nodeName: Optional<String>,
                        @RequestParam("agreementNumber") agreementNumber: String,
                        @RequestParam("agreementName") agreementName: String,
                        @RequestParam("agreementHash") agreementHash: String,
                        @RequestParam("agreementStatus") agreementStatus: AgreementStatus,
                        @RequestParam("agreementType") agreementType: AgreementType,
                        @RequestParam("totalAgreementValue") totalAgreementValue: Int,
                        @RequestParam("agreementStartDate") agreementStartDate: String,
                        // @DateTimeFormat(pattern = "yyyy-MM-dd", iso = DateTimeFormat.ISO.DATE_TIME)
                        @RequestParam("agreementEndDate") agreementEndDate: String,
                        // @DateTimeFormat(pattern = "yyyy-MM-dd", iso = DateTimeFormat.ISO.DATE_TIME)
                        @RequestParam("counterpartyName") counterpartyName: String?): ResponseEntity<Any?> {


        if (nodeName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }


        if (counterpartyName == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'counterPartyName' missing or has wrong format.\n")
        }



        val (status, message) = try {

            val result = getService(nodeName).createAgreement(agreementNumber, agreementName, agreementHash, agreementStatus, agreementType, totalAgreementValue, counterpartyName, agreementStartDate, agreementEndDate)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber",
                    "agreementName" to "$agreementName",
                    "agreementHash" to "$agreementHash",
                    "agreementStatus" to "$agreementStatus",
                    "agreementType" to "$agreementType",
                    "totalAgreementValue" to "$totalAgreementValue",
                    "agreementStartDate" to "$agreementStartDate",
                    "agreementEndDate" to "$agreementEndDate",
                    "party" to "$nodeName",
                    "counterpartyName" to "$counterpartyName"
            )

        } catch (e: Exception) {
            logger.error("Error sending Agreement to ${counterpartyName}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Activate Agreement. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/activateAgreement")
    @ApiOperation(value = "Activate Agreement")
    fun activateAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).activateAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error activating Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Terminate Agreement. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/terminateAgreement")
    @ApiOperation(value = "Terminate Agreement")
    fun terminateAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).terminateAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error terminating Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Renew Agreement. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/renewAgreement")
    @ApiOperation(value = "Renew Agreement")
    fun renweAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).renewAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error renewing Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Amend Agreement. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/amendAgreement")
    @ApiOperation(value = "Amend Agreement")
    fun amendAgreement(@PathVariable nodeName: Optional<String>, @RequestParam("agreementNumber") agreementNumber: String, request: HttpServletRequest): ResponseEntity<Any?> {
        val agreementNumber = request.getParameter("agreementNumber")
        val (status, message) = try {

            val result = getService(nodeName).amendAgreement(agreementNumber)

            HttpStatus.CREATED to mapOf<String, String>(
                    "agreementNumber" to "$agreementNumber"
            )

        } catch (e: Exception) {
            logger.error("Error amending Agreement ${agreementNumber}", e)
            e.printStackTrace()
            HttpStatus.BAD_REQUEST to e.message
        }
        return ResponseEntity<Any?>(message, status)
    }


    /** Upload the File. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @PostMapping(value = "/upload")
    @ApiOperation(value = "Upload Agreement")
    fun upload(@PathVariable nodeName: Optional<String>, @RequestParam file: MultipartFile, @RequestParam uploader: String): ResponseEntity<String> {
        val filename = file.originalFilename
        require(filename != null) { "File name must be set" }
        val hash: SecureHash = if (!(file.contentType == "zip" || file.contentType == "jar")) {
            uploadZip(nodeName, file.inputStream, uploader, filename!!)
        } else {
            this.getService(nodeName).proxy().uploadAttachmentWithMetadata(
                    jar = file.inputStream,
                    uploader = uploader,
                    filename = filename!!
            )
        }
        return ResponseEntity.created(URI.create("attachments/$hash")).body("Attachment uploaded with hash - $hash")
    }

    private fun uploadZip(nodeName: Optional<String>, inputStream: InputStream, uploader: String, filename: String): AttachmentId {
        val zipName = "$filename-${UUID.randomUUID()}.zip"
        FileOutputStream(zipName).use { fileOutputStream ->
            ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                val zipEntry = ZipEntry(filename)
                zipOutputStream.putNextEntry(zipEntry)
                inputStream.copyTo(zipOutputStream, 1024)
            }
        }
        return FileInputStream(zipName).use { fileInputStream ->
            val hash = this.getService(nodeName).proxy().uploadAttachmentWithMetadata(
                    jar = fileInputStream,
                    uploader = uploader,
                    filename = filename
            )
            Files.deleteIfExists(Paths.get(zipName))
            hash
        }
    }


    /** Download the File. */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network", "https://camila.network", "http://localhost:8080", "http://localhost:3000", "https://statesets.com"])
    @GetMapping(value = "/download")
    @ApiOperation(value = "Download Agreement")
    fun downloadByName(@PathVariable nodeName: Optional<String>, @RequestParam name: String): ResponseEntity<InputStreamResource> {
        val attachmentIds: List<AttachmentId> = this.getService(nodeName).proxy().queryAttachments(
                AttachmentQueryCriteria.AttachmentsQueryCriteria(filenameCondition = Builder.equal(name)),
                null
        )
        val inputStreams = attachmentIds.map { this.getService(nodeName).proxy().openAttachment(it) }
        val zipToReturn = if (inputStreams.size == 1) {
            inputStreams.single()
        } else {
            combineZips(inputStreams, name)
        }
        return ResponseEntity.ok().header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"$name.zip\""
        ).body(InputStreamResource(zipToReturn))
    }

    private fun combineZips(inputStreams: List<InputStream>, filename: String): InputStream {
        val zipName = "$filename-${UUID.randomUUID()}.zip"
        FileOutputStream(zipName).use { fileOutputStream ->
            ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                inputStreams.forEachIndexed { index, inputStream ->
                    val zipEntry = ZipEntry("$filename-$index.zip")
                    zipOutputStream.putNextEntry(zipEntry)
                    inputStream.copyTo(zipOutputStream, 1024)
                }
            }
        }
        return try {
            FileInputStream(zipName)
        } finally {
            Files.deleteIfExists(Paths.get(zipName))
        }
    }































    /*

    /** Send UPI Payment */

    @CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network"])
    @PostMapping(value = "/pay")
    fun sendPayment(@RequestParam("pa") pa: String,
                    @RequestParam("pn") pn: String,
                    @RequestParam("mc") mc: String,
                    @RequestParam("tid") tid: String,
                    @RequestParam("tr") tr: String,
                    @RequestParam("tn") tn: String,
                    @RequestParam("am") am: String,
                    @RequestParam("mam") mam: String,
                    @RequestParam("cu") cu: String,
                    @RequestParam("url") url : String): ResponseEntity<Any?> {

        if (tid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'tid' can not be null.\n")
        }

        if (pn == null) {
            return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'pn' missing or has wrong format.\n")
        }

        val counterparty = CordaX500Name.parse(pn)


        val pn = proxy.wellKnownPartyFromX500Name(counterparty)
                ?: return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Party named $pn cannot be found.\n")

        val (status, message) = try {


            val flowHandle = proxy.startFlowDynamic(SendPaymentFlow.InitiatePaymentRequest::class.java, pa, pn, mc, tid, tr, tn, am, mam, cu, url)

            val result = flowHandle.use { it.returnValue.getOrThrow() }

            HttpStatus.CREATED to "Payment sent."

        } catch (e: Exception) {
            HttpStatus.BAD_REQUEST to e.message
        }
        logger.info(message)
        return ResponseEntity<Any?>(message, status)
    }


/** Send Proxy Re-encryption Policy */

@CrossOrigin(origins = ["https://dapps.ngrok.io", "https://dsoa.network"])
@PostMapping(value = "/policy")
fun sendPolicy(@RequestParam("alice") alice: String,
                @RequestParam("enrico") enrico: String,
                @RequestParam("bob") bob: String,
                @RequestParam("policyName") policyName: String,
                @RequestParam("policyExpirationDate") policyExpirationDate: String,
                @RequestParam("policyPassword") policyPassword: String,
                @RequestParam("policyId") policyId: String): ResponseEntity<Any?> {

    if (policyId == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Query parameter 'tid' can not be null.\n")
    }

    if (bob == null) {
        return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Query parameter 'pn' missing or has wrong format.\n")
    }

    val counterparty = CordaX500Name.parse(bob)

    val bob = proxy.wellKnownPartyFromX500Name(counterparty)
            ?: return ResponseEntity.status(TSResponse.BAD_REQUEST).body("Party named $bob cannot be found.\n")

    val (status, message) = try {


        val flowHandle = proxy.startFlowDynamic(SendPolicyFlow.InitiatePolicyRequest::class.java, alice, enrico, bob, policyName, policyExpirationDate, policyPassword, policyId)

        val result = flowHandle.use { it.returnValue.getOrThrow() }

        HttpStatus.CREATED to "Payment sent."

    } catch (e: Exception) {
        HttpStatus.BAD_REQUEST to e.message
    }
    logger.info(message)
    return ResponseEntity<Any?>(message, status)
}

*/
}
