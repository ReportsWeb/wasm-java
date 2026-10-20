package jp.pao.reports.sample;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static org.junit.jupiter.api.Assertions.*;

/** B17: 体験版で存在しない reports-web.license に 500 を返し、ライセンスのダイアログが出ていた。 */
class ApiErrorsTest {
    @Test void missingStaticFileIs404Not500() {
        var response=new ApiErrors().error(new NoResourceFoundException(HttpMethod.GET,"preview/reports-web.license"));
        assertEquals(404,response.getStatusCode().value());
        assertFalse(response.getBody().get("error").contains("license"));
    }
    @Test void unexpectedFailureStays500() {
        assertEquals(500,new ApiErrors().error(new IllegalStateException("x")).getStatusCode().value());
    }
}