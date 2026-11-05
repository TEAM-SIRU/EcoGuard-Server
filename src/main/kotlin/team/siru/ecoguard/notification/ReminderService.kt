package team.siru.ecoguard.notification

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.cleaningarea.CleaningArea
import team.siru.ecoguard.schoolcalendar.VacationService
import team.siru.ecoguard.verification.CleaningTimeWindow
import team.siru.ecoguard.verification.VerificationRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 청소 인증 리마인더. 구역마다 인증 시간이 다를 수 있어서, 매분 호출되어 "지금이 어느 구역의 알림 시각인가"를 판단한다.
 * - 시작 알림: 인증 시작 [START_LEAD_MINUTES]분 전. 배정된 학생 모두에게.
 * - 마감 알림: 인증 마감 [DEADLINE_LEAD_MINUTES]분 전. 아직 제출하지 않은 학생에게만.
 * 주말과 방학에는 보내지 않는다.
 */
@Service
class ReminderService(
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
    private val vacationService: VacationService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** [now] 가 속한 분(分)에 알림 시각인 구역의 학생들에게 리마인더를 보낸다. 보낸 학생 수를 돌려준다. */
    @Transactional(readOnly = true)
    fun sendReminders(now: LocalDateTime): Int {
        val today = now.toLocalDate()
        if (!CleaningTimeWindow.isCertificationDay(today) || vacationService.isVacation(today)) return 0

        val time = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        val studentIdsByArea = assignmentRepository.findActiveAreaAssignments()
            .groupBy({ it[0] as CleaningArea }, { it[1] as Long })

        var sent = 0
        studentIdsByArea.forEach { (area, rawIds) ->
            val (start, end) = CleaningTimeWindow.parse(area.cleanTime)
            val studentIds = rawIds.distinct()
            when (time) {
                start.minusMinutes(START_LEAD_MINUTES) -> {
                    publish(NotificationEvent.cleaningStartReminder(studentIds, area.id, area.name, end), studentIds)
                    sent += studentIds.size
                }

                end.minusMinutes(DEADLINE_LEAD_MINUTES) -> {
                    val submitted = verificationRepository.findSubmittedStudentIds(today, studentIds).toSet()
                    val pending = studentIds.filterNot { it in submitted }
                    if (pending.isNotEmpty()) {
                        publish(NotificationEvent.cleaningDeadlineReminder(pending, area.id, area.name, end), pending)
                        sent += pending.size
                    }
                }
            }
        }
        return sent
    }

    private fun publish(event: NotificationEvent, targets: List<Long>) {
        log.info("청소 리마인더를 보냅니다 ({}, 대상 {}명)", event.title, targets.size)
        eventPublisher.publishEvent(event)
    }

    private companion object {
        const val START_LEAD_MINUTES = 10L
        const val DEADLINE_LEAD_MINUTES = 15L
    }
}
