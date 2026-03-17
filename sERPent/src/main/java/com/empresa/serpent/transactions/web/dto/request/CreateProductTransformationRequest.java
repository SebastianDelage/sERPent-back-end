package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateProductTransformationRequest(

        @NotNull(message = "Created by user id cannot be null")
        Long createdByUserId,

        @NotNull(message = "Warehouse id cannot be null")
        Long warehouseId,

        String description,

        String notes,

        @NotEmpty(message = "Inputs cannot be empty")
        List<@Valid CreateProductTransformationInputRequest> inputs,

        @NotEmpty(message = "Outputs cannot be empty")
        List<@Valid CreateProductTransformationOutputRequest> outputs
) {
}