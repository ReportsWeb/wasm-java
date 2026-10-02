package jp.pao.reports.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import jp.pao.reports.web.ReportsWebEngine;
import jp.pao.reports.web.ReportsWebEngineException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;

/** The Java sample owns data access and orchestration; rendering stays in the shared WASM engine. */
@Controller
@RequestMapping("/demo/reports.web/samples/java")
public class SampleController {
    static final int MAX_BYTES = 32 * 1024 * 1024;
    static final Set<String> ASSETS = Set.of("kakuin.png", "estimate-header.jpg");
    private final SampleCatalog catalog;
    private final ObjectMapper json;
    private final Path images;
    private final String engineUrl, previewUrl, designerUrl, assetBase;
    private List<String> trustedAssetBases = List.of();
    private final Semaphore renderSlots = new Semaphore(1);
    private final ReportsWebEngine engine;

    public SampleController(SampleCatalog catalog, ObjectMapper json,
            @Value("${reports.resources}") String resources,
            @Value("${reports.engine-url}") String engineUrl,
            @Value("${reports.preview-url}") String previewUrl,
            @Value("${reports.designer-url}") String designerUrl,
            @Value("${reports.asset-base}") String assetBase) {
        this.catalog=catalog; this.json=json; this.images=Path.of(resources).resolve("images");
        this.engineUrl=engineUrl.replaceAll("/+$", ""); this.engine=new ReportsWebEngine(this.engineUrl, Duration.ofSeconds(90)); this.previewUrl=previewUrl;
        this.designerUrl=designerUrl; this.assetBase=assetBase;
    }

    /** Deployment-owned exact endpoints only; never supplied by a request or fetched over HTTP. */
    @Value("${REPORTS_TRUSTED_ASSET_BASES:}")
    void setTrustedAssetBases(String configured) {
        trustedAssetBases=Arrays.stream(configured.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
    }

    private String registeredAssetName(String reference) {
        if (reference.startsWith(assetBase)) return reference.substring(assetBase.length());
        for (String base:trustedAssetBases) if(reference.startsWith(base)) return reference.substring(base.length());
        return null;
    }

    @GetMapping({"", "/"})
    String index(@RequestParam(defaultValue="invoice") String sample, Model model) {
        model.addAttribute("sample", SampleCatalog.SAMPLES.containsKey(sample) ? sample : "invoice");
        model.addAttribute("samples", SampleCatalog.SAMPLES);
        model.addAttribute("previewUrl", previewUrl); model.addAttribute("designerUrl", designerUrl);
        return "index";
    }

    @GetMapping({"/api", "/api.php"}) @ResponseBody
    ResponseEntity<?> api(@RequestParam(defaultValue="data") String action,
            @RequestParam(defaultValue="invoice") String sample,
            @RequestParam(defaultValue="") String name) throws Exception {
        if (action.equals("asset")) return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(name.endsWith(".jpg") ? "image/jpeg" : "image/png"))
                .header("Cache-Control", "public, max-age=86400").body(asset(name));
        if (action.equals("catalog")) return ResponseEntity.ok().body(SampleCatalog.SAMPLES);
        if (!SampleCatalog.SAMPLES.containsKey(sample)) throw new IllegalArgumentException("帳票を選択してください。");
        if (action.equals("definition")) {
            ObjectNode definition=catalog.definition(sample); embedDefinitionImages(definition, sample);
            return reportJson(definition, sample+".prepdj", "definition");
        }
        if (!action.equals("data")) throw new IllegalArgumentException("未対応の操作です。");
        ObjectNode data=catalog.printData(sample).toObjectNode();
        if (data.get("Definition") instanceof ObjectNode definition) embedDefinitionImages(definition, sample);
        for (JsonNode page : data.path("Pages")) {
            if (page.get("Definition") instanceof ObjectNode d) embedDefinitionImages(d, sample);
            if (sample.equals("invoice")) for (JsonNode v : page.path("Values"))
                if (v instanceof ObjectNode o && o.path("Name").asText().equals("Image1")) o.put("Value", inlineAsset("kakuin.png"));
        }
        return reportJson(data, sample+".prepej", "printdata");
    }

