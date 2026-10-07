package team.siru.ecoguard.activity

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.siru.ecoguard.activity.dto.ActivityRecord
import team.siru.ecoguard.activity.dto.ActivityResult
import team.siru.ecoguard.activity.dto.ActivitySummary
import team.siru.ecoguard.activity.dto.MyActivityResponse
import team.siru.ecoguard.activity.dto.StudentActivityResponse
import team.siru.ecoguard.activity.dto.WeeklyActivityResponse
import team.siru.ecoguard.activity.dto.WeeklyDay
import team.siru.ecoguard.cleaningarea.Assignment
import team.siru.ecoguard.cleaningarea.AssignmentRepository
import team.siru.ecoguard.common.exception.BusinessException
import team.siru.ecoguard.common.exception.ErrorCode
import team.siru.ecoguard.schoolcalendar.VacationService
import team.siru.ecoguard.user.Role
import team.siru.ecoguard.user.UserRepository
import team.siru.ecoguard.verification.CleaningTimeWindow
import team.siru.ecoguard.verification.Verification
import team.siru.ecoguard.verification.VerificationRepository
import team.siru.ecoguard.verification.VerificationStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private const val CLEANING_DAYS_PER_WEEK = 5

@Service
class ActivityService(
    private val serviceTimeLogRepository: ServiceTimeLogRepository,
    private val userRepository: UserRepository,
    private val assignmentRepository: AssignmentRepository,
    private val verificationRepository: VerificationRepository,
    private val vacationService: VacationService,
    private val clock: Clock,
) {

    @Transactional
    fun accumulate(studentId: Long, minutes: Int, reason: ServiceTimeReason, areaName: String?, date: LocalDate = LocalDate.now(clock)) {
        val student = userRepository.findById(studentId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        serviceTimeLogRepository.save(
            ServiceTimeLog(student = student, minutes = minutes, reason = reason, date = date, areaName = areaName),
        )
    }

    /** 월별 활동 기록: 날짜/구역/인증 결과, 승인/반려/미제출 집계, 누적 시간 */
    @Transactional(readOnly = true)
    fun getMonthlyActivity(studentId: Long, month: YearMonth): MyActivityResponse {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val allDays = buildRecords(studentId, from, to, LocalDateTime.now(clock))
        val records = allDays
            .filter { it.result != ActivityResult.UPCOMING }
            .sortedByDescending { it.date }

        return MyActivityResponse(
            totalMinutes = serviceTimeLogRepository.sumMinutesByStudentId(studentId),
            year = month.year,
            month = month.monthValue,
            monthlyMinutes = records.sumOf { it.minutes },
            summary = ActivitySummary(
                completedDays = records.count { it.result == ActivityResult.APPROVED },
                requiredDays = allDays.size,
                approvedCount = records.count { it.result == ActivityResult.APPROVED },
                rejectedCount = records.count { it.result == ActivityResult.REJECTED },
                notSubmittedCount = records.count { it.result == ActivityResult.NOT_SUBMITTED },
            ),
            records = records,
        )
    }

    /** 홈 화면의 이번 주 청소 현황 (N/5일) */
    @Transactional(readOnly = true)
    fun getWeeklyActivity(studentId: Long): WeeklyActivityResponse {
        val now = LocalDateTime.now(clock)
        val monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val friday = monday.plusDays((CLEANING_DAYS_PER_WEEK - 1).toLong())
        val days = buildRecords(studentId, monday, friday, now).map { WeeklyDay(it.date, it.result) }
        // 방학인 평일은 인증할 수 없으므로 이번 주 필요 일수에서 뺀다.
        val vacationDays = (0 until CLEANING_DAYS_PER_WEEK).count { vacationService.isVacation(monday.plusDays(it.toLong())) }
        return WeeklyActivityResponse(
            weekStart = monday,
            weekEnd = friday,
            completedDays = days.count { it.result == ActivityResult.APPROVED },
            requiredDays = CLEANING_DAYS_PER_WEEK - vacationDays,
            days = days,
        )
    }

    @Transactional(readOnly = true)
    fun searchStudents(keyword: String?): List<StudentActivityResponse> {
        val students = if (keyword.isNullOrBlank()) {
            userRepository.findByRole(Role.STUDENT)
        } else {
            userRepository.findByNameContainingOrStudentNumberContaining(keyword, keyword)
                .filter { it.role == Role.STUDENT }
        }
        if (students.isEmpty()) {
            throw BusinessException(ErrorCode.NO_SEARCH_RESULT)
        }

        // 학생마다 쿼리를 날리지 않도록 필요한 값을 학생 전체에 대해 한 번씩만 조회한다.
        val ids = students.map { it.id }
        val areaNameByStudent = assignmentRepository.findAllWithAreaByStudentIdIn(ids)
            .groupBy { it.student.id }
            .mapValues { (_, assignments) -> assignments.first().area.name }
        val photoUrlByStudent = verificationRepository.findLatestByStudentIdIn(ids)
            .groupBy { it.student.id }
            .mapValues { (_, latest) -> latest.first().photoUrl }
        val attendanceByStudent = verificationRepository.countByStudentIdInAndStatus(ids, VerificationStatus.APPROVED).toLongMap()
        val minutesByStudent = serviceTimeLogRepository.sumMinutesByStudentIdIn(ids).toLongMap()

        return students.map { student ->
            StudentActivityResponse(
                studentId = student.id,
                studentNumber = student.studentNumber,
                name = student.name,
                area = areaNameByStudent[student.id],
                photoUrl = photoUrlByStudent[student.id],
                attendanceDays = attendanceByStudent[student.id] ?: 0L,
                totalMinutes = minutesByStudent[student.id] ?: 0L,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getStudentMonthlyActivity(studentId: Long, month: YearMonth): MyActivityResponse {
        userRepository.findById(studentId)
            .filter { it.role == Role.STUDENT }
            .orElseThrow { BusinessException(ErrorCode.STUDENT_NOT_FOUND) }
        return getMonthlyActivity(studentId, month)
    }

    /**
     * [from, to] 범위의 평일마다 인증 결과를 만든다. 제출한 날은 인증 상태를,
     * 배정 이후 인증 시간이 지났는데 제출하지 않은 평일은 NOT_SUBMITTED로 표시한다.
     * 주말과 방학(NEIS 학사일정)은 인증할 수 없으므로 제출이 없는 날은 제외한다. (공휴일은 아직 반영하지 않는다.)
     */
    private fun buildRecords(studentId: Long, from: LocalDate, to: LocalDate, now: LocalDateTime): List<ActivityRecord> {
        val verifications = verificationRepository
            .findByStudentIdAndVerificationDateBetweenOrderByVerificationDateAsc(studentId, from, to)
            .associateBy { it.verificationDate }
        val minutesByDate = serviceTimeLogRepository.findByStudentIdAndDateBetween(studentId, from, to)
            .groupBy { it.date }
            .mapValues { (_, logs) -> logs.sumOf { it.minutes } }
        val assignment = assignmentRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)
        val activeSince = activeSince(studentId, assignment)

        return generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .mapNotNull { date ->
                val verification = verifications[date]
                when {
                    verification != null -> verification.toRecord(minutesByDate[date] ?: 0)
                    activeSince == null || date.isBefore(activeSince) -> null
                    // 방학에는 인증이 막혀 있으므로 미제출/예정으로 세지 않는다. (이미 제출된 인증은 위에서 그대로 보여 준다.)
                    vacationService.isVacation(date) -> null
                    isWindowOver(date, assignment, now) ->
                        ActivityRecord(date, assignment?.area?.name, ActivityResult.NOT_SUBMITTED, null, null, 0)
                    else -> ActivityRecord(date, assignment?.area?.name, ActivityResult.UPCOMING, null, null, 0)
                }
            }
            .toList()
    }

    private fun activeSince(studentId: Long, assignment: Assignment?): LocalDate? {
        val firstVerificationDate = verificationRepository.findFirstByStudentIdOrderByVerificationDateAsc(studentId)?.verificationDate
        val assignedDate = assignment?.createdAt?.toLocalDate()
        return listOfNotNull(firstVerificationDate, assignedDate).minOrNull()
    }

    private fun isWindowOver(date: LocalDate, assignment: Assignment?, now: LocalDateTime): Boolean {
        val today = now.toLocalDate()
        return date.isBefore(today) ||
            (date == today && CleaningTimeWindow.hasEnded(assignment?.area?.cleanTime, now.toLocalTime()))
    }

    /** `select 학생ID, 집계값 ... group by 학생ID` 결과 행을 학생ID → 집계값 맵으로 바꾼다. */
    private fun List<Array<Any>>.toLongMap(): Map<Long, Long> =
        associate { row -> (row[0] as Number).toLong() to (row[1] as Number).toLong() }

    private fun Verification.toRecord(minutes: Int) = ActivityRecord(
        date = verificationDate,
        area = area.name,
        result = ActivityResult.valueOf(status.name),
        verificationId = id,
        photoUrl = photoUrl,
        minutes = minutes,
    )
}
