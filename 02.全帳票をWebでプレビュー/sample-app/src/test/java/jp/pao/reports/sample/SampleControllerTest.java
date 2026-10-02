package jp.pao.reports.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SampleControllerTest {
    @TempDir Path resources;
    private static final String API="/demo/reports.web/samples/java/api";
    private final ObjectMapper json=new ObjectMapper();
    SampleController controller; MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        Files.createDirectories(resources.resolve("images"));
        Files.write(resources.resolve("images/kakuin.png"),new byte[]{1,2,3});
        controller=new SampleController(mock(SampleCatalog.class),json,resources.toString(),"http://127.0.0.1:1","/preview/","/design/",API+"?action=asset&name=");
        mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiErrors()).addFilter(new ResponseHeaders()).build();
    }
    @Test void catalogContainsAllEightSamples() throws Exception {
        mvc.perform(get(API).param("action","catalog")).andExpect(status().isOk())
            .andExpect(jsonPath("$.invoice").exists()).andExpect(jsonPath("$.postal").exists())
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
        assertEquals(8,SampleCatalog.SAMPLES.size());
    }
    @Test void assetAllowlistRejectsTraversalAndNetwork() throws Exception {
        for(String path : new String[]{"../application.properties","https://example.com/test.png","kakuin.png?x=1","/etc/passwd"})
            mvc.perform(get(API).param("action","asset").param("name",path)).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("action","asset").param("name","kakuin.png"))
            .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3}));
    }
    @Test void rejectsUnknownSampleAndAction() throws Exception {
        mvc.perform(get(API).param("sample","unknown")).andExpect(status().isBadRequest());
        mvc.perform(get(API).param("action","execute")).andExpect(status().isBadRequest());
    }
    @Test void rejectsMalformedAndEmptyDocuments() throws Exception {
        for(String body : new String[]{"broken","{}","{\"Pages\":[]}","{\"Format\":\"wrong\",\"Pages\":[{}]}"})
            mvc.perform(post(API).param("action","server-pdf").contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void rejectsOversizeBodyBeforeParsing() throws Exception {
        mvc.perform(post(API).param("action","server-pdf").content(new byte[SampleController.MAX_BYTES+1]))
            .andExpect(status().isPayloadTooLarge());
    }
    @Test void inlinesOnlyRegisteredAssetsAndRejectsRemoteImagePaths() throws Exception {
        var data=json.readTree("{\"Pages\":[{\"Values\":[{\"Value\":\""+API+"?action=asset&name=kakuin.png\"}]}]}");
        controller.inlineAssets(data);
        assertEquals("data:image/png;base64,AQID",data.at("/Pages/0/Values/0/Value").asText());
        for(String path : new String[]{"http://169.254.169.254/latest/meta-data","file:///etc/passwd","/etc/passwd"})
            assertThrows(IllegalArgumentException.class,()->controller.inlineAssets(json.readTree("{\"ImagePath\":\""+path+"\"}")));
    }
    @Test void errorMessagesDoNotExposeInternalPaths() throws Exception {
        mvc.perform(get(API).param("action","asset").param("name","estimate-header.jpg"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error").value("処理に失敗しました。時間をおいて再度お試しください。"));
    }

    @Test void importedPhpImagesResolveLocallyOnlyForExactConfiguredEndpoints() throws Exception {
        String php="https://www.pao.ac/demo/reports.web/sample/php/api.php?action=asset&name=";
        controller.setTrustedAssetBases(php+", https://www.pao.ac/demo/reports.web/samples/php/api.php?action=asset&name=");
        var imported=json.readTree("{\"Pages\":[{\"Definition\":{\"Objects\":[{\"ImagePath\":\""+php+"kakuin.png\"}]},\"Values\":[{\"Name\":\"Image1\",\"Value\":\""+php+"kakuin.png\"}]}]}");
        controller.inlineAssets(imported);
        assertEquals("data:image/png;base64,AQID",imported.at("/Pages/0/Definition/Objects/0/ImagePath").asText());
        assertEquals("data:image/png;base64,AQID",imported.at("/Pages/0/Values/0/Value").asText());
        for(String attack:new String[]{php+"../kakuin.png",php+"kakuin.png&redirect=http://evil.test",php+"%6bakuin.png",php.replace("www.pao.ac","www.pao.ac.evil.test")+"kakuin.png"})
            assertThrows(IllegalArgumentException.class,()->controller.inlineAssets(json.readTree("{\"ImagePath\":\""+attack+"\"}")));
    }
}
