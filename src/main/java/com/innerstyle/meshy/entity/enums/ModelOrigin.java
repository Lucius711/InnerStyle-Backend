package com.innerstyle.meshy.entity.enums;

/**
 * Where the model's origin point sits when it is exported for download / printing.
 *
 * <ul>
 *   <li>{@code BOTTOM} – the mesh is centred on X/Z and its lowest point is placed at Y = 0,
 *       i.e. it "sits" on the print bed (the default for 3D printing).</li>
 *   <li>{@code CENTER} – the mesh's bounding-box centre is placed at the origin on all axes.</li>
 * </ul>
 */
public enum ModelOrigin {
    BOTTOM,
    CENTER
}
