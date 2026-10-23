package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.reader.model.AccessRequestCreate;
import com.fishmatrix.reader.model.AccessRequestView;
import com.fishmatrix.reader.service.AccessRequestService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reader")
public class AccessRequestController {

    private final AccessRequestService service;

    public AccessRequestController(AccessRequestService service) {
        this.service = service;
    }

    @PostMapping("/books/{bookId}/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AccessRequestView request(@PathVariable long bookId,
            @RequestBody AccessRequestCreate create) {
        return service.request(bookId, create, UserContext.require());
    }

    @GetMapping("/access-requests/mine")
    public Map<String, Object> mine() {
        List<AccessRequestView> requests = service.mine(UserContext.require());
        return Map.of("requests", requests);
    }

    @GetMapping("/access-requests/received")
    public Map<String, Object> received(
            @RequestParam(required = false) String status) {
        List<AccessRequestView> requests = service.received(UserContext.require(), status);
        return Map.of("requests", requests);
    }

    @PostMapping("/access-requests/{requestId}/approve")
    public AccessRequestView approve(@PathVariable long requestId) {
        return service.approve(requestId, UserContext.require());
    }

    @PostMapping("/access-requests/{requestId}/reject")
    public AccessRequestView reject(@PathVariable long requestId,
            @RequestBody(required = false) AccessDecision decision) {
        return service.reject(requestId, decision == null ? null : decision.message(),
                UserContext.require());
    }

    @PostMapping("/access-requests/{requestId}/revoke")
    public AccessRequestView revoke(@PathVariable long requestId) {
        return service.revoke(requestId, UserContext.require());
    }

    @PostMapping("/access-requests/{requestId}/cancel")
    public AccessRequestView cancel(@PathVariable long requestId) {
        return service.cancel(requestId, UserContext.require());
    }

    public record AccessDecision(String message) {
    }
}
