package com.example.evalhub.repository;

import com.example.evalhub.entity.AssignmentStatus;
import com.example.evalhub.entity.EvaluationAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationAssignmentRepository extends JpaRepository<EvaluationAssignment, Long> {

    List<EvaluationAssignment> findByEvaluatorIdOrderByIdDesc(Long evaluatorId);

    List<EvaluationAssignment> findByProgramIdOrderByIdAsc(Long programId);

    // 결과 집계의 심사위원 열. 결과 조회만 가진 배정은 심사위원으로 세지 않는다.
    List<EvaluationAssignment> findByProgramIdAndCanEvaluateTrueOrderByIdAsc(Long programId);

    Optional<EvaluationAssignment> findByProgramIdAndEvaluatorId(Long programId, Long evaluatorId);

    boolean existsByProgramIdAndEvaluatorIdAndCanEvaluateTrue(Long programId, Long evaluatorId);

    long countByProgramIdAndCanEvaluateTrue(Long programId);

    long countByEvaluatorIdAndCanEvaluateTrue(Long evaluatorId);

    long countByEvaluatorIdAndCanViewResultsTrue(Long evaluatorId);

    long countByProgramIdAndStatusAndCanEvaluateTrue(Long programId, AssignmentStatus status);
}
