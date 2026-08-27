package com.hermes.companion.net

import com.hermes.companion.backend.HermesBackend
import com.hermes.companion.domain.GatewayConnection

/**
 * The only public way to build an HTTP-backed gateway client.
 *
 * Keeping the class itself internal means `:app` never names an OkHttp type or
 * a request factory, so okhttp and `:transport:auth` stay `implementation`
 * dependencies of this module rather than leaking onto the app's classpath.
 */
fun httpHermesBackend(gateway: GatewayConnection): HermesBackend = HttpHermesBackend(gateway)
