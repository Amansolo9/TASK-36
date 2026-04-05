package com.eaglepoint.storehub.repository;

import com.eaglepoint.storehub.entity.DeviceBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeviceBindingRepository extends JpaRepository<DeviceBinding, Long> {
    List<DeviceBinding> findByUserIdAndActiveTrue(Long userId);
    Optional<DeviceBinding> findByUserIdAndDeviceHash(Long userId, String deviceHash);
}
