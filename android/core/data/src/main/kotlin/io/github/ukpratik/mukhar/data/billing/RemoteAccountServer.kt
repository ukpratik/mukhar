package io.github.ukpratik.mukhar.data.billing

import io.github.ukpratik.mukhar.data.net.DeleteMeRequestDto
import io.github.ukpratik.mukhar.data.net.EntitlementRequestDto
import io.github.ukpratik.mukhar.data.net.ErrorMapper
import io.github.ukpratik.mukhar.data.net.Outcome
import io.github.ukpratik.mukhar.data.net.Stage
import io.github.ukpratik.mukhar.data.net.MukharApi
import io.github.ukpratik.mukhar.data.prefs.ServerTalkQuota
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import javax.inject.Inject

/** [AccountServer] over the Worker API v1 (LLD §3), field for field with `worker/src/routes/entitlement.ts` and `me.ts`. */
class RemoteAccountServer @Inject constructor(
    private val api: MukharApi,
    private val errors: ErrorMapper,
) : AccountServer {

    override suspend fun verify(purchaseToken: String, productId: String): Outcome<ServerEntitlement> = call {
        val r = api.entitlement(EntitlementRequestDto(purchaseToken, productId))
        ServerEntitlement(r.premium, r.expiryTime, r.isTrial, r.autoRenewing)
    }

    override suspend fun status(): Outcome<ServerStatus> = call {
        val r = api.me()
        val q = r.quota
        val quota = if (q?.day != null && q.talksUsed != null && q.talksLimit != null) {
            runCatching { ServerTalkQuota(LocalDate.parse(q.day), q.talksUsed, q.talksLimit) }.getOrNull()
        } else {
            null
        }
        ServerStatus(ServerEntitlement(r.premium, r.expiryTime, r.isTrial, r.autoRenewing), quota)
    }

    override suspend fun deleteMe(): Outcome<Unit> = call {
        api.deleteMe(DeleteMeRequestDto())
        Unit
    }

    private suspend fun <T> call(block: suspend () -> T): Outcome<T> = try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Outcome.Err(errors.map(e, Stage.SERVER))
    }
}
