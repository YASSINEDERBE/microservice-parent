package com.example.notification_service.order_service.service;

import com.example.notification_service.order_service.dto.InventoryResponse;
import com.example.notification_service.order_service.dto.OrderLineItemsDto;
import com.example.notification_service.order_service.dto.OrderRequest;
import com.example.notification_service.order_service.event.OrderPlacedEvent;
import com.example.notification_service.order_service.model.Order;
import com.example.notification_service.order_service.model.OrderLineItems;
import com.example.notification_service.order_service.repository.OrderRepository;
import org.springframework.kafka.core.KafkaTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final Tracer tracer;
    private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;

    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        // Map the DTOs to the OrderLineItems and set it on the order
        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToEntity)
                .toList();

        // Now set the orderLineItemsList in the Order
        order.setOrderLineItemsList(orderLineItems);

        // Extract SKU codes after setting orderLineItemsList
        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .toList();

        Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookup");

        try (Tracer.SpanInScope spanInScope = tracer.withSpan(inventoryServiceLookup.start())) {
            // Call inventory service to check stock
            InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
                    .uri("http://inventory-service/api/inventory",
                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
                    .retrieve()
                    .bodyToMono(InventoryResponse[].class)
                    .block();

            log.info("Inventory Response Array: {}", Arrays.toString(inventoryResponseArray));

            boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                    .allMatch(InventoryResponse::isInStock);

            // Save order only if all products are in stock
            if (allProductsInStock) {
                orderRepository.save(order);
                kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
                return "Order Placed";
            } else {
                return "Product is not in stock, please try again later.";
            }
        } catch (Exception e) {
            log.error("Error while checking inventory or placing order", e);
            throw e;
        } finally {
            inventoryServiceLookup.end();
        }
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
