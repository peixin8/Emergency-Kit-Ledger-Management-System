package com.emergency.ledger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class LedgerBatchExportService {
    private static final int MAX_BYTES=2*1024*1024;
    private final LedgerExcelService excel;
    public LedgerBatchExportService(LedgerExcelService excel) { this.excel=excel; }
    public byte[] export(CurrentUser user,String keyword,String status,String direction,String locationStatus) {
        List<LedgerRecord> records=excel.exportRecords(user,keyword,status,direction,locationStatus);
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)) {
            int start=0,part=1;
            do {
                int count=Math.min(1000,records.size()-start);
                byte[] bytes=excel.writeWorkbook(records.subList(start,start+count));
                while(bytes.length>MAX_BYTES&&count>1) {
                    count=Math.max(1,count/2); bytes=excel.writeWorkbook(records.subList(start,start+count));
                }
                if(bytes.length>MAX_BYTES) throw new IllegalStateException("单条台账超出 Excel 文件大小限制");
                zip.putNextEntry(new ZipEntry(String.format(java.util.Locale.ROOT,"应急宝台账-%03d.xlsx",part++)));
                zip.write(bytes); zip.closeEntry(); start+=count;
            } while(start<records.size());
            zip.finish(); return out.toByteArray();
        } catch(IOException e) { throw new IllegalStateException("分卷导出失败",e); }
    }
}
