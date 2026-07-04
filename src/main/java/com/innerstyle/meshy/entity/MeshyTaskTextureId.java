package com.innerstyle.meshy.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Composite primary key for {@link MeshyTaskTexture}: one row per (task, map). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MeshyTaskTextureId implements Serializable {

    private UUID taskId;

    private String mapName;
}
