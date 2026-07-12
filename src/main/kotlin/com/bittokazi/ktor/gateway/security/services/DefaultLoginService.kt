package com.bittokazi.ktor.gateway.security.services

import com.bittokazi.ktor.auth.domains.token.OauthTokenResponse
import com.bittokazi.ktor.gateway.clients.idp.IdpClient
import com.bittokazi.ktor.gateway.clients.idp.IdpClientErrorCode
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import io.ktor.server.application.ApplicationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface LoginService {
    suspend fun fetchToken(
        call: ApplicationCall,
        oauthClient: OauthClient,
    ): CallResult<OauthTokenResponse, LoginServiceErrorCode>
}

class DefaultLoginService(
    private val idpClient: IdpClient,
) : LoginService {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("[INFO] LoginService is created")
    }

    companion object {
        private const val CODE = "code"
    }

    override suspend fun fetchToken(
        call: ApplicationCall,
        oauthClient: OauthClient,
    ): CallResult<OauthTokenResponse, LoginServiceErrorCode> {
        val code =
            call.parameters[CODE]
                ?: return CallResult.Failure(LoginServiceErrorCode.MISSING_CODE)

        val result =
            idpClient.fetchAccessToken(
                call = call,
                code = code,
                oauthClient = oauthClient,
            )

        return when (result) {
            is CallResult.Success -> CallResult.Success(result.outcome)
            is CallResult.Failure -> {
                when (result.errorCode) {
                    IdpClientErrorCode.UNAUTHORIZED -> CallResult.Failure(LoginServiceErrorCode.UNAUTHORIZED)
                    IdpClientErrorCode.BAD_REQUEST -> CallResult.Failure(LoginServiceErrorCode.BAD_REQUEST)
                }
            }
        }
    }
}

enum class LoginServiceErrorCode {
    MISSING_CODE,
    UNAUTHORIZED,
    BAD_REQUEST,
}
