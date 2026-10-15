package com.example.art_course.modules.payment.repository;

import com.example.art_course.modules.payment.entity.MoneyLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface MoneyLedgerRepository extends JpaRepository<MoneyLedger, UUID> {
    List<MoneyLedger> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);

    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN m.entryType IN ('RELEASE', 'WITHDRAW_UNLOCK') THEN m.amount
                WHEN m.entryType IN ('WITHDRAW_LOCK', 'REFUND') THEN -m.amount
                ELSE 0
            END
        ), 0)
        FROM MoneyLedger m
        WHERE m.teacherId = :teacherId
    """)
    BigDecimal calculateAvailableBalance(@Param("teacherId") UUID teacherId);
}
