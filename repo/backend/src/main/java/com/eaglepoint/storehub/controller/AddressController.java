package com.eaglepoint.storehub.controller;

import com.eaglepoint.storehub.dto.AddressDto;
import com.eaglepoint.storehub.security.UserPrincipal;
import com.eaglepoint.storehub.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<AddressDto> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddressDto dto) {
        return ResponseEntity.ok(addressService.create(principal.getId(), dto));
    }

    @GetMapping
    public ResponseEntity<List<AddressDto>> getMyAddresses(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(addressService.getByUser(principal.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AddressDto dto) {
        return ResponseEntity.ok(addressService.update(principal.getId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        addressService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
