package jp.pao.reports.sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WebResourcesTest {
    @TempDir Path web;
    @Configuration @EnableWebMvc static class MvcOnly {}
    @Test void exactHelpAndFontMapAreServedButUnlistedRootFilesAreNot() throws Exception {
        Files.writeString(web.resolve("preview-help.html"), "<!doctype html><title>Reports Web help</title>");
        Files.writeString(web.resolve("font-map.json"), "{\"version\":1}");
        Files.writeString(web.resolve("internal.json"), "{\"private\":true}");
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MvcOnly.class);
            context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("webResources", new WebResources(web.toString())));
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/demo/reports.web/preview-help.html")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string("<!doctype html><title>Reports Web help</title>"));
            mvc.perform(head("/demo/reports.web/preview-help.html")).andExpect(status().isOk());
            mvc.perform(get("/demo/reports.web/font-map.json")).andExpect(status().isOk())
                .andExpect(content().json("{\"version\":1}"));
            for (String file : new String[]{"fontmap.json", "internal.json", "preview-help.html.bak", ".env"})
                mvc.perform(get("/demo/reports.web/" + file)).andExpect(status().isNotFound());
        }
    }
}
