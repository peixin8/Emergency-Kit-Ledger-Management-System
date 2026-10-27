package com.emergency.ledger;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record LedgerRequest(
    @NotBlank @Size(max=64) String ledgerCode,
    @NotBlank @Size(max=255) String originalAddress,
    @NotBlank @Size(max=255) String destinationAddress,
    @NotBlank @Pattern(regexp="ONLINE|PAPER|PHONE|OTHER") String applicationMethod,
    @NotBlank @Size(max=100) String applicantContact,
    @Size(max=40) String applicantPhone,
    @NotBlank @Pattern(regexp="GOOD|REPAIR|FAULT|RETIRED") String deviceCondition,
    @NotBlank @Pattern(regexp="IN|OUT|TRANSFER") String direction,
    @NotNull LocalDate applicationDate,
    @Size(max=120) String accessRoom,
    @Size(max=100) String fiberCore,
    @Size(max=100) String networkStandard,
    @Size(max=120) String bbuModel,
    @Size(max=120) String rruModel,
    String bbuRruPowerWatts,
    @Size(max=45) String managementIp,
    @Min(1) @Max(4094) Integer managementVlan,
    @Size(max=45) String businessIp,
    @Min(1) @Max(4094) Integer businessVlan,
    @Size(max=120) String deviceSerialNo,
    @Size(max=120) String assetOwner,
    @Size(max=2000) String remarks,
    String latitude,
    String longitude,
    String version
) {}
