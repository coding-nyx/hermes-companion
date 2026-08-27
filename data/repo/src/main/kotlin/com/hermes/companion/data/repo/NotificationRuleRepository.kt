package com.hermes.companion.data.repo

import com.hermes.companion.domain.NotificationAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * T5B: Per-app notification routing rules.
 *
 * Each rule overrides the global default action for a single package.
 * "Reply-with-rules" rules (matched against title+text) are a single regex
 * list, not per-package - rules apply globally for the [ReplyWithRules]
 * action.
 *
 * Why an in-memory MutableStateFlow for v0.2: rules live in the same
 * process as the NLS (which is in :node). Persistence ships in v0.3 once
 * the user feedback tells us whether the per-package override UX sticks.
 */
data class PackageRule(
    val packageName: String,
    val action: NotificationAction,
)

interface NotificationRuleRepository {
    fun rules(): Flow<List<PackageRule>>
    fun replyRules(): Flow<List<Regex>>
    suspend fun setPackageAction(packageName: String, action: NotificationAction)
    suspend fun removePackageAction(packageName: String)
    suspend fun addReplyRule(regex: Regex)
    suspend fun removeReplyRule(index: Int)
}

class InMemoryNotificationRuleRepository : NotificationRuleRepository {
    private val _rules = MutableStateFlow<Map<String, NotificationAction>>(emptyMap())
    private val _reply = MutableStateFlow<List<Regex>>(emptyList())

    override fun rules(): Flow<List<PackageRule>> =
        _rules.asStateFlow().let { f ->
            kotlinx.coroutines.flow.flow {
                f.collect { map -> emit(map.map { (pkg, act) -> PackageRule(pkg, act) }.sortedBy { it.packageName }) }
            }
        }

    override fun replyRules(): Flow<List<Regex>> = _reply.asStateFlow()

    override suspend fun setPackageAction(packageName: String, action: NotificationAction) {
        _rules.value = _rules.value + (packageName to action)
    }

    override suspend fun removePackageAction(packageName: String) {
        _rules.value = _rules.value - packageName
    }

    override suspend fun addReplyRule(regex: Regex) {
        _reply.value = _reply.value + regex
    }

    override suspend fun removeReplyRule(index: Int) {
        val list = _reply.value
        if (index in list.indices) {
            _reply.value = list.toMutableList().also { it.removeAt(index) }
        }
    }
}
