package com.example.dblab.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MyBatis 매퍼 예제 — JPA와 같은 트랜잭션 안에서도 사용 가능.
 */
@Mapper
public interface OrderMapper {

    @Select("""
        SELECT
            o.id,
            o.customer_id   AS customerId,
            o.total_amount  AS totalAmount,
            o.status,
            o.created_at    AS createdAt
        FROM orders o
        WHERE o.status = #{status}
          AND o.created_at >= #{since}
        ORDER BY o.created_at DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> findRecentByStatus(
        @Param("status") String status,
        @Param("since") LocalDateTime since,
        @Param("limit") int limit
    );

    @Select("""
        SELECT customer_id AS customerId,
               COUNT(*)     AS orderCount,
               SUM(total_amount) AS totalAmount
        FROM orders
        WHERE status = 'PAID'
        GROUP BY customer_id
        ORDER BY SUM(total_amount) DESC
        LIMIT 10
    """)
    List<Map<String, Object>> topCustomers();
}
