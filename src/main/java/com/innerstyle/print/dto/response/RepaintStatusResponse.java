package com.innerstyle.print.dto.response;

import com.innerstyle.meshy.dto.response.MeshyTaskResponse;

/**
 * State of a staff AI repaint for an order.
 *
 * @param task    the repaint task (latest started, or the applied one); null when none exists
 * @param applied true when the order currently prints the repainted model (undo is possible)
 */
public record RepaintStatusResponse(MeshyTaskResponse task, boolean applied) {
}
