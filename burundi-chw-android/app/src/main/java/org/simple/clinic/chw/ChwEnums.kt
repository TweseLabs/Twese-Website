package org.simple.clinic.chw

import androidx.room.TypeConverter
import org.simple.clinic.chw.screening.DangerSign
import org.simple.clinic.chw.screening.ScreeningOutcome
import org.simple.clinic.chw.screening.SpecialPopulation

/**
 * Enums used by the CHW module. Each carries a stable snake_case [code] that is used both in
 * the database and in the API contract (docs/burundi/api-contract.md). Unknown codes coming from
 * a newer server map to a safe UNKNOWN / conservative value instead of crashing.
 */
interface CodedEnum {
  val code: String
}

inline fun <reified T> codeToEnum(code: String?, fallback: T): T where T : Enum<T>, T : CodedEnum =
    enumValues<T>().firstOrNull { it.code == code } ?: fallback

enum class AssignmentStatus(override val code: String) : CodedEnum {
  PENDING_SERVER_CONFIRMATION("pending_server_confirmation"),
  ACTIVE("active"),
  SUSPENDED("suspended"),
  ENDED("ended"),
  UNKNOWN("unknown")
}

enum class AssignmentEndReason(override val code: String) : CodedEnum {
  REASSIGNED("reassigned"),
  TRANSFERRED_OUT("transferred_out"),
  DECLINED_CHW_FOLLOW_UP("declined_chw_follow_up"),
  MOVED_AWAY("moved_away"),
  DIED("died"),
  UNKNOWN("unknown")
}

enum class ScreeningLocationType(override val code: String) : CodedEnum {
  HOUSEHOLD("household"),
  COMMUNITY_SCREENING_POINT("community_screening_point"),
  OTHER("other"),
  UNKNOWN("unknown")
}

enum class ReferralPriority(override val code: String) : CodedEnum {
  ROUTINE("routine"),
  PRIORITY("priority"),
  URGENT("urgent"),
  UNKNOWN("unknown")
}

enum class FollowUpTaskType(override val code: String) : CodedEnum {
  REPEAT_SCREENING("repeat_screening"),
  CHECK_CLINIC_CONFIRMATION("check_clinic_confirmation"),
  URGENT_REFERRAL_CHECK("urgent_referral_check"),
  ROUTINE_FOLLOW_UP("routine_follow_up"),
  RESCREEN("rescreen"),
  UNKNOWN("unknown")
}

enum class FollowUpTaskStatus(override val code: String) : CodedEnum {
  OPEN("open"),
  COMPLETED("completed"),
  CANCELLED("cancelled"),
  UNKNOWN("unknown")
}

enum class ContactMethod(override val code: String) : CodedEnum {
  HOME_VISIT("home_visit"),
  PHONE_CALL("phone_call"),
  COMMUNITY_POINT("community_point"),
  UNKNOWN("unknown")
}

/** Result of a CHW contact attempt. Deliberately distinct categories – see FollowUpStatus. */
enum class ContactOutcome(override val code: String) : CodedEnum {
  REACHED("reached"),
  NOT_AT_HOME("not_at_home"),
  PHONE_UNREACHABLE("phone_unreachable"),
  DECLINED("declined"),
  TRANSFERRED_OUT("transferred_out"),
  MOVED_AWAY("moved_away"),
  UNKNOWN("unknown");

  val isSuccessful: Boolean get() = this == REACHED
  val isFailedAttempt: Boolean get() = this == NOT_AT_HOME || this == PHONE_UNREACHABLE
}

/** Patient-reported medicine availability. This is never a prescription record. */
enum class ReportedMedicineAvailability(override val code: String) : CodedEnum {
  HAS_MEDICINE_TAKING_AS_ADVISED("has_taking"),
  HAS_MEDICINE_NOT_TAKING("has_not_taking"),
  RAN_OUT("ran_out"),
  COULD_NOT_GET_AT_CLINIC_OR_PHARMACY("could_not_get"),
  NOT_ON_MEDICINE("not_on_medicine"),
  NOT_ASKED("not_asked"),
  UNKNOWN("unknown")
}

object CodeSets {
  fun encode(codes: Collection<String>): String = codes.sorted().joinToString(",")
  fun decode(value: String?): List<String> = value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
}

class ChwRoomTypeConverters {
  @TypeConverter fun fromAssignmentStatus(v: AssignmentStatus?) = v?.code
  @TypeConverter fun toAssignmentStatus(v: String?) = v?.let { codeToEnum(it, AssignmentStatus.UNKNOWN) }

  @TypeConverter fun fromAssignmentEndReason(v: AssignmentEndReason?) = v?.code
  @TypeConverter fun toAssignmentEndReason(v: String?) = v?.let { codeToEnum(it, AssignmentEndReason.UNKNOWN) }

  @TypeConverter fun fromLocationType(v: ScreeningLocationType?) = v?.code
  @TypeConverter fun toLocationType(v: String?) = v?.let { codeToEnum(it, ScreeningLocationType.UNKNOWN) }

  @TypeConverter fun fromReferralPriority(v: ReferralPriority?) = v?.code
  @TypeConverter fun toReferralPriority(v: String?) = v?.let { codeToEnum(it, ReferralPriority.UNKNOWN) }

  @TypeConverter fun fromTaskType(v: FollowUpTaskType?) = v?.code
  @TypeConverter fun toTaskType(v: String?) = v?.let { codeToEnum(it, FollowUpTaskType.UNKNOWN) }

  @TypeConverter fun fromTaskStatus(v: FollowUpTaskStatus?) = v?.code
  @TypeConverter fun toTaskStatus(v: String?) = v?.let { codeToEnum(it, FollowUpTaskStatus.UNKNOWN) }

  @TypeConverter fun fromContactMethod(v: ContactMethod?) = v?.code
  @TypeConverter fun toContactMethod(v: String?) = v?.let { codeToEnum(it, ContactMethod.UNKNOWN) }

  @TypeConverter fun fromContactOutcome(v: ContactOutcome?) = v?.code
  @TypeConverter fun toContactOutcome(v: String?) = v?.let { codeToEnum(it, ContactOutcome.UNKNOWN) }

  @TypeConverter fun fromMedicineAvailability(v: ReportedMedicineAvailability?) = v?.code
  @TypeConverter fun toMedicineAvailability(v: String?) = v?.let { codeToEnum(it, ReportedMedicineAvailability.UNKNOWN) }

  @TypeConverter fun fromScreeningOutcome(v: ScreeningOutcome?) = v?.code
  @TypeConverter fun toScreeningOutcome(v: String?) = v?.let { ScreeningOutcome.fromCode(it) }

  @TypeConverter fun fromSpecialPopulation(v: SpecialPopulation?) = v?.code
  @TypeConverter fun toSpecialPopulation(v: String?) = v?.let { SpecialPopulation.fromCode(it) }

  @TypeConverter fun fromDangerSigns(v: Set<DangerSign>?) = v?.let { set -> CodeSets.encode(set.map { it.code }) }
  @TypeConverter fun toDangerSigns(v: String?): Set<DangerSign>? = v?.let { CodeSets.decode(it).mapNotNull(DangerSign::fromCode).toSet() }
}
