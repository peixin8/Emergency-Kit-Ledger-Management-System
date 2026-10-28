package com.emergency.ledger;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record LedgerRecord(
    String id, String organizationId, String ledgerCode, String originalAddress, String destinationAddress,
    String applicationMethod, String applicantContact, String applicantPhone,
    String deviceCondition, String deviceConditionDetail, String direction, LocalDate applicationDate,
    String ptnPort, String accessRoom, String fiberCore, String networkStandard, String bbuModel, String rruModel,
    String bbuRruPowerWatts, String managementIp, Integer managementVlan,
    String businessIp, Integer businessVlan, String deviceSerialNo, String assetOwner,
    String remarks, String latitude, String longitude,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, String version
) {}
