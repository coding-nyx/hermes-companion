package com.hermes.companion.domain

enum class GatewayKind { Local, RemoteHttp, SshTunnel, CloudOAuth }

data class GatewayConnection(
    val id: String,
    val label: String,
    val kind: GatewayKind,
    val baseUrl: String,
    val authRef: String,
    val health: GatewayHealth = GatewayHealth.Unknown,
)

enum class GatewayHealth { Unknown, Healthy, Degraded, Down }
