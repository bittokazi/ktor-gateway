package com.bittokazi.ktor.gateway.clients.idp

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.bittokazi.ktor.auth.domains.token.OauthTokenResponse
import com.bittokazi.ktor.auth.utils.getBaseUrl
import com.bittokazi.ktor.gateway.clients.idp.entity.RefreshTokenRequest
import com.bittokazi.ktor.gateway.clients.idp.entity.TokenValidationResult
import com.bittokazi.ktor.gateway.common.CallResult
import com.bittokazi.ktor.gateway.common.OauthClient
import com.bittokazi.ktor.gateway.security.services.CustomJwkProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.interfaces.RSAPublicKey

class IdpClient(
    private val client: HttpClient,
    private val customJwkProvider: CustomJwkProvider,
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

    fun validateToken(
        token: String,
        oauthClient: OauthClient,
    ): CallResult<TokenValidationResult, IdpClientErrorCode> {
        try {
            val decoded = JWT.decode(token)

            val jwk = customJwkProvider.getJwkProvider(oauthClient.issuer).get(decoded.keyId)

            val verifier =
                JWT.require(
                    Algorithm.RSA256(jwk.publicKey as RSAPublicKey, null),
                )
                    .withIssuer(oauthClient.issuer)
                    .build()

            verifier.verify(token)
            return CallResult.Success(
                TokenValidationResult(true),
            )
        } catch (_: Exception) {
            return CallResult.Failure(IdpClientErrorCode.UNAUTHORIZED)
        }
    }
}

enum class IdpClientErrorCode {
    UNAUTHORIZED,
    BAD_REQUEST,
}
