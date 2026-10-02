import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import jp.pao.reports.web.PrintData;
import jp.pao.reports.web.ReportsWebEngine;
import jp.pao.reports.web.ReportsWebEngineException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public final class QuickStartServer {
    private static final Path APP = Path.of("/app");
    private static final Path REPORTS_WEB = APP.resolve("reports.web").normalize();
    // ac.pao:reports-web: PrintData builds the PREPEJ, ReportsWebEngine sends it to the engine.
    private static final ReportsWebEngine ENGINE = new ReportsWebEngine(System.getenv().getOrDefault("REPORTS_ENGINE_URL", "http://engine:3107"), Duration.ofSeconds(90));
    private static final Map<String,String> TYPES = Map.of(
        "html","text/html; charset=utf-8", "js","text/javascript; charset=utf-8",
        "mjs","text/javascript; charset=utf-8", "css","text/css", "wasm","application/wasm",
        "json","application/json", "svg","image/svg+xml", "woff2","font/woff2");

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/", QuickStartServer::handle);
        server.start();
        System.out.println("http://0.0.0.0:8080/");
    }

    static String createPrintData() throws IOException {
        var definition = new ObjectMapper().readTree(Files.readString(APP.resolve("report.prepdj"), StandardCharsets.UTF_8));
        return new PrintData()
            .setDefinition(definition)
            .pageStart()
            .setValue("Message", "Javaから、あっという間に帳票出力")
            .pageEnd()
            .toJson(false);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) { send(exchange, 200, "text/html; charset=utf-8", Files.readAllBytes(APP.resolve("index.html"))); return; }
            if (path.equals("/print-data")) { send(exchange, 200, "application/json; charset=utf-8", createPrintData().getBytes(StandardCharsets.UTF_8)); return; }
            if (path.equals("/pdf") && exchange.getRequestMethod().equals("POST")) { proxyPdf(exchange); return; }
            if (path.startsWith("/reports.web/") || path.startsWith("/demo/reports.web/")) { serveReportsWeb(exchange, path); return; }
            send(exchange, 404, "text/plain; charset=utf-8", "not found".getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            send(exchange, 500, "text/plain; charset=utf-8", e.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void proxyPdf(HttpExchange exchange) throws Exception {
        byte[] body = exchange.getRequestBody().readNBytes(32 * 1024 * 1024 + 1);
        if (body.length > 32 * 1024 * 1024) { send(exchange, 413, "text/plain", new byte[0]); return; }
        try {
            send(exchange, 200, "application/pdf", ENGINE.renderPdf(body, null));
        } catch (ReportsWebEngineException e) {
            send(exchange, e.status() > 0 ? e.status() : 502, "text/plain; charset=utf-8", e.getMessage().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void serveReportsWeb(HttpExchange exchange, String requestPath) throws IOException {
        String prefix = requestPath.startsWith("/demo/reports.web/") ? "/demo/reports.web/" : "/reports.web/";
        String relative = requestPath.substring(prefix.length());
        if (requestPath.endsWith("/")) relative += "index.html";
        Path file = REPORTS_WEB.resolve(relative).normalize();
        if (!file.startsWith(REPORTS_WEB) || !Files.isRegularFile(file)) { send(exchange, 404, "text/plain", new byte[0]); return; }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        send(exchange, 200, TYPES.getOrDefault(dot < 0 ? "" : name.substring(dot + 1), "application/octet-stream"), Files.readAllBytes(file));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
