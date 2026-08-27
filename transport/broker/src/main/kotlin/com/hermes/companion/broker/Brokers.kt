package com.hermes.companion.broker

/**
 * The only public door to a real broker, mirroring httpHermesBackend(): keeps
 * OkHttp an implementation detail of this module so callers never name an HTTP
 * type on their compile classpath.
 */
fun webSocketNodeBroker(
    url: String,
    token: String,
    hello: () -> BrokerHello,
): NodeBroker = WebSocketNodeBroker(url, token, hello)

/** Factory that keeps OkHttp off the caller's compile classpath. */
fun nodePairingClient(): NodePairingClient = NodePairingClient()
