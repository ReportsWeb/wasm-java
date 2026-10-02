package jp.pao.reports.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerPdfTest {
    @TempDir Path resources;
    static final String API="/demo/reports.web/samples/java/api";
    MockMvc mvc(HttpServer engine) {
        var controller=new SampleController(mock(SampleCatalog.class),new ObjectMapper(),resources.toString(),
                "http://127.0.0.1:"+engine.getAddress().getPort(),"/preview/","/design/",API+"?action=asset&name=");
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiErrors()).build();
    }
    @Test void serverPdfPassesDocumentAndReturnsPdf() throws Exception {
        var engine=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        byte[] pdf="%PDF-1.7\ntest\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        var received=new java.util.concurrent.atomic.AtomicReference<String>();
        engine.createContext("/render/pdf",exchange->{
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200,pdf.length);exchange.getResponseBody().write(pdf);exchange.close();
        });engine.start();
        try {
            mvc(engine).perform(post(API).param("action","server-pdf").param("sample","invoice")
                .contentType("application/json").content("{\"Format\":\"Reports.net PrintData\",\"Pages\":[{}]}"))
                .andExpect(status().isOk()).andExpect(content().bytes(pdf))
                .andExpect(header().string("X-Reports-Engine","server-wasm"));
            assertTrue(received.get().contains("Reports.net PrintData"));
        } finally {engine.stop(0);}
    }
    @Test void concurrentRenderingIsBoundedAndSlotRecovers() throws Exception {
        var engine=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        byte[] pdf="%PDF-1.7\ntest".getBytes(StandardCharsets.US_ASCII);
        engine.createContext("/render/pdf",exchange->{
            entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            exchange.sendResponseHeaders(200,pdf.length);exchange.getResponseBody().write(pdf);exchange.close();
        });engine.start();
        var pool=Executors.newSingleThreadExecutor();
        try {
            var client=mvc(engine);
            var first=pool.submit(()->client.perform(post(API).param("action","server-pdf").content("{\"Pages\":[{}]}")));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            client.perform(post(API).param("action","server-pdf").content("{\"Pages\":[{}]}"))
                .andExpect(status().isTooManyRequests());
            release.countDown();first.get(5,TimeUnit.SECONDS).andExpect(status().isOk());
            client.perform(post(API).param("action","server-pdf").content("{\"Pages\":[{}]}"))
                .andExpect(status().isOk());
        } finally { release.countDown();pool.shutdownNow();engine.stop(0); }
    }
}
