package com.innerstyle.meshy.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response of every Meshy "create task" endpoint: {@code { "result": "<task-id>" }}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MeshyCreateResponse {

    private String result;
}
