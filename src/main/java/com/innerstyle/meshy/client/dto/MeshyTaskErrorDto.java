package com.innerstyle.meshy.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Error detail returned by Meshy for failed tasks. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MeshyTaskErrorDto {

    private String message;
}
