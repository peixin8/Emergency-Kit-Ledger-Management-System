package com.emergency.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerMultipartLimitTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;

    @Test void fileOverTwoMegabytesReturnsChineseErrorAtRealPort() throws Exception {
        var result=post(new byte[2*1024*1024+1]);
        assertThat(result.getStatusCode().value()).isEqualTo(413);
        var body=json.readTree(result.getBody());
        assertThat(body.path("code").asText()).isEqualTo("FILE_TOO_LARGE");
        assertThat(body.path("message").asText()).contains("2 MB");
        assertThat(body.path("traceId").asText()).isNotBlank();
    }

    @Test void exactTwoMegabyteFileReachesServiceDespiteMultipartEnvelope() throws Exception {
        var result=post(new byte[2*1024*1024]);
        assertThat(result.getStatusCode().value()).as(result.getBody()).isEqualTo(400);
        assertThat(json.readTree(result.getBody()).path("message").asText()).contains("文件格式无效");
    }

    private org.springframework.http.ResponseEntity<String> post(byte[] bytes) {
        var parts=new LinkedMultiValueMap<String,Object>();
        parts.add("file",new ByteArrayResource(bytes) {
            @Override public String getFilename() { return "台账.xlsx"; }
        });
        HttpHeaders headers=new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity("/api/v1/ledgers/import",new HttpEntity<>(parts,headers),String.class);
    }
}
