package team.siru.ecoguard

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EcoGuardServerApplication

fun main(args: Array<String>) {
    runApplication<EcoGuardServerApplication>(*args)
}
