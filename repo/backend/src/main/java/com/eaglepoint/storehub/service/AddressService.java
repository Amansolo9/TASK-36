package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.annotation.Audited;
import com.eaglepoint.storehub.dto.AddressDto;
import com.eaglepoint.storehub.entity.Address;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.AddressRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Audited(action = "CREATE", entityType = "Address")
    @Transactional
    public AddressDto create(Long userId, AddressDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(a -> { a.setDefault(false); addressRepository.save(a); });
        }

        Address address = Address.builder()
                .user(user)
                .label(dto.getLabel())
                .street(dto.getStreet())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .isDefault(dto.isDefault())
                .build();

        log.info("Address created for userId={}, label={}", userId, dto.getLabel());
        return toDto(addressRepository.save(address));
    }

    @Transactional(readOnly = true)
    public List<AddressDto> getByUser(Long userId) {
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto).toList();
    }

    @Audited(action = "UPDATE", entityType = "Address")
    @Transactional
    public AddressDto update(Long userId, Long addressId, AddressDto dto) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (dto.isDefault() && !address.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrue(userId)
                    .ifPresent(a -> { a.setDefault(false); addressRepository.save(a); });
        }

        address.setLabel(dto.getLabel());
        address.setStreet(dto.getStreet());
        address.setCity(dto.getCity());
        address.setState(dto.getState());
        address.setZipCode(dto.getZipCode());
        address.setDefault(dto.isDefault());

        return toDto(addressRepository.save(address));
    }

    @Audited(action = "DELETE", entityType = "Address")
    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));
        addressRepository.delete(address);
        log.info("Address deleted: addressId={}, userId={}", addressId, userId);
    }

    private AddressDto toDto(Address a) {
        AddressDto dto = new AddressDto();
        dto.setId(a.getId());
        dto.setLabel(a.getLabel());
        dto.setStreet(a.getStreet());
        dto.setCity(a.getCity());
        dto.setState(a.getState());
        dto.setZipCode(a.getZipCode());
        dto.setDefault(a.isDefault());
        return dto;
    }
}
