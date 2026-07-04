package com.innerstyle.print.repository;

import com.innerstyle.print.entity.PrintOrder;
import com.innerstyle.print.entity.enums.PrintOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PrintOrderRepository extends JpaRepository<PrintOrder, UUID> {

    Page<PrintOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // ----- Staff queries (all customers) -----

    Page<PrintOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<PrintOrder> findByStatusOrderByCreatedAtDesc(PrintOrderStatus status, Pageable pageable);
}
