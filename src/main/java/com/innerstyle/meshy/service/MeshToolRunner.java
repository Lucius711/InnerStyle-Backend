package com.innerstyle.meshy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innerstyle.common.exception.BadRequestException;
import com.innerstyle.common.exception.UpstreamServiceException;
import com.innerstyle.meshy.dto.response.PrintabilityResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs the bundled Python mesh toolchain ({@code scripts/mesh_tools.py}, trimesh) for byte-level
 * mesh operations that are independent of any task. Kept separate from {@link PrintabilityService}
 * so callers (e.g. the task service adding a custom base) don't create a bean dependency cycle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshToolRunner {

    private final ObjectMapper objectMapper;

    @Value("${app.mesh.python-bin:python3}")
    private String pythonBin;

    @Value("${app.mesh.timeout-seconds:120}")
    private long timeoutSeconds;

    private Path scriptPath;

    @PostConstruct
    void extractScript() {
        try (InputStream in = new ClassPathResource("scripts/mesh_tools.py").getInputStream()) {
            Path tmp = Files.createTempFile("innerstyle-mesh_tools-runner-", ".py");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            this.scriptPath = tmp;
        } catch (IOException e) {
            log.error("Failed to extract mesh_tools.py: {}", e.getMessage());
        }
    }

    /**
     * Signature to engrave on the bottom of the base. {@code type} is {@code "strokes"} (hand-drawn
     * polylines, normalised coords) or {@code "text"}. Serialised to JSON for mesh_tools.py.
     */
    public record SignatureSpec(String type, String text, List<List<List<Double>>> strokes,
                                double penWidth, double depthRatio, boolean raised) {
        public boolean hasContent() {
            if ("strokes".equals(type)) {
                return strokes != null && !strokes.isEmpty();
            }
            return text != null && !text.isBlank();
        }
    }

    /**
     * Add a base/stand under a model and return the new GLB bytes (textures preserved).
     *
     * @param modelBytes    source model bytes
     * @param inputExt      source file extension (glb/gltf/obj/fbx/stl) so trimesh picks the loader
     * @param shape         cylinder | square | hexagon
     * @param heightRatio   base thickness as a fraction of model height
     * @param marginRatio   base footprint padding as a fraction of model footprint
     * @param color         base color as a #RRGGBB hex string (null = neutral grey)
     * @param signature     optional signature to engrave on the base (null = none)
     * @param baseColorPng  optional base-color texture map bytes to re-embed (null = none). Meshy
     *                      GLBs reference this map externally, so without re-embedding it the
     *                      trimesh round-trip would strip the figure's texture.
     */
    public byte[] addBase(byte[] modelBytes, String inputExt, String shape,
                          double heightRatio, double marginRatio, String color,
                          SignatureSpec signature, byte[] baseColorPng) {
        String ext = (inputExt == null || inputExt.isBlank()) ? "glb" : inputExt.toLowerCase();
        Path input = null;
        Path output = null;
        Path spec = null;
        Path texture = null;
        try {
            input = Files.createTempFile("innerstyle-base-in-", "." + ext);
            Files.write(input, modelBytes);
            output = Files.createTempFile("innerstyle-base-out-", ".glb");
            // The signature is structured/large, so it's passed as a JSON file path (arg 8), not
            // an inline CLI arg. Empty path = no signature (see mesh_tools.py `base`).
            String specPath = "";
            if (signature != null && signature.hasContent()) {
                spec = Files.createTempFile("innerstyle-sig-", ".json");
                Files.write(spec, objectMapper.writeValueAsBytes(signature));
                specPath = spec.toString();
            }
            texture = writeTexture(baseColorPng);
            run("base", input.toString(), output.toString(),
                shape, Double.toString(heightRatio), Double.toString(marginRatio),
                color == null ? "" : color, specPath, texture == null ? "" : texture.toString());
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.opFailed");
        } finally {
            quietDelete(input);
            quietDelete(output);
            quietDelete(spec);
            quietDelete(texture);
        }
    }

    /**
     * Remove a previously-baked base (geometry named {@code innerstyle_base}) from a model and
     * return the resulting GLB bytes. No-op if the model has no such base.
     */
    public byte[] stripBase(byte[] modelBytes, String inputExt, byte[] baseColorPng) {
        String ext = (inputExt == null || inputExt.isBlank()) ? "glb" : inputExt.toLowerCase();
        Path input = null;
        Path output = null;
        Path texture = null;
        try {
            input = Files.createTempFile("innerstyle-strip-in-", "." + ext);
            Files.write(input, modelBytes);
            output = Files.createTempFile("innerstyle-strip-out-", ".glb");
            texture = writeTexture(baseColorPng);
            run("strip", input.toString(), output.toString(),
                texture == null ? "" : texture.toString());
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.opFailed");
        } finally {
            quietDelete(input);
            quietDelete(output);
            quietDelete(texture);
        }
    }

    /**
     * Remove the pedestal that Meshy bakes under a generated figure (a flat disc the figure stands
     * on) and return the resulting GLB bytes. Heuristic + conservative: if no part clearly looks
     * like a base, the model is returned unchanged, so the figure is never mangled.
     */
    public byte[] stripGeneratedBase(byte[] modelBytes, String inputExt, byte[] baseColorPng) {
        String ext = (inputExt == null || inputExt.isBlank()) ? "glb" : inputExt.toLowerCase();
        Path input = null;
        Path output = null;
        Path texture = null;
        try {
            input = Files.createTempFile("innerstyle-debase-in-", "." + ext);
            Files.write(input, modelBytes);
            output = Files.createTempFile("innerstyle-debase-out-", ".glb");
            texture = writeTexture(baseColorPng);
            JsonNode result = run("debase", input.toString(), output.toString(),
                texture == null ? "" : texture.toString());
            // Ground-truth for "white figure" reports: logs where the colour lived in Meshy's GLB
            // (embedded image / external URL / vertex colours) and whether it survived the strip.
            log.info("debase texture diag: textureProvided={} src={} out={}",
                baseColorPng != null && baseColorPng.length > 0,
                result.path("srcTex"), result.path("outTex"));
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.opFailed");
        } finally {
            quietDelete(input);
            quietDelete(output);
            quietDelete(texture);
        }
    }

    /** Outcome of an in-place repair: the watertight GLB plus before/after printability stats. */
    public record RepairOutput(byte[] glb, PrintabilityResponse before, PrintabilityResponse after) {
    }

    /**
     * Repair a model into a watertight mesh and return it as GLB (geometry-only; textures are not
     * preserved by the repair), along with the before/after printability reports.
     *
     * @param modelBytes source model bytes
     * @param inputExt   source file extension so trimesh picks the loader
     */
    public RepairOutput repairToGlb(byte[] modelBytes, String inputExt) {
        String ext = (inputExt == null || inputExt.isBlank()) ? "glb" : inputExt.toLowerCase();
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("innerstyle-repair-in-", "." + ext);
            Files.write(input, modelBytes);
            output = Files.createTempFile("innerstyle-repair-out-", ".glb");
            JsonNode json = run("repair", input.toString(), output.toString());
            byte[] glb = Files.readAllBytes(output);
            return new RepairOutput(glb, toResponse(json.path("before")), toResponse(json.path("after")));
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.opFailed");
        } finally {
            quietDelete(input);
            quietDelete(output);
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

    private JsonNode run(String command, String input, String output, String... extra) {
        if (scriptPath == null) {
            throw new UpstreamServiceException("mesh.toolMissing");
        }
        try {
            List<String> args = new ArrayList<>();
            args.add(pythonBin);
            args.add(scriptPath.toString());
            args.add(command);
            args.add(input);
            if (output != null) {
                args.add(output);
            }
            if (extra != null) {
                Collections.addAll(args, extra);
            }
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.redirectErrorStream(false);
            Process process = pb.start();
            byte[] stdout = process.getInputStream().readAllBytes();
            byte[] stderr = process.getErrorStream().readAllBytes();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new UpstreamServiceException("mesh.opTimeout");
            }
            int exit = process.exitValue();
            // 128 + N == killed by signal N. 137 = SIGKILL (usually the OS OOM-killer reclaiming
            // memory in a constrained container), 143 = SIGTERM. These are resource failures, not a
            // malformed request, so surface them as a server-side error with an unambiguous log.
            if (exit == 137 || exit == 143) {
                log.warn("mesh_tools {} killed by signal (code {}) — likely out of memory on a "
                    + "large model; container may need more RAM", command, exit);
                throw new UpstreamServiceException("mesh.outOfMemory");
            }
            if (exit != 0) {
                log.warn("mesh_tools {} failed (code {}): {}", command, exit, new String(stderr));
                throw new BadRequestException("mesh.opFailed");
            }
            JsonNode json = objectMapper.readTree(new String(stdout).trim());
            if (json.has("error")) {
                log.warn("mesh_tools {} error: {}", command, json);
                throw new BadRequestException("mesh.opFailed");
            }
            return json;
        } catch (IOException e) {
            throw new UpstreamServiceException("mesh.opFailed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamServiceException("mesh.opFailed");
        }
    }

    /** Spill base-color texture bytes to a temp file for mesh_tools.py, or null when none given. */
    private static Path writeTexture(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        Path texture = Files.createTempFile("innerstyle-tex-", ".img");
        Files.write(texture, bytes);
        return texture;
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
