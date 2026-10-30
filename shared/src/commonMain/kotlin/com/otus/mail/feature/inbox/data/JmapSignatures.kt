package com.otus.mail.feature.inbox.data

import com.otus.jmap.JmapConnections
import com.otus.jmap.JmapException
import com.otus.jmap.SetMethod
import com.otus.jmap.login.LoginStore
import com.otus.jmap.login.LoginStoreException
import com.otus.mail.core.mail.IdentityType
import com.otus.mail.feature.inbox.domain.SendIdentity
import com.otus.mail.feature.inbox.domain.Signature
import com.otus.mail.feature.inbox.domain.SignatureRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Writes `htmlSignature` and `textSignature` on the identity with one `Identity/set` update. */
class JmapSignatures(
    private val connections: JmapConnections,
    private val logins: LoginStore,
) : SignatureRepository {
    override suspend fun save(
        identity: SendIdentity,
        signature: Signature,
    ): Result<Unit> =
        try {
            val account =
                mailAccount(connections, logins, identity.accountId) ?: error("No account for ${identity.accountId}")
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
