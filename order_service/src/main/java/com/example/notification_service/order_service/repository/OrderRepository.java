package com.example.notification_service.order_service.repository;

import com.example.notification_service.order_service.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
