package com.emergency.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 通过真实数据库在 COUNT 返回后、SELECT 之前提交新增，稳定重现导出竞争。 */
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:export_race;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
class LedgerExportRaceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired Validator validator;
    @Autowired SharedIdentity identity;

    @ParameterizedTest(name="concurrent insert cannot exceed export limit: batches={0}")
    @ValueSource(booleans={false,true})
    void rejectsActualResultAboveLimitWhenCountWasWithinLimit(boolean batches) {
        String prefix="EXPORT-RACE-"+System.nanoTime()+"-";
        CurrentUser user=identity.current();
        String insert="INSERT INTO emergency_device_ledger(organization_id,ledger_code,original_address,destination_address,application_method,applicant_contact,device_condition,direction,application_date) VALUES (?,?,'原址','目标','ONLINE','联系人','GOOD','IN','2026-10-28')";
        jdbc.batchUpdate(insert,java.util.stream.IntStream.range(0,10000)
            .mapToObj(i->new Object[]{user.organizationId(),prefix+i}).toList());
        AtomicBoolean inserted=new AtomicBoolean(); AtomicLong countBeforeInsert=new AtomicLong(-1);
        // 只增加确定性的测试调度点；COUNT、插入和SELECT均执行真实SQL，未伪造Page或记录。
        JdbcTemplate interleaved=new JdbcTemplate(jdbc.getDataSource()) {
            @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
                T result=super.queryForObject(sql,type,args);
                if(sql.startsWith("SELECT COUNT(*) FROM emergency_device_ledger")&&inserted.compareAndSet(false,true)) {
                    countBeforeInsert.set(((Number)result).longValue());
                    jdbc.update(insert,user.organizationId(),prefix+"concurrent");
                }
                return result;
            }
        };
        var repository=new LedgerRepository(interleaved,json);
        var service=new LedgerService(repository,json);
        var excel=new LedgerExcelService(service,repository,validator);
        ApiException error=assertThrows(ApiException.class,()->{
            if(batches) new LedgerBatchExportService(excel).export(user,prefix,null,null,null);
            else excel.export(user,prefix,null,null,null);
        });
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(countBeforeInsert.get()).isEqualTo(10000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM emergency_device_ledger WHERE ledger_code LIKE ?",Long.class,prefix+"%")).isEqualTo(10001);
    }
}
