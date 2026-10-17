package com.example.art_course.offer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CreateOfferRequest {

    @NotNull
    @JsonAlias({"requestId", "request_id", "customizeRequestId", "customize_request_id"})
    private UUID requestId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @JsonAlias({"price", "amount", "totalAmount", "total_amount"})
    private BigDecimal price;

    @JsonAlias({"message", "note", "description"})
    private String message;
}
