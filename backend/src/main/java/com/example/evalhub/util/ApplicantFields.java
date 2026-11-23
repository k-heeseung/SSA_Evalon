package com.example.evalhub.util;

import com.example.evalhub.dto.ApplicantField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

// programs.applicant_fields(JSON 텍스트) ↔ 항목 목록 변환.
// null은 '항목 설정 없음' = 예전 고정 항목 방식(legacy)을 뜻하므로 빈 목록과 구분한다.
public final class ApplicantFields {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<ApplicantField>> TYPE = new TypeReference<>() { };

    private ApplicantFields() {
    }

    // 값이 없으면 null(legacy). 깨진 JSON도 legacy로 보고 예전 방식으로 동작하게 한다.
    public static List<ApplicantField> parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<ApplicantField> fields = MAPPER.readValue(json, TYPE);
            return fields == null ? null : List.copyOf(fields);
        } catch (Exception e) {
            return null;
        }
    }

    public static String toJson(List<ApplicantField> fields) {
        if (fields == null) return null;
        try {
            return MAPPER.writeValueAsString(fields);
        } catch (Exception e) {
            throw new IllegalStateException("지원자 항목 설정을 저장할 수 없습니다.", e);
        }
    }
}
