package com.softbei.scenicai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softbei.scenicai.dto.TtsVoiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TtsService {

    private static final String POWERSHELL = "C:\\WINDOWS\\System32\\WindowsPowerShell\\v1.0\\powershell.exe";

    private final ObjectMapper objectMapper;

    public List<TtsVoiceResponse> listVoices() {
        ensureWindows();

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
            log.error("解析 TTS 音色列表失败，原始输出：{}", output, exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "语音音色解析失败");
        }
    }

    public byte[] synthesize(String text, String voiceName) {
        ensureWindows();
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "朗读内容不能为空");
        }

        Path outputPath;
        try {
            outputPath = Files.createTempFile("scenic-mobile-tts-", ".wav");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建语音文件失败");
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
            log.error("读取 TTS 音频失败", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取语音文件失败");
        } finally {
            try {
                Files.deleteIfExists(outputPath);
            } catch (IOException exception) {
                log.warn("删除临时 TTS 音频失败：{}", outputPath, exception);
            }
        }
    }

    private void ensureWindows() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("windows")) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "当前服务端仅支持 Windows 语音");
        }
    }

    private String runPowerShell(String script, Map<String, String> environment) {
        ProcessBuilder builder = new ProcessBuilder(
                POWERSHELL,
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
                log.error("PowerShell TTS 执行失败，exitCode={}, output={}", exitCode, output);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "服务端语音生成失败");
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("执行 PowerShell TTS 被中断", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "服务端语音生成失败");
        } catch (IOException exception) {
            log.error("执行 PowerShell TTS 失败", exception);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "服务端语音生成失败");
        }
    }
}
