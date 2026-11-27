package team.siru.ecoguard.notification

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "기기 플랫폼 · ANDROID · IOS · WEB")
enum class DevicePlatform { ANDROID, IOS, WEB }
