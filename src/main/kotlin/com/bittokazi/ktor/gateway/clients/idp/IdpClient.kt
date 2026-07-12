package com.bittokazi.ktor.gateway.clients.idp

import com.bittokazi.ktor.auth.domains.token.OauthTokenResponse
import com.bittokazi.ktor.auth.utils.getBaseUrl
import com.bittokazi.ktor.gateway.clients.idp.entity.RefreshTokenRequest
import com.bittokazi.ktor.gateway.clients.idp.entity.TokenIntrospectResult
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdpClient(
    private val client: HttpClient,
) {
    val log: Logger = LoggerFactory.getLogger(javaClass)

    init {
        log.info("[INFO] IdpClient is created")
    }

    suspend fun fetchAccessToken(
        call: ApplicationCall,
        code: String,
        oauthClient: OauthClient,
    ): CallResult<OauthTokenResponse, IdpClientErrorCode> {
        val response =
            client.submitForm(
                url = oauthClient.tokenUrl,
                formParameters =
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", "${call.getBaseUrl()}/gateway/callback")
                        append("client_id", oauthClient.clientId)
                        append("client_secret", oauthClient.clientSecret)
                    },
            )

        return when (response.status) {
            HttpStatusCode.OK ->
                CallResult.Success(
                    response.body<OauthTokenResponse>(),
                )
            HttpStatusCode.Unauthorized ->
                CallResult.Failure(
                    IdpClientErrorCode.UNAUTHORIZED,
                )
            else ->
                CallResult.Failure(
                    IdpClientErrorCode.BAD_REQUEST,
                )
        }
    }

    suspend fun fetchRefreshToken(
        refreshTokenRequest: RefreshTokenRequest,
        oauthClient: OauthClient,
    ): CallResult<OauthTokenResponse, IdpClientErrorCode> {
        val response: HttpResponse =
            client.submitForm(
                url = oauthClient.tokenUrl,
                formParameters =
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshTokenRequest.refreshToken)
                        append("client_id", oauthClient.clientId)
                        append("client_secret", oauthClient.clientSecret)
                    },
            )

        return when (response.status) {
            HttpStatusCode.OK ->
                CallResult.Success(
                    response.body<OauthTokenResponse>(),
                )
            HttpStatusCode.Unauthorized ->
                CallResult.Failure(
                    IdpClientErrorCode.UNAUTHORIZED,
                )
            else ->
                CallResult.Failure(
                    IdpClientErrorCode.BAD_REQUEST,
                )
        }
    }

    suspend fun tokenIntrospect(
        token: String,
        oauthClient: OauthClient,
    ): CallResult<TokenIntrospectResult, IdpClientErrorCode> {
        val response: HttpResponse =
            client.submitForm(
                url = oauthClient.introspectUrl,
                formParameters =
                    Parameters.build {
                        append("token", token)
                        append("client_id", oauthClient.clientId)
                        append("client_secret", oauthClient.clientSecret)
                    },
            )

        return when (response.status) {
            HttpStatusCode.OK ->
                CallResult.Success(
                    response.body<TokenIntrospectResult>(),
                )
            HttpStatusCode.Unauthorized ->
                CallResult.Failure(
                    IdpClientErrorCode.UNAUTHORIZED,
                )
            else ->
                CallResult.Failure(
                    IdpClientErrorCode.BAD_REQUEST,
                )
        }
    }
}

enum class IdpClientErrorCode {
    UNAUTHORIZED,
    BAD_REQUEST,
}
