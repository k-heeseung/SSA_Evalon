package com.example.evalhub.dto;

// 매니저가 정한 지원자 인적사항 항목 하나. 모든 항목은 단답형이고 항상 필수다.
// id는 서버가 붙이는 고정 식별자로, 라벨을 고치거나 순서를 바꿔도 지원자가 입력한 값과의 연결이 유지된다.
public record ApplicantField(String id, String label) {
}
