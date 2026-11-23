package com.example.evalhub.controller;

import com.example.evalhub.dto.AssignmentResultsResponse;
import com.example.evalhub.service.EvaluationResultScoring;
import com.example.evalhub.service.EvaluationResultsPdfService;
import com.example.evalhub.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

// 결과 조회 권한이 있는 심사위원용. 매니저 '평가 결과' 탭과 같은 데이터를 읽기 전용으로 내려준다.
// 합격/불합격 변경, 팀 상세, 가산점 등 매니저 API는 계속 매니저 전용이다.
@RestController
@RequestMapping("/api/evaluation-assignments/{assignmentId}/results")
@Tag(name = "Evaluator Results", description = "결과 조회 권한 심사위원의 평가 결과 조회·내보내기 API")
@PreAuthorize("hasRole('EVALUATOR') and @assignmentAccess.canViewResults(#assignmentId, principal)")
public class EvaluatorResultsController {

    private final EvaluationService evaluationService;
    private final EvaluationResultsPdfService pdfService;

    public EvaluatorResultsController(EvaluationService evaluationService, EvaluationResultsPdfService pdfService) {
        this.evaluationService = evaluationService;
        this.pdfService = pdfService;
    }

    @Operation(summary = "평가 결과 조회 (결과 조회 권한)", description = "프로그램 정보, 평가 기준, 팀별 결과를 한 번에 조회합니다. 평가 진행 전이면 409입니다.")
    @GetMapping
    public AssignmentResultsResponse getResults(@PathVariable Long assignmentId) {
        return evaluationService.getAssignmentResults(assignmentId);
    }

    @Operation(summary = "평가 결과 PDF 내보내기 (결과 조회 권한)")
    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long assignmentId,
                                              @RequestParam(defaultValue = "false") boolean onlyPassed,
                                              @RequestParam(defaultValue = "ALL") EvaluationResultScoring.Method method) {
        var file = pdfService.export(evaluationService.findResultsProgramId(assignmentId), onlyPassed, method);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
