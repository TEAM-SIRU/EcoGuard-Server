package team.siru.ecoguard.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "file-storage")
data class FileStorageProperties(
    val basePath: String = "uploads",
    val publicUrlPrefix: String = "/files",
)
