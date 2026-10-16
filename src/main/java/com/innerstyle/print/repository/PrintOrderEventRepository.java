package com.innerstyle.print.repository;

import com.innerstyle.print.entity.PrintOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrintOrderEventRepository extends JpaRepository<PrintOrderEvent, UUID> {

    List<PrintOrderEvent> findByPrintOrderIdOrderByCreatedAtAsc(UUID printOrderId);
}
