package com.example.art_course.child.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateChildRequest {

    @JsonAlias({"name", "display_name", "fullName", "childName"})
    @Size(max = 255)
    private String displayName;

    @JsonAlias({"birth_year", "yearOfBirth"})
    @Min(1900)
    @Max(2100)
    private Short birthYear;

    @JsonAlias({"birth_date", "dob"})
    private String birthDate;

    private Boolean shareInfo;
}