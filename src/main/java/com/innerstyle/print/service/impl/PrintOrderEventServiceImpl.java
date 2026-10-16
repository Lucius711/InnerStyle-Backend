package com.innerstyle.print.service.impl;

import com.innerstyle.print.dto.response.PrintOrderTrackingResponse;
import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.PrintOrderEvent;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import com.innerstyle.print.repository.PrintOrderEventRepository;
import com.innerstyle.print.service.PrintOrderEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrintOrderEventServiceImpl implements PrintOrderEventService {

    private final PrintOrderEventRepository eventRepository;

    @Override
    @Transactional
    public void record(UUID printOrderId, PrintOrderStatus status, String note, UUID actorId) {
        PrintOrderEvent e = new PrintOrderEvent();
        e.setPrintOrderId(printOrderId);
        e.setStatus(status);
        e.setNote(note == null || note.isBlank() ? null : note.trim());
        e.setCreatedBy(actorId);
        eventRepository.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public PrintOrderTrackingResponse tracking(PrintOrder order) {
        var events = eventRepository.findByPrintOrderIdOrderByCreatedAtAsc(order.getId()).stream()
            .map(e -> new PrintOrderTrackingResponse.Event(e.getStatus().name(), e.getNote(), e.getCreatedAt()))
            .toList();
        return new PrintOrderTrackingResponse(order.getId(), order.getStatus().name(),
            order.getCarrier(), order.getTrackingCode(), events);
    }
}
