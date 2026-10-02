package jp.pao.reports.sample;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.*;
import java.sql.*;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.junit.jupiter.api.Assertions.*;

class SampleCatalogTest {
    private Path resources() {
        Path here=Path.of("").toAbsolutePath();
        while(here!=null) { Path candidate=here.resolve("samples/php/resources"); if(Files.isDirectory(candidate)) return candidate; here=here.getParent(); }
        String configured=System.getenv("REPORTS_RESOURCE_ROOT");
        if(configured!=null) return Path.of(configured);
        throw new IllegalStateException("Original PHP sample resources required for parity tests.");
    }
    private SampleCatalog catalog() { return new SampleCatalog(null,resources(),Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"),ZoneId.of("Asia/Tokyo"))); }
    @Test void estimateHeaderUsesOneCurrencyMarkerAndKeepsCoverBeforeBody() throws Exception {
        DataSource db=mock(DataSource.class); Connection connection=mock(Connection.class);
        PreparedStatement statement=mock(PreparedStatement.class); ResultSet headers=mock(ResultSet.class), details=mock(ResultSet.class);
        when(db.getConnection()).thenReturn(connection); when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(headers,details); when(headers.next()).thenReturn(true,false); when(details.next()).thenReturn(false);
        when(headers.getString(1)).thenReturn("""
            {"見積番号":"1","お客様名":"パオ","担当者名":"担当者","見積日":"2026-09-06","合計金額":123456,"消費税額":1234}
            """);
        var pages=new SampleCatalog(db,resources()).printData("estimate").toObjectNode().path("Pages");
        assertEquals(2,pages.size()); assertEquals(2,pages.get(0).path("Values").size());
        var matches=new java.util.ArrayList<String>();
        for(var value:pages.get(1).path("Values")) if(value.path("Name").asText().equals("ヘッダ合計")) matches.add(value.path("Value").asText());
        assertEquals(java.util.List.of("\\ 123,456"),matches);
    }
    @Test void originalDefinitionsAreUnmodifiedForEverySample() throws Exception {
        assertEquals(8,SampleCatalog.SAMPLES.size()); var c=catalog();
        for(String key:SampleCatalog.SAMPLES.keySet()) { var d=c.definition(key); assertEquals("mm",d.path("CoordinateUnit").asText()); assertTrue(d.path("Objects").size()>0,key); }
        assertThrows(IllegalArgumentException.class,()->c.definition("../invoice"));
    }
    @Test void allFourNonDatabaseReportsAndPageSpecificOperations() throws Exception {
        var c=catalog(); for(String key:new String[]{"quick-start","business-card","design-showcase"}) assertEquals(1,c.printData(key).toObjectNode().path("Pages").size());
        var pages=c.printData("multiples-of-ten").toObjectNode().path("Pages"); assertEquals(4,pages.size());
        int lines=0,hidden=0;
        for(int page=0;page<4;page++) for(var value:pages.get(page).path("Values")) {
            if(value.path("Name").asText().equals("行番号")) lines++;
            if(value.path("Name").asText().equals("Line3")) { hidden++; assertEquals(1,page); assertFalse(value.path("Drawing").asBoolean()); }
            if(value.path("Name").asText().equals("日付")) assertEquals("2026/09/06 09:00:00",value.path("Value").asText());
        }
        assertEquals(60,lines); assertEquals(1,hidden);
        assertEquals("12",pages.get(0).path("DynamicAttributes").get(0).path("Values").path("fontSize").asText());
        assertThrows(IllegalStateException.class,()->c.printData("invoice"));
    }
}
