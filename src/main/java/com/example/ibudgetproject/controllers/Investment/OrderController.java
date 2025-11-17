package com.example.ibudgetproject.controllers.Investment;

import com.example.ibudgetproject.entities.Investment.Coin;
import com.example.ibudgetproject.entities.Investment.Order;
import com.example.ibudgetproject.entities.Investment.domain.OrderType;
import com.example.ibudgetproject.entities.User.User;
import com.example.ibudgetproject.request.CreateOrderRequest;
import com.example.ibudgetproject.services.Investment.CoinService;
import com.example.ibudgetproject.services.Investment.OrderService;
import com.example.ibudgetproject.services.User.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private CoinService coinService;

    @PostMapping("/pay")
    public ResponseEntity<?> payOrderPayment(
            @RequestHeader("Authorization") String jwt,
            @RequestBody CreateOrderRequest req
    ) {
        try {
            // Validate request
            if (req.getCoinId() == null || req.getCoinId().isEmpty()) {
                return buildErrorResponse("Coin ID is required", HttpStatus.BAD_REQUEST);
            }

            if (req.getQuantity() <= 0) {
                return buildErrorResponse("Quantity must be greater than 0", HttpStatus.BAD_REQUEST);
            }

            // Get user
            User user = userService.findUserProfileByJwt(jwt);

            // Find or fetch coin (will auto-save if not found)
            Coin coin = coinService.findById(req.getCoinId());

            // Process order
            Order order = orderService.processOrder(coin, req.getQuantity(), req.getOrderType(), user);

            return ResponseEntity.ok(order);

        } catch (Exception e) {
            System.err.println("Order processing error: " + e.getMessage());
            return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(
            @RequestHeader("Authorization") String jwToken,
            @PathVariable Long orderId
    ) {
        try {
            User user = userService.findUserProfileByJwt(jwToken);
            Order order = orderService.getOrderById(orderId);

            if (order.getUser().getUserId().equals(user.getUserId())) {
                return ResponseEntity.ok(order);
            } else {
                return buildErrorResponse("You don't have access to this order", HttpStatus.FORBIDDEN);
            }
        } catch (Exception e) {
            return buildErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping()
    public ResponseEntity<?> getAllOrdersForUser(
            @RequestHeader("Authorization") String jwt,
            @RequestParam(required = false) OrderType order_type,
            @RequestParam(required = false) String asset_symbol
    ) {
        try {
            Long userId = userService.findUserProfileByJwt(jwt).getUserId();
            List<Order> userOrders = orderService.getAllOrdersOfUser(userId, order_type, asset_symbol);
            return ResponseEntity.ok(userOrders);
        } catch (Exception e) {
            return buildErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper method to build error responses
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", message);

        return new ResponseEntity<>(errorResponse, status);
    }
}
