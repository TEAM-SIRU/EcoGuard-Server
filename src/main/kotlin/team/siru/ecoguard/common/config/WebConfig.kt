package team.siru.ecoguard.common.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import team.siru.ecoguard.common.storage.FileStorageProperties
import java.nio.file.Path

@Configuration
class WebConfig(
    private val fileStorageProperties: FileStorageProperties,
) : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val location = Path.of(fileStorageProperties.basePath).toAbsolutePath().toUri().toString()
        registry.addResourceHandler("${fileStorageProperties.publicUrlPrefix}/**")
            .addResourceLocations(location)
    }
}
