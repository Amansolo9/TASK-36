package com.eaglepoint.storehub.service;

import com.eaglepoint.storehub.dto.RatingRequest;
import com.eaglepoint.storehub.enums.RatingTarget;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that RatingRequest DTO validation rejects missing/invalid dimension scores
 * before they can reach the service layer.
 */
class RatingValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validRating_passes() {
        RatingRequest req = new RatingRequest();
        req.setOrderId(1L);
        req.setRatedUserId(2L);
        req.setTargetType(RatingTarget.STAFF);
        req.setStars(4);
        req.setTimelinessScore(5);
        req.setCommunicationScore(3);
        req.setAccuracyScore(4);
        req.setComment("Good");

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Valid rating should have no violations");
    }

    @Test
    void missingTimelinessScore_rejected() {
        RatingRequest req = buildBase();
        req.setTimelinessScore(null);

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("timelinessScore")));
    }

    @Test
    void missingCommunicationScore_rejected() {
        RatingRequest req = buildBase();
        req.setCommunicationScore(null);

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("communicationScore")));
    }

    @Test
    void missingAccuracyScore_rejected() {
        RatingRequest req = buildBase();
        req.setAccuracyScore(null);

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("accuracyScore")));
    }

    @Test
    void dimensionScoreZero_rejected() {
        RatingRequest req = buildBase();
        req.setTimelinessScore(0);

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void dimensionScoreSix_rejected() {
        RatingRequest req = buildBase();
        req.setAccuracyScore(6);

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void allDimensionsMissing_rejected() {
        RatingRequest req = new RatingRequest();
        req.setOrderId(1L);
        req.setRatedUserId(2L);
        req.setTargetType(RatingTarget.STAFF);
        req.setStars(4);
        // All dimensions null

        Set<ConstraintViolation<RatingRequest>> violations = validator.validate(req);
        assertEquals(3, violations.stream()
                .filter(v -> v.getPropertyPath().toString().contains("Score"))
                .count());
    }

    private RatingRequest buildBase() {
        RatingRequest req = new RatingRequest();
        req.setOrderId(1L);
        req.setRatedUserId(2L);
        req.setTargetType(RatingTarget.STAFF);
        req.setStars(4);
        req.setTimelinessScore(5);
        req.setCommunicationScore(3);
        req.setAccuracyScore(4);
        return req;
    }
}
