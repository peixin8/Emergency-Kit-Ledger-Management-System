package com.emergency.ledger;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LedgerRecord(
    String id, String organizationId, String ledgerCode, String originalAddress, String destinationAddress,
    String applicationMethod, String applicantContact, String applicantPhone,
    String deviceCondition, String direction, LocalDate applicationDate,
    String accessRoom, String fiberCore, String networkStandard, String bbuModel, String rruModel,
    String bbuRruPowerWatts, String managementIp, Integer managementVlan,
    String businessIp, Integer businessVlan, String deviceSerialNo, String assetOwner,
    String remarks, OffsetDateTime createdAt, OffsetDateTime updatedAt, String version
) {}
