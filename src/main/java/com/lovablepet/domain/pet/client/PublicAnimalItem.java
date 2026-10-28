package com.lovablepet.domain.pet.client;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 구조동물 조회 응답의 동물 한 마리 (response.body.items.item[]).
 * 원본 JSON 을 그대로 들고 있다가 필요한 값만 꺼내 쓴다. 원본은 pet.raw(JSONB)에 통째로 저장한다.
 * <p>
 * 값은 모두 문자열로 오고 형식이 보장되지 않는다. 변환할 수 없는 값은 예외 없이 null 로 돌려준다.
 */
public final class PublicAnimalItem {

    /** 보호 중인 동물의 상태 값. 공고중(공고 종료일 전)인 동물도 이 값으로 온다. */
    public static final String STATE_PROTECTED = "보호중";

    // "5.0(Kg)" 형식. 실제 데이터에는 "12..00(Kg)"(점 두 개), "6~7(Kg)"(범위), "10,3(Kg)"(쉼표), "5.5kg(Kg)"도 섞여 있다
    private static final Pattern WEIGHT = Pattern.compile("^\\s*([0-9.,~\\s]+?)\\s*(?:kg)?\\s*\\(\\s*kg\\s*\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("120");
    // "2024(년생)", "2026(60일미만)(년생)"
    private static final Pattern BIRTH_YEAR = Pattern.compile("^\\s*(\\d{4})");
    // "2026-10-28 14:10:45.0" (한국 시각)
    private static final DateTimeFormatter UPDATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int PHOTO_FIELDS = 8;

    private final JsonNode node;

    public PublicAnimalItem(JsonNode node) {
        this.node = node;
    }

    public String desertionNo() {
        return text("desertionNo");
    }

    public String noticeNo() {
        return text("noticeNo");
    }

    public String kindName() {
        return text("kindNm");
    }

    public String color() {
        return text("colorCd");
    }

    public String ageText() {
        return text("age");
    }

    public String weightText() {
        return text("weight");
    }

    /** M, F, Q(미상) */
    public String sex() {
        return code("sexCd");
    }

    /** Y, N, U(미상) */
    public String neutered() {
        return code("neuterYn");
    }

    /** 보호중, 종료(입양), 종료(반환) ... */
    public String processState() {
        return text("processState");
    }

    public String orgName() {
        return text("orgNm");
    }

    public String happenPlace() {
        return text("happenPlace");
    }

    public String careName() {
        return text("careNm");
    }

    public String careTel() {
        return text("careTel");
    }

    public String careAddr() {
        return text("careAddr");
    }

    public String careRegNo() {
        return text("careRegNo");
    }

    public String specialMark() {
        return text("specialMark");
    }

    public LocalDate noticeStart() {
        return date("noticeSdt");
    }

    public LocalDate noticeEnd() {
        return date("noticeEdt");
    }

    public BigDecimal weightKg() {
        return parseWeightKg(weightText());
    }

    public Integer birthYear() {
        String age = ageText();
        if (age == null) {
            return null;
        }
        Matcher matcher = BIRTH_YEAR.matcher(age);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /** 원본 수정 시각(updTm). 한국 시각으로 오는 값을 절대 시각으로 바꾼다. 매일 동기화에서 "마지막으로 받은 시점"으로 쓴다. */
    public Instant updatedAt() {
        String value = text("updTm");
        if (value == null || value.length() < 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.substring(0, 19), UPDATED_AT).atZone(KST).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 사진 주소 popfile1~8. 원본은 전부 http:// 인데 https:// 로도 열려서,
     * HTTPS 페이지에서 막히지 않도록 https 로 바꾸고 같은 주소는 하나만 남긴다.
     */
    public List<String> photoUrls() {
        Set<String> urls = new LinkedHashSet<>();
        for (int i = 1; i <= PHOTO_FIELDS; i++) {
            String url = text("popfile" + i);
            if (url != null) {
                urls.add(url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url);
            }
        }
        return List.copyOf(urls);
    }

    public String rawJson() {
        return node.toString();
    }

    /**
     * "5.0(Kg)" → 5.00. "12..00(Kg)" 처럼 점이 겹치면 하나로, "10,3(Kg)" 의 쉼표는 소수점으로,
     * "6~7(Kg)" 처럼 범위면 가운데 값(6.50)으로 본다.
     * 형식이 다르거나 0 이하·120kg 초과면 null (→ AR 생성 불가).
     */
    static BigDecimal parseWeightKg(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = WEIGHT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String number = matcher.group(1).replaceAll("\\s", "").replace(',', '.').replaceAll("\\.{2,}", ".");
        try {
            BigDecimal kg;
            if (number.contains("~")) {
                String[] range = number.split("~");
                if (range.length != 2) {
                    return null;
                }
                kg = new BigDecimal(range[0]).add(new BigDecimal(range[1])).divide(BigDecimal.TWO);
            } else {
                kg = new BigDecimal(number);
            }
            kg = kg.setScale(2, RoundingMode.HALF_UP);
            return kg.signum() > 0 && kg.compareTo(MAX_WEIGHT_KG) <= 0 ? kg : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asString().trim();
        return text.isEmpty() ? null : text;
    }

    private String code(String field) {
        String value = text(field);
        return value != null && value.length() == 1 ? value : null;
    }

    private LocalDate date(String field) {
        String value = text(field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
