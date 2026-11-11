package com.otusfoss.mail.feature.compose.data

import com.otusfoss.core.jmap.JmapConnections
import com.otusfoss.core.jmap.JmapException
import com.otusfoss.core.jmap.SetMethod
import com.otusfoss.core.jmap.login.LoginStoreException
import com.otusfoss.mail.core.mail.IdentityType
import com.otusfoss.mail.core.mail.mailAccount
import com.otusfoss.mail.core.messages.domain.SendIdentity
import com.otusfoss.mail.core.messages.domain.Signature
import com.otusfoss.mail.feature.compose.domain.SignatureRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Writes `htmlSignature` and `textSignature` on the identity with one `Identity/set` update. */
class JmapSignatures(
    private val connections: JmapConnections,
) : SignatureRepository {
    override suspend fun save(
        identity: SendIdentity,
        signature: Signature,
    ): Result<Unit> =
        try {
            val account =
                connections.mailAccount(identity.accountId) ?: error("No account for ${identity.accountId}")
            val patch =
                buildJsonObject {
                    put("htmlSignature", signature.html)
                    put("textSignature", signature.text)
                }
            account.connection
                .call(SetMethod(IdentityType, account.id, update = mapOf(identity.id to patch)))
                .notUpdated[identity.id]
                ?.let { Result.failure(IllegalStateException(it.type)) }
                ?: Result.success(Unit)
        } catch (error: JmapException) {
            Result.failure(error)
        } catch (error: LoginStoreException) {
            Result.failure(error)
        } catch (error: IllegalStateException) {
            Result.failure(error)
        }
}
