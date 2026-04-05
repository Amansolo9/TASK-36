package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.entity.Order;
import com.eaglepoint.storehub.entity.User;
import com.eaglepoint.storehub.repository.OrderRepository;
import com.eaglepoint.storehub.service.SiteAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingLabelService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final OrderRepository orderRepository;
    private final SiteAuthorizationService siteAuth;

    /**
     * Generates a printable PDF shipping label (4x6 inch label size).
     */
    public byte[] generateLabel(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        siteAuth.requireOwnerOrSiteAccess(order.getCustomer().getId(), order.getSite().getId());

        if (order.isPickup()) {
            throw new IllegalArgumentException("Pickup orders do not have shipping labels");
        }

        User customer = order.getCustomer();
        String address = customer.getAddress() != null ? customer.getAddress() : "N/A";

        try (PDDocument doc = new PDDocument()) {
            // 4x6 inch label (288x432 points)
            PDPage page = new PDPage(new PDRectangle(288, 432));
            doc.addPage(page);

            PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 400;
                float margin = 20;

                // Header
                cs.setFont(fontBold, 14);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("STOREHUB SHIPPING LABEL");
                cs.endText();

                // Separator line
                y -= 15;
                cs.moveTo(margin, y);
                cs.lineTo(268, y);
                cs.stroke();

                // Order info
                y -= 20;
                cs.setFont(fontRegular, 10);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Order #: " + order.getId());
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Date: " + DATE_FMT.format(order.getCreatedAt()));
                cs.endText();

                // Ship To section
                y -= 25;
                cs.setFont(fontBold, 11);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("SHIP TO:");
                cs.endText();

                y -= 18;
                cs.setFont(fontBold, 12);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText(customer.getUsername());
                cs.endText();

                y -= 16;
                cs.setFont(fontRegular, 10);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText(address);
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("ZIP: " + (order.getDeliveryZip() != null ? order.getDeliveryZip() : "N/A"));
                cs.endText();

                // Separator
                y -= 15;
                cs.moveTo(margin, y);
                cs.lineTo(268, y);
                cs.stroke();

                // Shipment details
                y -= 20;
                cs.setFont(fontRegular, 10);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Site ID: " + order.getSite().getId());
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText(String.format("Distance: %.1f mi", order.getDeliveryDistanceMiles()));
                cs.endText();

                y -= 15;
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText("Total: $" + order.getTotal().toPlainString());
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate shipping label PDF", e);
        }
    }
}
