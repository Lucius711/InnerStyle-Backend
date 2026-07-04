package com.innerstyle.meshy.service;

import com.innerstyle.meshy.dto.response.PrintabilityResponse;

import java.util.UUID;

/**
 * Analyses a task's model for 3D-print readiness and auto-repairs it (watertight) via the bundled
 * Python mesh toolchain ({@code scripts/mesh_tools.py}, using trimesh + pymeshfix).
 */
public interface PrintabilityService {

    /** Compute the printability report for a task's model (GLB). */
    PrintabilityResponse analyze(UUID taskId);

    /** Auto-repair the model into a watertight, printable mesh of the requested format. */
    RepairResult repair(UUID taskId, String format);

    /** Repaired model bytes + the printability report after repair. */
    record RepairResult(byte[] bytes, String contentType, String filename, PrintabilityResponse after) {
    }
}
