package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.SupportTicket;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.Organization;
import com.eaglepoint.storehub.repository.EvidenceFileRepository;
import com.eaglepoint.storehub.repository.SupportTicketRepository;
import com.eaglepoint.storehub.repository.UserRepository;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock private EvidenceFileRepository evidenceFileRepository;
    @Mock private SupportTicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private SiteAuthorizationService siteAuth;

    @InjectMocks private EvidenceService evidenceService;

    private SupportTicket ticket;
    private User customer;

    @BeforeEach
    void setUp() {
        customer = User.builder().id(1L).username("customer").build();
        Order order = Order.builder().id(1L).customer(customer).site(Organization.builder().id(1L).build()).build();
        ticket = SupportTicket.builder().id(1L).customer(customer).order(order).build();
    }

    @Test
    void uploadEvidence_emptyFile_throws() throws IOException {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> evidenceService.uploadEvidence(1L, 1L, file));
    }

    @Test
    void uploadEvidence_tooLarge_throws() throws IOException {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(11L * 1024 * 1024); // 11MB

        assertThrows(IllegalArgumentException.class,
                () -> evidenceService.uploadEvidence(1L, 1L, file));
    }

    @Test
    void uploadEvidence_invalidType_throws() throws IOException {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("text/plain");

        assertThrows(IllegalArgumentException.class,
                () -> evidenceService.uploadEvidence(1L, 1L, file));
    }

    @Test
    void uploadEvidence_unauthorizedUser_throws() throws IOException {
        User otherUser = User.builder().id(99L).username("other").build();
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherUser));
        MultipartFile file = mock(MultipartFile.class);

        assertThrows(Exception.class,
                () -> evidenceService.uploadEvidence(1L, 99L, file));
    }
}
