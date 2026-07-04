package com.innerstyle.meshy.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 3D-print readiness of a model, computed from its geometry (see {@code scripts/mesh_tools.py}).
 *
 * @param watertight        true when the mesh is closed (no boundary / non-manifold edges) — required to print.
 * @param volume            mesh volume in the model's coordinate units, cubed.
 * @param triangles         triangle (face) count.
 * @param boundaryEdges     edges used by only one face (open seams).
 * @param holes             number of connected boundary loops (open holes).
 * @param nonManifoldEdges  edges shared by more than two faces (bad for slicers).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrintabilityResponse(
        boolean watertight,
        double volume,
        int triangles,
        int boundaryEdges,
        int holes,
        int nonManifoldEdges) {
}
