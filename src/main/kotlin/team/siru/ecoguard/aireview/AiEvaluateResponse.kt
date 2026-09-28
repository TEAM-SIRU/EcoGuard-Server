package team.siru.ecoguard.aireview

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AiEvaluateResponse(
    val decision: String? = null,
    @JsonProperty("is_passed")
    val isPassed: Boolean? = null,
    @JsonProperty("fail_reasons")
    val failReasons: List<String>? = null,
    val dustpan: DustpanResult? = null,
    val zone: ZoneResult? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DustpanResult(
    val detected: Boolean? = null,
    @JsonProperty("trash_detected")
    val trashDetected: Boolean? = null,
    @JsonProperty("trash_inside")
    val trashInside: Boolean? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ZoneResult(
    @JsonProperty("requested_zone_id")
    val requestedZoneId: String? = null,
    @JsonProperty("recognized_zone_id")
    val recognizedZoneId: String? = null,
    val recognized: Boolean? = null,
    @JsonProperty("anomaly_detected")
    val anomalyDetected: Boolean? = null,
)
