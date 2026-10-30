package com.lovablepet.domain.pet.client;

import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.global.config.PublicAnimalProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PublicAnimalClientTest {

    // 포털 인증키처럼 +, /, = 가 들어간 가짜 키
    private static final String KEY = "ab+cd/ef==";
    private static final String BASE_URL = "https://apis.example.test/svc";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("인증키의 +, /, = 를 %2B, %2F, %3D 로 인코딩한다")
    void uri_encodesServiceKeyStrictly() {
        URI uri = client(KEY, RestClient.create()).uri(Map.of("state", "protect"));

        assertThat(uri.getRawQuery())
                .contains("serviceKey=ab%2Bcd%2Fef%3D%3D")
                .contains("_type=json")
                .contains("state=protect")
                .doesNotContain("+");
        assertThat(uri.getRawPath()).isEqualTo("/svc/abandonmentPublic_v2");
    }

    @Test
    @DisplayName("포털의 Encoding 인증키(%2B...)를 넣어도 한 번만 인코딩된다")
    void uri_acceptsEncodedServiceKey() {
        URI uri = client("ab%2Bcd%2Fef%3D%3D", RestClient.create()).uri(Map.of());

        assertThat(uri.getRawQuery()).contains("serviceKey=ab%2Bcd%2Fef%3D%3D").doesNotContain("%25");
    }

    @Test
    @DisplayName("여러 건 응답: 동물 목록과 전체 건수를 읽는다")
    void parse_items() throws IOException {
        PublicAnimalPage page = client(KEY, RestClient.create()).parse(fixture("protect-page.json"));

        assertThat(page.totalCount()).isEqualTo(3);
        assertThat(page.items()).extracting(PublicAnimalItem::desertionNo)
                .containsExactly("413588202600395", "445464202602934", "445479202601110");
    }

    @Test
    @DisplayName("한 건이면 item 이 객체로, 없으면 items 가 {} 나 \"\" 로 온다")
    void parse_singleAndEmpty() {
        PublicAnimalClient client = client(KEY, RestClient.create());

        PublicAnimalPage single = client.parse(ok("{\"items\":{\"item\":{\"desertionNo\":\"1\"}},\"totalCount\":1}"));
        assertThat(single.items()).extracting(PublicAnimalItem::desertionNo).containsExactly("1");

        assertThat(client.parse(ok("{\"items\":{},\"totalCount\":0}")).items()).isEmpty();
        assertThat(client.parse(ok("{\"items\":\"\",\"totalCount\":0}")).items()).isEmpty();
    }

    @Test
    @DisplayName("resultCode 가 00 이 아니면 EXTERNAL_502")
    void parse_errorResultCode() {
        String body = "{\"response\":{\"header\":{\"resultCode\":\"22\",\"resultMsg\":\"LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR.\"}}}";

        assertThatThrownBy(() -> client(KEY, RestClient.create()).parse(body))
                .isInstanceOf(PetException.class)
                .hasMessageContaining("resultCode=22")
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    @DisplayName("인증키 오류는 XML 로 오고, 그 사유를 메시지에 담는다")
    void parse_xmlAuthError() {
        String body = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
                + "<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";

        assertThatThrownBy(() -> client(KEY, RestClient.create()).parse(body))
                .isInstanceOf(PetException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
    }

    @Test
    @DisplayName("HTTP 오류여도 예외 메시지에 요청 주소(인증키)를 남기지 않는다")
    void fetch_httpError_hidesServiceKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicAnimalClient client = client(KEY, builder.build());
        server.expect(requestTo(containsString("serviceKey=ab%2Bcd%2Fef%3D%3D")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        assertThatThrownBy(() -> client.fetchByState("protect", 1))
                .isInstanceOf(PetException.class)
                .hasMessageContaining("HTTP 500")
                .hasNoCause()
                .message().doesNotContain("ab+cd").doesNotContain("ab%2Bcd").doesNotContain("serviceKey");
        server.verify();
    }

    @Test
    @DisplayName("요청에 개 축종(417000), 페이지, 페이지 크기가 들어간다")
    void fetch_sendsDogPagingParams() throws IOException {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PublicAnimalClient client = client(KEY, builder.build());
        server.expect(requestTo(allOf(
                        containsString("upkind=417000"),
                        containsString("state=protect"),
                        containsString("pageNo=2"),
                        containsString("numOfRows=1000"))))
                .andRespond(withSuccess(fixture("protect-page.json"), MediaType.APPLICATION_JSON));

        assertThat(client.fetchByState("protect", 2).items()).hasSize(3);
        server.verify();
    }

    private PublicAnimalClient client(String serviceKey, RestClient restClient) {
        PublicAnimalProperties properties = new PublicAnimalProperties(
                BASE_URL, serviceKey, 1000, Duration.ofSeconds(10), Duration.ofSeconds(60));
        return new PublicAnimalClient(restClient, jsonMapper, properties);
    }

    private static String ok(String body) {
        return "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"NORMAL SERVICE.\"},\"body\":" + body + "}}";
    }

    static String fixture(String name) throws IOException {
        return new ClassPathResource("public-animal/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
