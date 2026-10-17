package com.example.art_course.offer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateOfferStatusRequest {

    @NotBlank
    @JsonAlias({"status", "offerStatus", "state"})
    private String status;
}