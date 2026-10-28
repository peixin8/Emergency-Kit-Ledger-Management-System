package com.emergency.ledger;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/ledgers")
public class LedgerController {
    private final LedgerService service;
    private final SharedIdentity identity;
    private final LedgerExcelService excel;
    private final LedgerImportService imports;
    private final LedgerBatchExportService batches;
    public LedgerController(LedgerService service, SharedIdentity identity, LedgerExcelService excel,LedgerImportService imports,LedgerBatchExportService batches) {
        this.service=service; this.identity=identity; this.excel=excel; this.imports=imports; this.batches=batches;
    }
    @GetMapping public ApiResponse<LedgerRepository.Page> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String direction,@RequestParam(required=false) String locationStatus) {
        return ApiResponse.ok(service.list(identity.current(),page,pageSize,keyword,status,direction,locationStatus));
    }
    @GetMapping("/map-points") public ApiResponse<LedgerRepository.MapPoints> mapPoints(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String direction,@RequestParam(required=false) String locationStatus) {
        return ApiResponse.ok(service.mapPoints(identity.current(),keyword,status,direction,locationStatus));
    }
    @GetMapping("/deleted") public ApiResponse<LedgerRepository.DeletedPage> deleted(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,
        @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate deletedFrom,
        @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate deletedTo) {
        return ApiResponse.ok(service.deleted(identity.current(),page,pageSize,keyword,deletedFrom,deletedTo));
    }
    @GetMapping("/{id}") public ApiResponse<LedgerRecord> get(@PathVariable long id) { return ApiResponse.ok(service.get(identity.current(),id)); }
    @GetMapping("/{id}/history") public ApiResponse<LedgerRepository.HistoryPage> history(@PathVariable long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        return ApiResponse.ok(service.history(identity.current(),id,page,pageSize));
    }
    @GetMapping(value="/export",produces="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> export(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String direction,@RequestParam(required=false) String locationStatus) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=emergency-ledger.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(excel.export(identity.current(),keyword,status,direction,locationStatus));
    }
    @GetMapping(value="/export-batches",produces="application/zip")
    public ResponseEntity<byte[]> exportBatches(@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String direction,@RequestParam(required=false) String locationStatus) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=emergency-ledger-batches.zip").contentType(MediaType.parseMediaType("application/zip"))
            .body(batches.export(identity.current(),keyword,status,direction,locationStatus));
    }
    @PostMapping(value="/import/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LedgerImportService.Preview> preview(@RequestPart("file") MultipartFile file) { return ApiResponse.ok(imports.preview(identity.current(),file)); }
    @PostMapping(value="/import/confirm",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LedgerExcelService.ImportCounts> confirm(@RequestPart("file") MultipartFile file,@RequestParam String previewFingerprint) {
        return ApiResponse.ok(imports.confirm(identity.current(),file,previewFingerprint));
    }
    @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LedgerExcelService.ImportCounts> importFile(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(excel.importFile(identity.current(),file));
    }
    @PostMapping public ResponseEntity<ApiResponse<LedgerRecord>> create(@Valid @RequestBody LedgerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(service.create(identity.current(),request)));
    }
    @PutMapping("/{id}") public ApiResponse<LedgerRecord> update(@PathVariable long id,@Valid @RequestBody LedgerRequest request) {
        return ApiResponse.ok(service.update(identity.current(),id,request));
    }
    @DeleteMapping("/{id}") public ApiResponse<Void> delete(@PathVariable long id,@RequestParam String version) { service.delete(identity.current(),id,version); return ApiResponse.ok(null); }
}
