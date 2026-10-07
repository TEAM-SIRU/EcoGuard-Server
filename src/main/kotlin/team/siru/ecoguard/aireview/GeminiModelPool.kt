package team.siru.ecoguard.aireview

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 여러 Gemini 모델 중 지금 쓸 수 있는 모델을 골라 준다.
 *
 * - 모델마다 **최근 1분 동안 보낸 요청 수**를 세어, 분당 한도에 가까운 모델은 피하고 여유 있는 모델을 먼저 쓴다.
 * - 429 나 장애가 난 모델은 일정 시간 **선택 대상에서 제외**(쿨다운)한다.
 * - 쓸 수 있는 모델이 하나도 없으면 가장 빨리 쓸 수 있게 되는 때까지 얼마나 기다려야 하는지 알려 준다.
 *
 * 서버 메모리에서만 세므로 서버가 한 대일 때 정확하고, 재시작하면 처음부터 센다.
 */
class GeminiModelPool(
    specs: List<GeminiModelSpec>,
    private val clock: Clock,
) {

    sealed interface Pick {
        /** 이 모델로 요청하면 된다. 요청 수는 이미 세어 두었다. */
        data class Use(val model: String) : Pick

        /** 지금 쓸 수 있는 모델이 없다. [duration] 뒤에 다시 물어보면 된다. */
        data class WaitFor(val duration: Duration) : Pick
    }

    data class ModelStatus(val model: String, val requestsInLastMinute: Int, val rpmLimit: Int, val coolingDown: Boolean)

    private class State(val spec: GeminiModelSpec) {
        val calls = ArrayDeque<Instant>()
        var cooldownUntil: Instant = Instant.EPOCH

        fun prune(now: Instant) {
            val oldest = now.minus(WINDOW)
            while (calls.isNotEmpty() && !calls.first().isAfter(oldest)) calls.removeFirst()
        }

        /** 한도에 가까워 다른 모델이 있으면 피하고 싶은 상태 */
        fun nearLimit(): Boolean = spec.rpmLimit > 0 && calls.size * 100 >= spec.rpmLimit * NEAR_LIMIT_PERCENT

        /** 이 모델을 다시 쓸 수 있게 되는 시각. 이미 쓸 수 있으면 과거 시각이다. */
        fun availableAt(): Instant {
            val rateReady = if (spec.rpmLimit > 0 && calls.size >= spec.rpmLimit) calls.first().plus(WINDOW) else Instant.EPOCH
            return maxOf(cooldownUntil, rateReady)
        }
    }

    private val states = specs.map(::State)

    val modelNames: List<String> get() = states.map { it.spec.name }

    @Synchronized
    fun acquire(): Pick {
        val now = clock.instant()
        states.forEach { it.prune(now) }

        val available = states.withIndex().filter { !it.value.availableAt().isAfter(now) }
        if (available.isNotEmpty()) {
            // 한도에 가깝지 않은 모델을 먼저, 같은 조건이면 설정한 우선순위 순서로 고른다.
            val chosen = available.minWith(compareBy({ if (it.value.nearLimit()) 1 else 0 }, { it.index })).value
            chosen.calls.addLast(now)
            return Pick.Use(chosen.spec.name)
        }
        val earliest = states.minOf { it.availableAt() }
        return Pick.WaitFor(Duration.between(now, earliest).coerceAtLeast(MIN_WAIT))
    }

    /** [model] 을 [duration] 동안 선택 대상에서 제외한다. 이미 더 오래 제외 중이면 그대로 둔다. */
    @Synchronized
    fun cooldown(model: String, duration: Duration) {
        val state = states.firstOrNull { it.spec.name == model } ?: return
        state.cooldownUntil = maxOf(state.cooldownUntil, clock.instant().plus(duration))
    }

    @Synchronized
    fun status(): List<ModelStatus> {
        val now = clock.instant()
        return states.map {
            it.prune(now)
            ModelStatus(it.spec.name, it.calls.size, it.spec.rpmLimit, it.cooldownUntil.isAfter(now))
        }
    }

    private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

    private companion object {
        val WINDOW: Duration = Duration.ofMinutes(1)
        val MIN_WAIT: Duration = Duration.ofMillis(50)

        /** 분당 한도의 80% 이상 썼으면 "한도에 가깝다"고 본다. */
        const val NEAR_LIMIT_PERCENT = 80
    }
}
