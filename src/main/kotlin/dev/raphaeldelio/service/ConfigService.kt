package dev.raphaeldelio.service

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.raphaeldelio.Logger
import dev.raphaeldelio.model.Config
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class ConfigService() {

    private val objectMapper = YAMLMapper().apply {
        findAndRegisterModules()
    }

    fun loadConfig(): Config {
        val providedFilePath = "/config/config.yaml"
        val configFilePath = if (File(providedFilePath).exists()) {
            Logger.info("Using provided configuration file: $providedFilePath")
            providedFilePath
        } else {
            Logger.warn("Provided file not found: $providedFilePath. Falling back to resources.")
            getResourceFilePath("config.yaml")
        }

        val file = File(configFilePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Configuration file not found: $configFilePath")
        }

        return objectMapper.readValue(file)
    }


    private fun getResourceFilePath(fileName: String): String {
        val resource = {}.javaClass.classLoader.getResource(fileName)
            ?: throw IllegalArgumentException("Resource file not found: $fileName")
        val tempFile = Files.createTempFile(Paths.get(System.getProperty("java.io.tmpdir")), "resource-", fileName)
        resource.openStream().use { input ->
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
        }
        return tempFile.toString()
    }
}