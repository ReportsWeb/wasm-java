import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import jp.pao.reports.web.PrintData;
import jp.pao.reports.web.ReportsWebEngine;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class QuickStartServer {
    // Docker の中では /app。外（このフォルダーで `java QuickStartServer.java`）では今のフォルダー。
    private static final boolean IN_DOCKER = Files.isRegularFile(Path.of("/app/reports.web/design/index.html"));
    private static final Path APP = IN_DOCKER ? Path.of("/app") : Path.of("").toAbsolutePath();
    private static final Path REPORTS_WEB = LocalRun.findReportsWeb(APP);
    private static String ENGINE = "http://engine:3107";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String,String> TYPES = Map.of(
        "html","text/html; charset=utf-8", "js","text/javascript; charset=utf-8",
        "mjs","text/javascript; charset=utf-8", "css","text/css", "wasm","application/wasm",
        "json","application/json", "svg","image/svg+xml", "woff2","font/woff2");

    public static void main(String[] args) throws Exception {
        if (!Files.isRegularFile(APP.resolve("report.prepdj"))) {
            System.out.println("report.prepdj が見つかりません。このフォルダー（01.あっという間に帳票出力）で起動してください。");
            return;
        }
        if (REPORTS_WEB == null) {
            System.out.println("Runtime/reports.web が見つかりません。ZIP を展開したフォルダーの並びのまま動かしてください。");
            return;
        }
        ENGINE = LocalRun.prepareEngine(IN_DOCKER, REPORTS_WEB);
        // Docker の中では 8080 番。外では 127.0.0.1 の 19210 番（compose と同じ番号）。
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", IN_DOCKER ? "8080" : "19210"));
        HttpServer server = HttpServer.create(new InetSocketAddress(IN_DOCKER ? "0.0.0.0" : "127.0.0.1", port), 0);
        server.createContext("/", QuickStartServer::handle);
        server.start();
        System.out.println(IN_DOCKER ? "http://0.0.0.0:8080/" : "見本の帳票：http://127.0.0.1:" + port + "/（止めるときは Ctrl+C）");
        if (!IN_DOCKER) LocalRun.openBrowser("http://127.0.0.1:" + port + "/");
    }

    static String createPrintData() throws IOException {
        var json = new ObjectMapper();
        var definition = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(Files.readString(APP.resolve("report.prepdj")));
        return new PrintData().setDefinition(definition).pageStart().setValue("Message", "Javaから、あっという間に帳票出力").pageEnd().toJson();
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
            // 例外の文字列や内部パスは応答へ出さず、サーバーのログにだけ残す。
            System.err.println("Request failed: " + e.getClass().getSimpleName());
            sendError(exchange, 500, "処理に失敗しました。サーバーのログを確認してください。");
        }
    }

    private static void proxyPdf(HttpExchange exchange) throws Exception {
        byte[] body = exchange.getRequestBody().readNBytes(64 * 1024 * 1024 + 1);
        if (body.length > 64 * 1024 * 1024) { send(exchange, 413, "text/plain", new byte[0]); return; }
        byte[] pdf = new ReportsWebEngine(ENGINE, Duration.ofSeconds(90)).renderPdf(body, null);
        send(exchange, 200, "application/pdf", pdf);
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

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        String json = "{\"error\":\"" + message + "\"}";
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}

// Docker を使わずに動かすための準備（Docker の中では何もしない）。
// - 製品（Runtime/reports.web）は、ZIP の並びのまま ../../../Runtime/reports.web にある。
// - PDF を作るエンジンは、REPORTS_ENGINE_URL が無ければ 127.0.0.1:3107。居なければ、PC の Node.js で子として起動する。
final class LocalRun {
    private LocalRun() {}

    static Path findReportsWeb(Path app) {
        String configured = System.getenv("REPORTS_WEB_ROOT");
        if (configured != null && !configured.isEmpty()) return Path.of(configured).toAbsolutePath().normalize();
        for (Path dir = app; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("Runtime").resolve("reports.web");
            if (Files.isRegularFile(candidate.resolve("design").resolve("index.html"))) return candidate.normalize();
        }
        Path docker = Path.of("/app/reports.web");
        return Files.isRegularFile(docker.resolve("design").resolve("index.html")) ? docker : null;
    }

    private static boolean alive(String url) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1500)).build();
            return client.send(HttpRequest.newBuilder(URI.create(url + "/health")).timeout(Duration.ofMillis(1500)).build(),
                HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Path findNode() {
        String name = System.getProperty("os.name").toLowerCase().startsWith("windows") ? "node.exe" : "node";
        String path = System.getenv("PATH"); // Windows では名前の大小を区別しない getenv(String) で読む（Map は "Path" のことがある）
        for (String dir : (path == null ? "" : path).split(java.io.File.pathSeparator)) {
            try {
                Path candidate = Path.of(dir.replace("\"", "")).resolve(name);
                if (Files.isRegularFile(candidate)) return candidate;
            } catch (java.nio.file.InvalidPathException ignored) { }
        }
        return null;
    }

    static String prepareEngine(boolean inDocker, Path reportsWeb) throws InterruptedException {
        String configured = System.getenv("REPORTS_ENGINE_URL");
        if (configured != null && !configured.isEmpty()) return configured.replaceAll("/+$", "");
        if (inDocker) return "http://engine:3107";
        String url = "http://127.0.0.1:3107";
        if (alive(url)) return url;
        Path node = findNode();
        if (node == null) {
            System.out.println("PDF の保存には Node.js か Docker が要ります（https://nodejs.org/）。プレビューはこのまま使えます。");
            return url;
        }
        Path server = reportsWeb.resolve("server").resolve("server.mjs");
        ProcessBuilder builder = new ProcessBuilder(node.toString(), "--max-old-space-size=1024", server.toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("REPORTS_ENGINE_ROOT", server.getParent().toString());
        builder.environment().put("REPORTS_ENGINE_PORT", "3107");
        builder.environment().put("REPORTS_ENGINE_HOST", "127.0.0.1");
        try {
            Process engine = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(engine::destroy));
            for (int i = 0; i < 60 && engine.isAlive(); i++) {
                if (alive(url)) return url;
                Thread.sleep(500);
            }
        } catch (IOException e) {
            // 起動できなくても、プレビューは使える。
        }
        System.out.println("PDF 用のエンジンを起動できませんでした（3107 番が使用中かもしれません）。プレビューはこのまま使えます。");
        return url;
    }

    // 端末から起動したときだけ、ブラウザーで開く（REPORTS_NO_BROWSER=1 で止められる）。
    static void openBrowser(String url) {
        java.io.Console console = System.console();
        if (console == null || System.getenv("REPORTS_NO_BROWSER") != null) return;
        try {
            // JDK 22 からは出力を写していても console が返るので、端末かどうかも見る。
            if (!(Boolean) java.io.Console.class.getMethod("isTerminal").invoke(console)) return;
        } catch (NoSuchMethodException e) {
            // JDK 21 まで：console が null でなければ端末。
        } catch (ReflectiveOperationException e) {
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE))
                java.awt.Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception | LinkageError ignored) { }
    }
}
