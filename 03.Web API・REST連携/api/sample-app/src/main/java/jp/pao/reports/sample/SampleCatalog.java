package jp.pao.reports.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.pao.reports.web.PrintData;
import javax.sql.DataSource;
import java.io.IOException;
import java.math.*;
import java.nio.file.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Same original definitions, database rows and page operations as the PHP example.
 * All mutable report state is request-local; the catalog is safe as a Spring singleton.
 */
public final class SampleCatalog {
    public static final Map<String, String> SAMPLES;
    private static final Map<String, String> FILES;
    private static final ObjectMapper JSON = new ObjectMapper();
    static {
        LinkedHashMap<String,String> names = new LinkedHashMap<>();
        names.put("quick-start", "あっという間に帳票出力"); names.put("multiples-of-ten", "10のサンプル");
        names.put("postal", "郵便番号一覧（定義切替）"); names.put("estimate", "見積書（表紙＋明細）");
        names.put("invoice", "請求書"); names.put("products", "商品大小分類（途中で小計）");
        names.put("business-card", "名刺"); names.put("design-showcase", "デザイン機能見本");
        SAMPLES = Collections.unmodifiableMap(names);
        Map<String,String> files = new HashMap<>(); names.keySet().forEach(n -> files.put(n,n+".prepdj")); files.put("postal", "postal-1.prepdj");
        FILES = Map.copyOf(files);
    }
    private final DataSource database;
    private final Path resourceRoot;
    private final Clock clock;
    public SampleCatalog(DataSource database, Path resourceRoot) { this(database, resourceRoot, Clock.system(ZoneId.of("Asia/Tokyo"))); }
    public SampleCatalog(DataSource database, Path resourceRoot, Clock clock) { this.database=database; this.resourceRoot=resourceRoot; this.clock=clock; }
    public ObjectNode definition(String sample) throws IOException { assertSample(sample); return loadDefinition(FILES.get(sample)); }
    public PrintData printData(String sample) throws IOException, SQLException {
        assertSample(sample);
        return switch(sample) {
            case "multiples-of-ten" -> multiples(); case "postal" -> postal(); case "estimate" -> estimate();
            case "invoice" -> invoice(); case "products" -> products(); default -> simple(sample);
        };
    }
    private ObjectNode loadDefinition(String name) throws IOException {
        JsonNode value = JSON.readTree(resourceRoot.resolve("definitions").resolve(name).toFile());
        if (!value.isObject() || !value.path("CoordinateUnit").asText("mm").equals("mm")) throw new IOException("Original definition must use mm: " + name);
        return (ObjectNode)value;
    }
    private PrintData simple(String sample) throws IOException {
        PrintData p=new PrintData().setDefinition(definition(sample)).pageStart();
        if(sample.equals("quick-start")) p.setValue("Text2", "Webブラウザで作った\n印刷データです。");
        // 名刺は 5 段。繰返しの物体は、何も書かないと 1 段目にしか描かない（原本と同じ、D-37）。各段に定義の文字をそのまま書く。
        if(sample.equals("business-card")) for(JsonNode o:definition(sample).path("Objects")) for(int i=0;i<repeat(o);i++) p.setValue(o.path("Name").asText(), o.path("Text").asText(""), i);
        return p.pageEnd();
    }
    private PrintData multiples() throws IOException {
        PrintData p=new PrintData().setDefinition(definition("multiples-of-ten"));
        for(int page=1;page<=4;page++) {
            p.pageStart().setValue("日付", now("yyyy/MM/dd HH:mm:ss")).setValue("頁数", "Page - "+page)
                .setValue("フォントサイズ", "フォントサイズ\n 変更後").changeAttributes("フォントサイズ", Map.of("fontSize",12));
            // 2ページ目だけ、ページ上部の線「Line3」を非表示にする。空文字と drawing=false を指定する。
            if(page==2) p.setValue("Line3", "", 0, false);
            for(int line=0;line<15;line++) {
                int i=(page-1)*15+line+1;
                p.setValue("行番号",i,line).setValue("10倍数",i*10,line).drawObject("横線",line,null); // 罫線・枠は値を書かずに描く（"" を書くと描かない。原本と同じ、D-33）
                // 100で割り切れる値だけ、この行の文字色を青にする。
                if ((i * 10) % 100 == 0) p.changeAttributes("10倍数",Map.of("foreground","#FF0000FF"),line);
            }
            p.pageEnd();
        } return p;
    }
    private PrintData postal() throws IOException, SQLException {
        List<ObjectNode> rows=rows("postal",1); ObjectNode first=loadDefinition("postal-1.prepdj"), second=loadDefinition("postal-2.prepdj");
        PrintData p=new PrintData().setDefinition(first);
        for(int offset=0,page=0;offset<rows.size();offset+=32,page++) {
            p.pageStart(page<5?first:second).setValue("ページ", "Page-"+(page+1)).setValue("日時",now("yyyy/MM/dd HH:mm:ss"));
            List<ObjectNode> chunk=rows.subList(offset,Math.min(offset+32,rows.size()));
            for(int i=0;i<chunk.size();i++) {
                List<String> v=values(chunk.get(i));
                p.setValue("郵便番号",at(v,0),i).setValue("市区町村",at(v,1),i).setValue("住所",at(v,2),i).drawObject("横罫線",i,null);
                if(page>=5 && i%2==1) p.drawObject("網掛け",i,null);
            }
            if(page<5) { List<String> v=values(chunk.get(0)); p.setValue("QR",(at(v,0)+" "+at(v,1)+at(v,2)).trim()); }
            p.pageEnd();
        } return p;
    }
    private PrintData estimate() throws IOException, SQLException {
        List<ObjectNode> headers=rows("estimate",1), details=rows("estimate",2);
        ObjectNode cover=loadDefinition("estimate-cover.prepdj"), body=loadDefinition("estimate.prepdj"); PrintData p=new PrintData().setDefinition(cover);
        for(ObjectNode h:headers) {
            p.pageStart(cover).setValue("お客様名",s(h,"お客様名")).setValue("担当者名",s(h,"担当者名")).pageEnd()
             .pageStart(body).setValue("見積番号",s(h,"見積番号")).setValue("お客様名",s(h,"お客様名")).setValue("担当者名",s(h,"担当者名"))
             .setValue("見積日",japaneseDate(s(h,"見積日"))).setValue("ヘッダ合計","\\ "+number(h.get("合計金額")))
             .setValue("消費税額",number(h.get("消費税額"))).setValue("フッタ合計",number(h.get("合計金額")));
            for(int i=0;i<=6;i++) for(String n:List.of("品番白","品名白","数量白","単価白","金額白","品番青","品名青","数量青","単価青","金額青",
                // 品名青・単価青と同じ位置の帯。何も書かないと 1 行目にしか描かない（D-37）。
                "Square5","Square10")) p.drawObject(n,i,null);
            int i=0; for(ObjectNode r:matching(details,"見積番号",s(h,"見積番号"))) {
                p.setValue("品番",s(r,"品番"),i).setValue("品名",s(r,"品名"),i).setValue("数量",s(r,"数量"),i)
                 .setValue("単価",number(r.get("単価")),i).setValue("金額",number(r.get("金額")),i); i++;
            } p.pageEnd();
        } return p;
    }
    private PrintData invoice() throws IOException, SQLException {
        List<ObjectNode> headers=rows("invoice",1), details=rows("invoice",2); ObjectNode d=definition("invoice");
        PrintData p=new PrintData().setDefinition(d); double px=96/25.4;
        for(ObjectNode h:headers) {
            int maxH=Math.max(4,repeat(object(d,"hLine"))-1), maxV=Math.max(1,repeat(object(d,"vLine"))-1);
            p.pageStart().setValue("txtNo",s(h,"請求番号")).setValue("txtCustomer",s(h,"お客様名"))
             .setValue("txtDate",now("yyyy年M月d日")).setValue("Image1",imageData("kakuin.png"));
            double[] adjust={-5,44,-20,-10,-9}, columnX=new double[maxV]; double nextX=0;
            for(int j=0;j<maxV;j++) { JsonNode base=object(d,"field"+(j+1)); double x=j==0?base.path("X").asDouble()*px:nextX; columnX[j]=x; nextX=x+(base.path("Width").asDouble()+adjust[j])*px; }
            for(int i=0;i<maxH;i++) {
                p.drawObject("hLine",i,null).drawObject("LineRect",i,null);
                if(i==0) p.changeAttributes("hLine",Map.of("borderWidth",0.5*px),i);
                if(i==1) p.changeAttributes("hLine",Map.of("strokeStyle","Double"),i);
                String color=i==0?"#FFFFDAB9":i<maxH-3?(i%2==1?"#FFFFFFFF":"#FF87CEFA"):"#FFFFFFB4";
                p.changeAttributes("LineRect",Map.of("background",color,"fillEnabled",true,"fillStyle","Solid","borderColor","#FFFFFFFF"),i);
                for(int j=0;j<maxV;j++) {
                    // Attribute selection alone does not draw unused native text instances.
                    if(j<3 && i>matching(details,"請求番号",s(h,"請求番号")).size()) continue;
                    JsonNode base=object(d,"field"+(j+1)); Map<String,Object> attrs=new LinkedHashMap<>();
                    attrs.put("x",columnX[j]); attrs.put("width",(base.path("Width").asDouble()+adjust[j])*px); attrs.put("bold",i==0);
                    attrs.put("fontSize",i==0?base.path("FontSizePt").numberValue():12);
                    attrs.put("horizontalAlignment",i==0?"Center":j==1?"Left":j==0?"Center":"Right");
                    p.setValue("field"+(j+1),"",i).changeAttributes("field"+(j+1),attrs,i);
                }
            }
            for(int j=0;j<=maxV;j++) {
                p.drawObject("vLine",j,null).changeAttributes("vLine",Map.of("x",j<maxV?columnX[j]:nextX),j);
                if(j==0||j==maxV) p.changeAttributes("vLine",Map.of("borderWidth",0.5*px),j);
            }
            String[] labels={"品番","品名","数量","単価","金額"}; for(int j=0;j<labels.length;j++) p.setValue("field"+(j+1),labels[j],0);
            long total=0; int row=1;
            for(ObjectNode r:matching(details,"請求番号",s(h,"請求番号"))) {
                long quantity=invoiceInteger(r.get("数量")), price=invoiceInteger(r.get("単価"));
                long amount=Math.multiplyExact(quantity,price); total=Math.addExact(total,amount);
                p.setValue("field1",s(r,"品番"),row).setValue("field2",s(r,"品名"),row).setValue("field3",s(r,"数量"),row)
                 .setValue("field4",invoiceNumber(r.get("単価")),row).setValue("field5",invoiceNumber(amount),row); row++;
            }
            double tax=total*0.05; String[] totals={"小計","消費税","合計"}; double[] amounts={total,tax,total+tax};
            for(int k=0;k<3;k++) { int r=maxH-3+k; p.setValue("field4",totals[k],r).setValue("field5",invoiceNumber(amounts[k]),r).changeAttributes("field4",Map.of("fontSize",16,"bold",true,"horizontalAlignment","Center"),r); }
            p.setValue("txtTotal",invoiceNumber(total+tax)).changeAttributes("hLine",Map.of("strokeStyle","Double"),maxH-3)
             .drawObject("hLine",maxH,null).changeAttributes("hLine",Map.of("borderWidth",0.5*px),maxH).pageEnd();
        } return p;
    }
    private PrintData products() throws IOException, SQLException {
        Map<String,String> big=new HashMap<>(), small=new HashMap<>();
        for(ObjectNode r:rows("products",1)) big.put(s(r,"大分類コード"),s(r,"大分類名称"));
        for(ObjectNode r:rows("products",2)) small.put(s(r,"大分類コード")+":"+s(r,"小分類コード"),s(r,"小分類名称"));
        List<Map<String,String>> stream=new ArrayList<>(); String prevBig=null,prevSmall=null,prevBigName=null,prevSmallName=null; int bigCount=0,smallCount=0;
        for(ObjectNode r:rows("products",3)) {
            String bigKey=s(r,"大分類コード"), smallKey=bigKey+":"+s(r,"小分類コード");
            String bn=big.getOrDefault(bigKey,""), sn=small.getOrDefault(smallKey,"");
            if(prevSmall!=null&&!prevSmall.equals(smallKey)) { stream.add(subtotal("small",prevSmallName,smallCount)); smallCount=0; }
            if(prevBig!=null&&!prevBig.equals(bigKey)) { stream.add(subtotal("big",prevBigName,bigCount)); bigCount=0; }
            stream.add(Map.of("大分類",Objects.equals(prevBig,bigKey)?"":bn,"小分類",Objects.equals(prevSmall,smallKey)?"":sn,"品番",s(r,"品番"),"品名",s(r,"品名"),"kind","detail"));
            prevBig=bigKey; prevSmall=smallKey; prevBigName=bn; prevSmallName=sn; bigCount++; smallCount++;
        }
        if(prevSmall!=null) stream.add(subtotal("small",prevSmallName,smallCount)); if(prevBig!=null) stream.add(subtotal("big",prevBigName,bigCount));
        PrintData p=new PrintData().setDefinition(definition("products"));
        for(int offset=0;offset<stream.size();offset+=20) {
            p.pageStart(); List<Map<String,String>> chunk=stream.subList(offset,Math.min(offset+20,stream.size()));
            for(int i=0;i<chunk.size();i++) {
                Map<String,String> r=chunk.get(i);
                for(String n:List.of("大分類","小分類","品番","品名")) p.setValue(n,r.get(n),i);
                for(String n:List.of("枠_大分類","枠_小分類","枠_品番","枠_品名")) {
                    p.drawObject(n,i,null); if(!r.get("kind").equals("detail")) p.changeAttributes(n,Map.of("background",r.get("kind").equals("small")?"#FFFFFFE0":"#FFFFB6C1","fillEnabled",true,"fillStyle","Solid"),i);
                }
            } p.pageEnd();
        } return p;
    }
    private static Map<String,String> subtotal(String kind,String name,int count) { return Map.of("大分類","","小分類",(kind.equals("small")?"小分類":"大分類")+"("+name+")小計","品番",count+" 冊","品名","","kind",kind); }
    private List<ObjectNode> rows(String sample,int sheet) throws SQLException, IOException {
        if(database==null) throw new IllegalStateException("PostgreSQL is required for this sample.");
        List<ObjectNode> result=new ArrayList<>();
        try(Connection c=database.getConnection(); PreparedStatement q=c.prepareStatement("SELECT row_data FROM reports_framework_rows WHERE sample_key=? AND sheet_no=? ORDER BY row_no")) {
            q.setString(1,sample); q.setInt(2,sheet); try(ResultSet rs=q.executeQuery()) { while(rs.next()) result.add((ObjectNode)JSON.readTree(rs.getString(1))); }
        }
        if(sample.equals("products")&&sheet==3) result.sort((a,b)-> { int first=new BigDecimal(s(a,"大分類コード")).compareTo(new BigDecimal(s(b,"大分類コード"))); return first!=0?first:new BigDecimal(s(a,"小分類コード")).compareTo(new BigDecimal(s(b,"小分類コード"))); });
        return result;
    }
    private static List<ObjectNode> matching(List<ObjectNode> rows,String key,String value) { return rows.stream().filter(r->s(r,key).equals(value)).toList(); }
    private static String s(JsonNode row,String key) { return row.path(key).asText(""); }
    private static List<String> values(ObjectNode row) { List<String> v=new ArrayList<>(); row.elements().forEachRemaining(n->v.add(n.asText(""))); return v; }
    private static String at(List<String> values,int i) { return i<values.size()?values.get(i):""; }
    private static JsonNode object(JsonNode d,String name) { for(JsonNode o:d.path("Objects")) if(s(o,"Name").equals(name)) return o; throw new IllegalStateException("Missing report object: "+name); }
    private static int repeat(JsonNode o) { return o.has("Repeat")?o.path("Repeat").asInt():o.path("RepeatCount").asInt(); }
    private static String number(Object value) {
        String text=value instanceof JsonNode n?n.asText():value.toString(); DecimalFormat f=new DecimalFormat("#,##0",DecimalFormatSymbols.getInstance(Locale.US)); f.setRoundingMode(RoundingMode.HALF_UP); return f.format(new BigDecimal(text));
    }
    static long invoiceInteger(Object value) {
        String text=value instanceof JsonNode n?n.asText():String.valueOf(value); final double number;
        try { number=Double.parseDouble(text.trim()); } catch(RuntimeException ex) { throw new IllegalArgumentException("Invoice numeric value required: "+text,ex); }
        if(!Double.isFinite(number)) throw new IllegalArgumentException("Invoice numeric value must be finite: "+text);
        double rounded=Math.rint(number);
        if(rounded < -0x1.0p63 || rounded >= 0x1.0p63) throw new IllegalArgumentException("Invoice numeric value is outside Int64 range: "+text);
        return (long)rounded;
    }
    private static String invoiceNumber(Object value) {
        String text=value instanceof JsonNode n?n.asText():value.toString(); DecimalFormat f=new DecimalFormat("#,##0",DecimalFormatSymbols.getInstance(Locale.US)); f.setRoundingMode(RoundingMode.HALF_EVEN); return f.format(new BigDecimal(text));
    }
    private String now(String pattern) { return LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern(pattern,Locale.JAPAN)); }
    private static String japaneseDate(String value) { return LocalDate.parse(value.substring(0,10)).format(DateTimeFormatter.ofPattern("yyyy年M月d日",Locale.JAPAN)); }
    private String imageData(String name) throws IOException { return "data:image/png;base64,"+Base64.getEncoder().encodeToString(Files.readAllBytes(resourceRoot.resolve("images").resolve(name))); }
    private static void assertSample(String sample) { if(sample==null||!SAMPLES.containsKey(sample)) throw new IllegalArgumentException("Unknown sample: "+sample); }
}
