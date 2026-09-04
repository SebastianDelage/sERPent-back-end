package com.empresa.serpent.transactions.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateProductTransformationRequest(

        /**
         * Legacy field. The acting user now comes from the authenticated session; sending a
         * different id here is rejected rather than silently honoured. Newer clients omit it.
         */
        Long createdByUserId,

        /** Ignored when {@code terminalId} is set: the terminal decides the warehouse. */
        Long warehouseId,

        /** Optional registered point of sale. When present it supplies the warehouse. */
        Long terminalId,

        /**
         * Lo que escribió la persona sobre esta transformación. Va a
         * {@code transactions.description}. Ver CreatePurchaseRequest: mismo caso, misma
         * columna muerta ({@code product_transformations.notes}).
         */
        String description,

        @NotEmpty(message = "Inputs cannot be empty")
        List<@Valid CreateProductTransformationInputRequest> inputs,

        @NotEmpty(message = "Outputs cannot be empty")
        List<@Valid CreateProductTransformationOutputRequest> outputs
) {

    /** Convenience overload for callers that do not go through a terminal. */
    public CreateProductTransformationRequest(Long createdByUserId,
                                              Long warehouseId,
                                              String description,
                                              List<CreateProductTransformationInputRequest> inputs,
                                              List<CreateProductTransformationOutputRequest> outputs) {
        this(createdByUserId, warehouseId, null, description, inputs, outputs);
    }
}