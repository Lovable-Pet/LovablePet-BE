package com.lovablepet.domain.pet.client;

import com.lovablepet.domain.pet.exception.PetException;
import com.lovablepet.global.config.PublicAnimalProperties;
import com.lovablepet.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공공데이터포털 구조동물 조회 서비스 (농림축산검역본부 abandonmentPublic_v2). 개(축종 417000)만 조회한다.
 * <p>
 * 요청 주소에 인증키가 들어가므로, 주소나 원인 예외(메시지에 주소가 담긴다)를 로그·응답에 남기지 않는다.
 */
@Component
public class PublicAnimalClient {

    static final String DOG = "417000";
    private static final String PATH = "/abandonmentPublic_v2";
    // 인증키 오류 등은 _type=json 이어도 XML 로 온다: <returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>
    private static final Pattern XML_ERROR = Pattern.compile("<returnAuthMsg>([^<]+)</returnAuthMsg>");

    private final RestClient publicAnimalRestClient;
    private final JsonMapper jsonMapper;
    private final PublicAnimalProperties properties;

    public PublicAnimalClient(
            @Qualifier("publicAnimalRestClient") RestClient publicAnimalRestClient,
            JsonMapper jsonMapper,
            PublicAnimalProperties properties
    ) {
        this.publicAnimalRestClient = publicAnimalRestClient;
        this.jsonMapper = jsonMapper;
        this.properties = properties;
    }

    /** 상태별 목록. state: notice(공고중) | protect(보호중) — 응답의 processState 는 둘 다 "보호중"이다. */
    public PublicAnimalPage fetchByState(String state, int pageNo) {
        return fetch(Map.of("state", state), pageNo);
    }

    /** 원본 수정일(updTm)이 from~to 사이인 동물 (상태 무관). 입양·반환 등으로 종료된 동물을 받는 데 쓴다. */
    public PublicAnimalPage fetchUpdated(LocalDate from, LocalDate to, int pageNo) {
        return fetch(Map.of(
                "bgupd", from.format(DateTimeFormatter.BASIC_ISO_DATE),
                "enupd", to.format(DateTimeFormatter.BASIC_ISO_DATE)), pageNo);
    }

    public Optional<PublicAnimalItem> fetchOne(String desertionNo) {
        return fetch(Map.of("desertion_no", desertionNo), 1).items().stream().findFirst();
    }

    private PublicAnimalPage fetch(Map<String, String> condition, int pageNo) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("upkind", DOG);
        params.putAll(condition);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("numOfRows", String.valueOf(properties.pageSize()));
        return parse(get(uri(params)));
    }

    /**
     * 인증키는 URI 변수로 넣어 엄격하게 인코딩한다 (+ → %2B, / → %2F, = → %3D).
     * 쿼리 문자열에 그대로 붙이면 서버가 + 를 공백으로 읽어 인증에 실패한다.
     */
    URI uri(Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl() + PATH)
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("_type", "json");
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("serviceKey", serviceKey());
        params.forEach((name, value) -> {
            builder.queryParam(name, "{" + name + "}");
            variables.put(name, value);
        });
        return builder.encode().buildAndExpand(variables).toUri();
    }

    /** 포털의 Encoding 인증키(%2B 등)를 넣었어도 Decoding 값으로 되돌린다. */
    private String serviceKey() {
        String key = properties.serviceKey().trim();
        return key.contains("%") ? UriUtils.decode(key, StandardCharsets.UTF_8) : key;
    }

    private String get(URI uri) {
        try {
            return publicAnimalRestClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            throw failure("HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw failure(e.getClass().getSimpleName());
        }
    }

    PublicAnimalPage parse(String body) {
        if (body == null || body.isBlank()) {
            throw failure("빈 응답");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            Matcher matcher = XML_ERROR.matcher(body);
            throw failure(matcher.find() ? matcher.group(1) : "JSON 이 아닌 응답");
        }

        JsonNode response = root.path("response");
        String resultCode = response.path("header").path("resultCode").asString("");
        if (!"00".equals(resultCode)) {
            throw failure("resultCode=" + resultCode + " " + response.path("header").path("resultMsg").asString(""));
        }

        JsonNode bodyNode = response.path("body");
        // 결과가 없으면 items 가 {} 나 "" 로, 한 건이면 item 이 배열이 아니라 객체로 온다
        JsonNode item = bodyNode.path("items").path("item");
        List<PublicAnimalItem> items = new ArrayList<>();
        if (item.isArray()) {
            item.values().forEach(node -> items.add(new PublicAnimalItem(node)));
        } else if (item.isObject()) {
            items.add(new PublicAnimalItem(item));
        }
        return new PublicAnimalPage(items, bodyNode.path("totalCount").asInt(items.size()));
    }

    private PetException failure(String reason) {
        return new PetException(ErrorCode.EXTERNAL_API_ERROR, "공공데이터 구조동물 조회 실패: " + reason);
    }
}
