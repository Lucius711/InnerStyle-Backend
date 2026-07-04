package com.innerstyle.meshy.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.meshy.dto.response.PrintabilityResponse;
import com.innerstyle.meshy.service.MeshyTaskService;
import com.innerstyle.meshy.service.PrintabilityService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link PrintabilityService}. Shells out to the bundled Python script (trimesh +
 * pymeshfix) to analyse / repair a model. The model is fetched as GLB via the existing proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrintabilityServiceImpl implements PrintabilityService {

    private final MeshyTaskService meshyTaskService;
    private final ObjectMapper objectMapper;

    @Value("${app.mesh.python-bin:python3}")
    private String pythonBin;

    @Value("${app.mesh.timeout-seconds:120}")
    private long timeoutSeconds;

    /** Extracted-once location of the bundled mesh_tools.py. */
    private Path scriptPath;

    private static final Map<String, String[]> FORMATS = Map.of(
        // requested -> { file extension, content type }
        "stl", new String[] {"stl", "model/stl"},
        "obj", new String[] {"obj", "text/plain"},
        "ply", new String[] {"ply", "application/octet-stream"},
        "glb", new String[] {"glb", "model/gltf-binary"});

    @PostConstruct
    void extractScript() {
        try (InputStream in = new ClassPathResource("scripts/mesh_tools.py").getInputStream()) {
            Path tmp = Files.createTempFile("innerstyle-mesh_tools-", ".py");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            this.scriptPath = tmp;
        } catch (IOException e) {
            log.error("Failed to extract mesh_tools.py: {}", e.getMessage());
        }
    }

    @Override
    public PrintabilityResponse analyze(UUID taskId) {
        Path model = writeModel(taskId);
        try {
            JsonNode out = run("analyze", model.toString(), null);
            return toResponse(out);
        } finally {
            quietDelete(model);
        }
    }

    @Override
    public RepairResult repair(UUID taskId, String format) {
        String[] fmt = FORMATS.getOrDefault(normalize(format), FORMATS.get("stl"));
        Path model = writeModel(taskId);
        Path output = null;
        try {
            output = Files.createTempFile("innerstyle-repaired-", "." + fmt[0]);
            JsonNode out = run("repair", model.toString(), output.toString());
            PrintabilityResponse after = toResponse(out.path("after"));
            byte[] bytes = Files.readAllBytes(output);
            return new RepairResult(bytes, fmt[1], "innerstyle-repaired." + fmt[0], after);
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.repairFailed");
        } finally {
            quietDelete(model);
            quietDelete(output);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Path writeModel(UUID taskId) {
        MeshyTaskService.ModelData glb = meshyTaskService.fetchModel(taskId, "glb");
        try {
            Path tmp = Files.createTempFile("innerstyle-model-", ".glb");
            Files.write(tmp, glb.bytes());
            return tmp;
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.repairFailed");
        }
    }

    /** Run the python script and return its parsed JSON stdout (throws on error). */
    private JsonNode run(String command, String input, String output, String... extra) {
        if (scriptPath == null) {
            throw new UpstreamServiceException("mesh.toolMissing");
        }
        try {
            java.util.List<String> args = new java.util.ArrayList<>();
            args.add(pythonBin);
            args.add(scriptPath.toString());
            args.add(command);
            args.add(input);
            if (output != null) {
                args.add(output);
            }
            if (extra != null) {
                java.util.Collections.addAll(args, extra);
            }
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new UpstreamServiceException("mesh.repairTimeout");
            }
            if (process.exitValue() != 0) {
                log.warn("mesh_tools {} failed (code {}): {}", command, process.exitValue(),
                    new String(stderr));
                throw new BadRequestException("mesh.repairFailed");
            }
            JsonNode json = objectMapper.readTree(new String(stdout).trim());
            if (json.has("error")) {
                log.warn("mesh_tools {} error: {}", command, json);
                throw new BadRequestException("mesh.repairFailed");
            }
            return json;
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.repairFailed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("mesh.repairFailed");
        }
    }

    private PrintabilityResponse toResponse(JsonNode n) {
        return new PrintabilityResponse(
            n.path("watertight").asBoolean(false),
            n.path("volume").asDouble(0),
            n.path("triangles").asInt(0),
            n.path("boundaryEdges").asInt(0),
            n.path("holes").asInt(0),
            n.path("nonManifoldEdges").asInt(0));
    }

    private static String normalize(String f) {
        return f == null ? "stl" : f.trim().toLowerCase();
    }

    private static void quietDelete(Path p) {
        if (p != null) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