    private ResponseEntity<?> reportJson(ObjectNode data, String file, String kind) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.pao.reports-"+kind+"+json"))
            .header("Content-Disposition", "inline; filename=\""+file+"\"").body(data);
    }

    private void embedDefinitionImages(ObjectNode definition, String sample) throws IOException {
        for (JsonNode item : definition.path("Objects")) {
            String name=item.path("Name").asText();
            String asset=null;
            if ((sample.equals("invoice")||sample.equals("estimate")) && name.equals("Image1")) asset="kakuin.png";
            if (sample.equals("estimate") && name.equals("Image2")) asset="estimate-header.jpg";
            if (asset!=null && item instanceof ObjectNode o) { o.put("ImagePath", inlineAsset(asset)); o.put("ImageDataBase64", ""); }
        }
    }

    private String inlineAsset(String name) throws IOException {
        return "data:image/"+(name.endsWith(".jpg")?"jpeg":"png")+";base64,"+
                Base64.getEncoder().encodeToString(asset(name));
    }

    private byte[] asset(String name) throws IOException {
        if (!ASSETS.contains(name)) throw new IllegalArgumentException("未登録の画像資源です。");
        return Files.readAllBytes(images.resolve(name));
    }

    @PostMapping(value={"/api", "/api.php"}, params="action=server-pdf") @ResponseBody
    ResponseEntity<byte[]> serverPdf(@RequestParam(defaultValue="invoice") String sample,
            HttpServletRequest request) throws Exception {
        if (!renderSlots.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "PDF作成中です。少し待ってからお試しください。");
        try {
            if (request.getContentLengthLong()>MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "印刷データは32MiB以内にしてください。");
            byte[] body=request.getInputStream().readNBytes(MAX_BYTES+1);
            if (body.length>MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "印刷データは32MiB以内にしてください。");
            JsonNode data=json.readTree(body);
            if (data==null || !data.path("Pages").isArray() || data.path("Pages").isEmpty() ||
                    (data.has("Format") && !data.path("Format").asText().equals("Reports.net PrintData")))
                throw new IllegalArgumentException("PREPEJ形式の印刷データではありません。");
            inlineAssets(data);
            byte[] pdf;
            try {
                // ac.pao:reports-web: POST /render/pdf to the Reports.Web engine.
                pdf=engine.renderPdf(json.writeValueAsBytes(data), null);
            } catch (ReportsWebEngineException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "サーバーでPDFを作成できませんでした。");
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\""+sample.replaceAll("[^A-Za-z0-9_.-]", "_")+".pdf\"")
                .header("X-Reports-Engine", "server-wasm").body(pdf);
        } finally { renderSlots.release(); }
    }

    /** Resolve our exact allowlisted endpoint. Never fetch URLs supplied inside a report. */
    void inlineAssets(JsonNode node) throws IOException {
        if (node instanceof ObjectNode object) {
            Iterator<Map.Entry<String,JsonNode>> fields=object.fields();
            while (fields.hasNext()) {
                var field=fields.next(); JsonNode value=field.getValue();
                String name=value.isTextual()?registeredAssetName(value.asText()):null;
                if (name!=null) {
                    object.put(field.getKey(), "data:image/"+(name.endsWith(".jpg")?"jpeg":"png")+";base64,"+Base64.getEncoder().encodeToString(asset(name)));
                } else if (field.getKey().equals("ImagePath") && value.isTextual() && !value.asText().isBlank() && !value.asText().startsWith("data:image/")) {
                    throw new IllegalArgumentException("外部画像は、このサンプルに登録した画像か埋め込み画像を使用してください。");
                } else inlineAssets(value);
            }
        } else if (node.isArray()) for (JsonNode child : node) inlineAssets(child);
    }
}
