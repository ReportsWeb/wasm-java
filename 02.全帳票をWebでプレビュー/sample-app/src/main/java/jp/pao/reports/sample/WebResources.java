package jp.pao.reports.sample;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.Path;
import java.util.Map;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Local Docker serves the same built browser bundle; production Apache serves it directly. */
@Configuration
class WebResources implements WebMvcConfigurer {
    private final String root;
    WebResources(@Value("${REPORTS_WEB_ROOT:/opt/reports/web}") String root) { this.root=root; }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String base=Path.of(root).toAbsolutePath().toUri().toString();
        if (!base.endsWith("/")) base+="/";
        for (String folder : new String[]{"preview", "design", "assets", "barcode", "fonts", "wasm"})
            registry.addResourceHandler("/demo/reports.web/"+folder+"/**").addResourceLocations(base+folder+"/");
        for(String file : new String[]{"font-map.json", "fontmap.json", "preview-help.html"})
            registry.addResourceHandler("/demo/reports.web/"+file).addResourceLocations(base)
                .resourceChain(false).addResolver(new PathResourceResolver() {
                    // An exact mapping retains the entire request path. Resolve only this
                    // allowlisted filename, never expose the remaining root directory.
                    @Override protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        return super.getResource(file, location);
                    }
                });
    }
    @Override public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/demo/reports.web/samples/java/");
        registry.addViewController("/demo/reports.web/preview/").setViewName("forward:/demo/reports.web/preview/index.html");
        registry.addViewController("/demo/reports.web/design/").setViewName("forward:/demo/reports.web/design/index.html");
        registry.addRedirectViewController("/demo/reports.web/samples/java", "/demo/reports.web/samples/java/");
    }
}

@RestController
class HealthController {
    @GetMapping({"/health", "/demo/reports.web/samples/java/health"})
    Map<String,String> health() { return Map.of("status", "ok", "sample", "java-spring-boot"); }
}
