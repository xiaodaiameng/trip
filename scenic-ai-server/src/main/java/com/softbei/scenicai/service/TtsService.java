package com.softbei.scenicai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softbei.scenicai.dto.TtsVoiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TtsService {

    private final ObjectMapper objectMapper;

    @Value("${app.tts.provider:disabled}")
    private String provider;

    @Value("${app.tts.powershell-path:}")
    private String configuredPowerShellPath;

    public List<TtsVoiceResponse> listVoices() {
        if (!isWindowsProvider()) {
            return List.of();
        }

        String script = """
                Add-Type -AssemblyName System.Speech
                $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
                @($s.GetInstalledVoices() | ForEach-Object {
                  [PSCustomObject]@{
                    name = $_.VoiceInfo.Name
                    culture = $_.VoiceInfo.Culture.Name
                    gender = $_.VoiceInfo.Gender.ToString()
                  }
                }) | ConvertTo-Json -Compress
                """;

        String output = runPowerShell(script, Map.of());
        if (output.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(output, new TypeReference<>() {});
        } catch (IOException exception) {
            log.error("Failed to parse TTS voice list: {}", output, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse TTS voice list");
        }
    }

    public byte[] synthesize(String text, String voiceName) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TTS text must not be blank");
        }
        if (!isWindowsProvider()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Server TTS provider is not enabled");
        }

        Path outputPath;
        try {
            outputPath = Files.createTempFile("scenic-mobile-tts-", ".wav");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create temporary audio file");
        }

        try {
            String script = """
                    Add-Type -AssemblyName System.Speech
                    $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
                    if ($env:TTS_VOICE) {
                      $s.SelectVoice($env:TTS_VOICE)
                    }
                    $s.Rate = 0
                    $s.Volume = 100
                    $s.SetOutputToWaveFile($env:TTS_OUTPUT)
                    $s.Speak($env:TTS_TEXT)
                    $s.Dispose()
                    """;

            runPowerShell(script, Map.of(
                    "TTS_TEXT", text,
                    "TTS_OUTPUT", outputPath.toString(),
                    "TTS_VOICE", voiceName == null ? "" : voiceName
            ));
            return Files.readAllBytes(outputPath);
        } catch (IOException exception) {
            log.error("Failed to read TTS audio", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read TTS audio");
        } finally {
            try {
                Files.deleteIfExists(outputPath);
            } catch (IOException exception) {
                log.warn("Failed to delete temporary TTS audio: {}", outputPath, exception);
            }
        }
    }

    private boolean isWindowsProvider() {
        if (!"windows".equalsIgnoreCase(provider)) {
            return false;
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Windows TTS provider requires a Windows host");
        }
        return true;
    }

    private String runPowerShell(String script, Map<String, String> environment) {
        String powerShell = resolvePowerShell()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "PowerShell executable was not found"));

        ProcessBuilder builder = new ProcessBuilder(
                powerShell,
                "-NoProfile",
                "-NonInteractive",
                "-Command",
                script
        );
        builder.redirectErrorStream(true);
        builder.environment().putAll(environment);

        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("PowerShell TTS failed, exitCode={}, output={}", exitCode, output);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server TTS generation failed");
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server TTS generation was interrupted");
        } catch (IOException exception) {
            log.error("Failed to run PowerShell TTS", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Server TTS generation failed");
        }
    }

    private Optional<String> resolvePowerShell() {
        if (configuredPowerShellPath != null && !configuredPowerShellPath.isBlank() && Files.isExecutable(Path.of(configuredPowerShellPath))) {
            return Optional.of(configuredPowerShellPath);
        }
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null && !systemRoot.isBlank()) {
            Path defaultPath = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isExecutable(defaultPath)) {
                return Optional.of(defaultPath.toString());
            }
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(pathEnv.split(java.io.File.pathSeparator))
                .map(directory -> Path.of(directory, "powershell.exe"))
                .filter(Files::isExecutable)
                .map(Path::toString)
                .findFirst();
    }
}
