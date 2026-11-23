package com.example.evalhub.service;

import com.example.evalhub.invitation.EvaluatorInvitationService;
import com.example.evalhub.invitation.InvitationSecrets;

import com.example.evalhub.dto.*;
import com.example.evalhub.entity.*;
import com.example.evalhub.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import com.example.evalhub.util.ApplicantFields;
import com.example.evalhub.util.ProgramTimes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProgramManagementService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EvaluationCriterionRepository criterionRepository;
    private final EvaluationAssignmentRepository assignmentRepository;
    private final ApplicationFormFieldRepository formFieldRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationAnswerRepository answerRepository;
    private final ParticipantAttachmentRepository attachmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EvaluatorInvitationService invitationService;

    public ProgramManagementService(
            ProgramRepository programRepository,
            UserRepository userRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            EvaluationCriterionRepository criterionRepository,
            EvaluationAssignmentRepository assignmentRepository,
            ApplicationFormFieldRepository formFieldRepository,
            ApplicationRepository applicationRepository,
            ApplicationAnswerRepository answerRepository,
            ParticipantAttachmentRepository attachmentRepository,
            PasswordEncoder passwordEncoder,
            EvaluatorInvitationService invitationService
    ) {
        this.programRepository = programRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.criterionRepository = criterionRepository;
        this.assignmentRepository = assignmentRepository;
        this.formFieldRepository = formFieldRepository;
        this.applicationRepository = applicationRepository;
        this.answerRepository = answerRepository;
        this.attachmentRepository = attachmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.invitationService = invitationService;
    }

    // 프로그램 편집 화면의 전체 상태(기본 정보·평가 기준·지원 양식 항목)를 한 트랜잭션으로 생성한다.
    // 중간에 하나라도 실패하면 전부 롤백되어 반쯤 만들어진 DRAFT가 남지 않는다.
    // managerId는 요청 본문이 아니라 로그인 정보로 정한다. (MANAGER가 만들면 본인, 그 외에는 null)
    @Transactional
    public ProgramEditorResponse createProgram(SaveProgramRequest request, Long managerId) {
        validateProgramBasics(request, true);
        User manager = managerId == null ? null : userRepository.findById(managerId)
                .orElseThrow(() -> notFound("Manager not found."));
        Instant applicationStart = ProgramTimes.resolve(request.applicationStartAt(), null, null, null, false);
        Instant applicationDeadline = ProgramTimes.resolve(request.applicationDeadlineAt(), null, null, null, true);
        Instant evaluationStart = ProgramTimes.resolve(request.evaluationStartAt(), null, null, null, false);
        Instant evaluationDeadline = ProgramTimes.resolve(request.evaluationDeadlineAt(), null, null, null, true);
        validateProgramDates(applicationStart, applicationDeadline, evaluationStart, evaluationDeadline);

        Program program = new Program(manager, request.title().trim(), request.description(), request.type(),
                null, null, null, null, request.eligibilityCriteria());
        program.updateSchedule(applicationStart, applicationDeadline, evaluationStart, evaluationDeadline);
        program.changeStatus(Status.DRAFT);
        program.updateAttachmentSettings(request.attachmentsEnabled(), request.maxAttachments());
        program.updateParticipantType(request.participantType());
        updateBonusItems(program, request.bonusEnabled(), null, request.bonusItems());
        Program saved = programRepository.save(program);
        saveApplicantFields(saved, request.applicantFields(), true);

        saveCriteria(saved, request.criteria());
        saveFormFields(saved, request.formFields());
        if (request.publish()) publish(saved);
        entityManager.flush();
        return editorResponse(saved);
    }

    // 기존 프로그램을 화면의 전체 상태로 저장한다. 임시저장 이어쓰기·수정·임시저장 후 개설이 모두 이 경로를 쓴다.
    // 동시 수정은 막지 않는다. 나중에 저장한 쪽이 이긴다.
    @Transactional
    public ProgramEditorResponse updateProgram(Long programId, SaveProgramRequest request) {
        validateProgramBasics(request, false);
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> notFound("Program not found."));

        entityManager.refresh(program, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (request.publish() && program.getStatus() != Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "임시저장 프로그램만 개설할 수 있습니다.");
        }

        // 일정 값이 null이면 기존 값을 유지한다. (저장된 일정은 화면에서 비울 수 없다)
        Instant applicationStart = ProgramTimes.resolve(request.applicationStartAt(), null, null, ProgramTimes.applicationStart(program), false);
        Instant applicationDeadline = ProgramTimes.resolve(request.applicationDeadlineAt(), null, null, ProgramTimes.applicationDeadline(program), true);
        Instant evaluationStart = ProgramTimes.resolve(request.evaluationStartAt(), null, null, ProgramTimes.evaluationStart(program), false);
        Instant evaluationDeadline = ProgramTimes.resolve(request.evaluationDeadlineAt(), null, null, ProgramTimes.evaluationDeadline(program), true);
        boolean scheduleChanged = !Objects.equals(ProgramTimes.applicationStart(program), applicationStart)
                || !Objects.equals(ProgramTimes.applicationDeadline(program), applicationDeadline)
                || !Objects.equals(ProgramTimes.evaluationStart(program), evaluationStart)
                || !Objects.equals(ProgramTimes.evaluationDeadline(program), evaluationDeadline);
        if (scheduleChanged) validateProgramDates(applicationStart, applicationDeadline, evaluationStart, evaluationDeadline);

        boolean applicationDeadlineChanged = !Objects.equals(ProgramTimes.applicationDeadline(program), applicationDeadline);
        if (applicationDeadlineChanged && !APPLICATION_DEADLINE_EDITABLE.contains(program.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "평가가 시작된 뒤에는 지원 마감일을 변경할 수 없습니다.");
        }

        // 평가 마감일이 실제로 바뀐 경우에만 완료(INACTIVE) 상태를 다시 연다.
        // 제목만 고쳤는데 매니저가 수동으로 종료한 프로그램이 되살아나면 안 되기 때문이다.
        boolean evaluationDeadlineChanged = !Objects.equals(ProgramTimes.evaluationDeadline(program), evaluationDeadline);

        // 담당 매니저는 생성 시에만 정한다. 수정하는 사람으로 바뀌지 않는다.
        program.update(request.title().trim(), request.description(), request.type(), null,
                null, null, null, null, request.eligibilityCriteria());
        program.updateSchedule(applicationStart, applicationDeadline, evaluationStart, evaluationDeadline);
        program.updateAttachmentSettings(request.attachmentsEnabled(), request.maxAttachments());
        program.updateParticipantType(request.participantType());
        updateBonusItems(program, request.bonusEnabled(), null, request.bonusItems());
        saveApplicantFields(program, request.applicantFields(), false);

        saveCriteria(program, request.criteria());
        saveFormFields(program, request.formFields());
        if (request.publish()) {
            publish(program);
        } else {
            // 기간이 바뀌면 즉시 재계산한다. 평가 마감일을 늘렸다면 INACTIVE(평가 완료)도 다시 연다.
            applyAutoStatus(program, evaluationDeadlineChanged);
        }
        entityManager.flush();
        return editorResponse(program);
    }

    private void validateProgramBasics(SaveProgramRequest request, boolean creating) {
        if (request.title() == null || request.title().isBlank() || request.title().trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "프로그램 명을 1~255자로 입력해주세요.");
        }
        if (creating && request.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "프로그램 유형을 선택해주세요.");
        }
        if (request.criteria() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "평가 기준 목록(criteria)이 필요합니다. 비우려면 빈 배열을 보내주세요.");
        }
        if (request.formFields() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원 양식 항목 목록(formFields)이 필요합니다. 비우려면 빈 배열을 보내주세요.");
        }
    }

    // DRAFT를 개설한다. 이후 실제 상태는 현재 시각과 기간으로 계산한다.
    private void publish(Program program) {
        if (program.getStatus() != Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "임시저장 프로그램만 개설할 수 있습니다.");
        }
        requirePublishDates(ProgramTimes.applicationDeadline(program), ProgramTimes.evaluationDeadline(program));
        validateProgramDates(ProgramTimes.applicationStart(program), ProgramTimes.applicationDeadline(program),
                ProgramTimes.evaluationStart(program), ProgramTimes.evaluationDeadline(program));
        program.changeStatus(Status.ACTIVE);
        applyAutoStatus(program);
    }

    private ProgramEditorResponse editorResponse(Program program) {
        return new ProgramEditorResponse(
                ProgramResponse.from(program),
                findCriteria(program.getId()),
                findFormFields(program.getId())
        );
    }

    // 평가 기준을 id 기준 diff로 저장한다. 배열 순서가 displayOrder가 된다.
    //
    // 심사가 시작된 프로그램은 이름·설명만 고칠 수 있다. 추가·삭제·배점 변경·순서 변경은 409.
    //
    // evaluation_criteria에 unique(program_id, display_order)가 있고, Hibernate는 한 번의 flush에서
    // INSERT → UPDATE → DELETE 순으로 실행한다. 그래서 삭제 → flush → 남길 행을 임시 음수 순서로 → flush
    // → 최종 순서와 신규 추가 순으로 나눠 반영해야 제약에 걸리지 않는다.
    private void saveCriteria(Program program, List<SaveProgramRequest.Criterion> items) {
        List<EvaluationCriterion> current = program.getId() == null ? List.of()
                : criterionRepository.findByProgramIdOrderByDisplayOrderAsc(program.getId());
        Map<Long, EvaluationCriterion> existing = new LinkedHashMap<>();
        current.forEach(criterion -> existing.put(criterion.getId(), criterion));

        Set<Long> keptIds = new HashSet<>();
        for (SaveProgramRequest.Criterion item : items) {
            if (item == null || item.name() == null || item.name().isBlank() || item.name().trim().length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "평가 기준 이름을 1~255자로 입력해주세요.");
            }
            validateCriterionMaxScore(item.maxScore());
            if (item.id() == null) continue;
            if (!existing.containsKey(item.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "평가 기준 " + item.id() + "은(는) 이 프로그램에 속하지 않습니다.");
            }
            if (!keptIds.add(item.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 평가 기준이 두 번 포함되었습니다: " + item.id());
            }
        }

        if (program.isEvaluationStarted()) {
            List<Long> requestedOrder = items.stream().map(SaveProgramRequest.Criterion::id).toList();
            boolean structureChanged = requestedOrder.contains(null)
                    || !requestedOrder.equals(new ArrayList<>(existing.keySet()));
            boolean maxChanged = items.stream().anyMatch(item -> item.id() != null
                    && item.maxScore().compareTo(existing.get(item.id()).getMaxScore()) != 0);
            if (structureChanged || maxChanged) throw criteriaLocked();
            // 순서는 그대로 두고 이름·설명만 반영한다.
            for (SaveProgramRequest.Criterion item : items) {
                EvaluationCriterion criterion = existing.get(item.id());
                criterion.update(item.name().trim(), blankToNull(item.description()), criterion.getWeight(), criterion.getDisplayOrder());
            }
            return;
        }

        List<EvaluationCriterion> toDelete = existing.values().stream()
                .filter(criterion -> !keptIds.contains(criterion.getId()))
                .toList();
        if (!toDelete.isEmpty()) {
            criterionRepository.deleteAll(toDelete);
            entityManager.flush();
        }

        // 남길 행을 서로 겹치지 않는 임시 음수 순서로 옮긴다.
        int temp = -1;
        for (SaveProgramRequest.Criterion item : items) {
            if (item.id() == null) continue;
            EvaluationCriterion criterion = existing.get(item.id());
            criterion.update(criterion.getName(), criterion.getDescription(), criterion.getWeight(), temp--);
        }
        entityManager.flush();

        for (int i = 0; i < items.size(); i++) {
            SaveProgramRequest.Criterion item = items.get(i);
            String description = blankToNull(item.description());
            if (item.id() == null) {
                EvaluationCriterion criterion = new EvaluationCriterion(program, item.name().trim(), description, BigDecimal.ONE, i + 1);
                criterion.updateMaxScore(item.maxScore());
                criterionRepository.save(criterion);
            } else {
                EvaluationCriterion criterion = existing.get(item.id());
                criterion.update(item.name().trim(), description, criterion.getWeight(), i + 1);
                criterion.updateMaxScore(item.maxScore());
            }
        }
        entityManager.flush();
    }

    private static final Set<String> FORM_FIELD_TYPES = Set.of("SHORT_TEXT", "LONG_TEXT", "RADIO", "CHECKBOX");
    private static final Set<String> OPTION_FIELD_TYPES = Set.of("RADIO", "CHECKBOX");

    // 지원 양식 항목을 id 기준 diff로 저장한다. 배열 순서가 displayOrder가 된다.
    //
    // 항목을 통째로 지우고 다시 만들면 application_answers의 on delete cascade 때문에
    // 지원자가 이미 제출한 답변이 함께 사라진다. 따라서 id를 기준으로 차이만 반영한다.
    //   - id가 있는 항목: 기존 행을 제자리에서 수정한다 (id 유지 → 답변 보존)
    //   - id가 null인 항목: 새로 추가한다
    //   - 요청에 없는 기존 항목: 삭제한다. 단 답변이 달려 있으면 409로 막는다 (트랜잭션 전체가 롤백된다)
    private static final int MAX_APPLICANT_FIELDS = 30;
    private static final int MAX_APPLICANT_FIELD_LABEL = 100;

    // 지원자 인적사항 항목을 id 기준 diff로 저장한다. 배열 순서가 곧 표시 순서다.
    // requested가 null이면 손대지 않는다. (POST는 예전 고정 항목 방식으로 남고, PUT은 기존 설정을 유지)
    //
    // - id가 null인 항목은 새 항목이다. 서버가 id를 붙인다.
    // - id가 있는 항목은 라벨·순서만 바뀐다. id가 유지되므로 지원자가 입력한 값과의 연결도 유지된다.
    // - 요청에 없는 기존 항목은 삭제한다. 단 지원자가 이미 값을 입력한 항목이면 409.
    // - 예전 고정 항목 방식 프로그램은 지원서가 하나도 없을 때만 직접 설정 방식으로 바꿀 수 있다.
    //   (기존 지원서의 이름·이메일·연락처는 새 항목으로 옮길 수 없기 때문)
    private void saveApplicantFields(Program program, List<ApplicantField> requested, boolean creating) {
        if (requested == null) return;
        List<ApplicantField> existing = ApplicantFields.parse(program.getApplicantFields());
        if (existing == null && !creating && applicationRepository.existsByProgramId(program.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이미 지원서가 있는 프로그램은 지원자 정보 항목 방식을 바꿀 수 없습니다.");
        }
        if (requested.size() > MAX_APPLICANT_FIELDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "지원자 정보 항목은 최대 " + MAX_APPLICANT_FIELDS + "개까지 추가할 수 있습니다.");
        }

        Map<String, ApplicantField> existingById = new LinkedHashMap<>();
        if (existing != null) existing.forEach(f -> existingById.put(f.id(), f));

        List<ApplicantField> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> seenLabels = new HashSet<>();
        for (ApplicantField item : requested) {
            String label = item == null || item.label() == null ? "" : item.label().trim();
            if (label.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원자 정보 항목의 이름을 입력해주세요.");
            }
            if (label.length() > MAX_APPLICANT_FIELD_LABEL) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "지원자 정보 항목 이름은 " + MAX_APPLICANT_FIELD_LABEL + "자 이하로 입력해주세요.");
            }
            if (program.getParticipantType() == ParticipantType.TEAM && label.replace(" ", "").equals("팀이름")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "팀 이름은 항상 받는 항목이라 따로 추가할 필요가 없습니다.");
            }
            if (!seenLabels.add(label.toLowerCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원자 정보 항목 이름이 중복됩니다: " + label);
            }
            String id = item.id();
            if (id == null || id.isBlank()) {
                id = java.util.UUID.randomUUID().toString();
            } else if (!existingById.containsKey(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이 프로그램에 없는 지원자 정보 항목입니다: " + label);
            }
            if (!seenIds.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 지원자 정보 항목이 두 번 들어 있습니다: " + label);
            }
            result.add(new ApplicantField(id, label));
        }

        List<ApplicantField> removed = existingById.values().stream()
                .filter(f -> !seenIds.contains(f.id()))
                .toList();
        if (!removed.isEmpty()) {
            List<ApplicantInfo> infos = teamRepository.findByProgramIdOrderByIdAsc(program.getId()).stream()
                    .map(t -> ApplicantInfo.parse(t.getApplicantInfo()))
                    .toList();
            for (ApplicantField field : removed) {
                if (infos.stream().anyMatch(info -> hasApplicantValue(info, field.id()))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "지원자가 이미 입력한 항목은 삭제할 수 없습니다: " + field.label());
                }
            }
        }
        program.updateApplicantFields(ApplicantFields.toJson(result));
    }

    private static boolean hasApplicantValue(ApplicantInfo info, String fieldId) {
        if (isNotBlank(info.values().get(fieldId))) return true;
        return info.members().stream()
                .anyMatch(m -> m.values() != null && isNotBlank(m.values().get(fieldId)));
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private void saveFormFields(Program program, List<SaveProgramRequest.FormField> items) {
        Map<Long, ApplicationFormField> existing = new LinkedHashMap<>();
        for (ApplicationFormField field : formFieldRepository.findByProgramIdOrderByDisplayOrderAsc(program.getId())) {
            existing.put(field.getId(), field);
        }

        // 남겨둘 항목을 먼저 가려낸다. 나머지가 삭제 대상이다.
        Set<Long> keptIds = new HashSet<>();
        for (SaveProgramRequest.FormField item : items) {
            if (item == null || item.label() == null || item.label().isBlank() || item.label().trim().length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원 양식 항목 이름을 1~255자로 입력해주세요.");
            }
            if (item.fieldType() == null || !FORM_FIELD_TYPES.contains(item.fieldType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원 양식 항목 유형이 올바르지 않습니다: " + item.label().trim());
            }
            if (OPTION_FIELD_TYPES.contains(item.fieldType()) && joinOptions(item.options()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "선택지 유형 항목에 선택지를 최소 1개 입력해주세요: " + item.label().trim());
            }
            if (item.id() == null) continue;
            if (!existing.containsKey(item.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "지원 양식 항목 " + item.id() + "은(는) 이 프로그램에 속하지 않습니다.");
            }
            if (!keptIds.add(item.id())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "같은 지원 양식 항목이 두 번 포함되었습니다: " + item.id());
            }
        }

        List<ApplicationFormField> toDelete = existing.values().stream()
                .filter(field -> !keptIds.contains(field.getId()))
                .toList();

        guardAnsweredFieldsNotDeleted(toDelete);

        // 화면 순서(i + 1)를 displayOrder로 다시 매긴다.
        List<ApplicationFormField> toSave = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            SaveProgramRequest.FormField item = items.get(i);
            String options = OPTION_FIELD_TYPES.contains(item.fieldType()) ? joinOptions(item.options()) : null;
            if (item.id() == null) {
                toSave.add(new ApplicationFormField(
                        program, item.label().trim(), item.fieldType(), item.required(), i + 1, options
                ));
            } else {
                ApplicationFormField field = existing.get(item.id());
                field.update(item.label().trim(), item.fieldType(), item.required(), i + 1, options);
                toSave.add(field);
            }
        }

        if (!toDelete.isEmpty()) {
            formFieldRepository.deleteAll(toDelete);
        }
        formFieldRepository.saveAll(toSave);
        entityManager.flush();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    // 지원자에게 노출할 프로그램 목록: 접수 중(RECRUITING) + 접수 예정(SCHEDULED).
    // 실제 지원 가능 여부는 상태로 구분하며, 지원 시작 전(SCHEDULED)에는 프론트에서 '접수 예정'으로 표시한다.
    @Transactional(readOnly = true)
    public List<ProgramResponse> findOpenPrograms() {
        return programRepository
                .findByStatusInOrderByApplicationDeadlineAtAsc(List.of(Status.RECRUITING, Status.SCHEDULED)).stream()
                .filter(program -> {
                    Status status = ProgramScheduleResponse.from(program).periodStatus();
                    return status == Status.RECRUITING || status == Status.SCHEDULED;
                })
                .map(ProgramResponse::from)
                .toList();
    }

    // 지원·평가 기간에 맞춰 전체 프로그램 상태를 동기화한다. 스케줄러가 주기적으로 호출한다.
    @Transactional
    public int syncProgramStatuses() {
        int changed = 0;
        for (Program program : programRepository.findAll()) {
            if (applyAutoStatus(program)) changed++;
        }
        return changed;
    }

    // 시작 시각은 포함하고 마감 시각은 제외한다. DRAFT와 수동 종료는 유지한다.
    // 평가 기간 연장 시에만 INACTIVE를 다시 계산한다.
    private Status resolveAutoStatus(Program program, boolean reopenInactive) {
        Status current = program.getStatus();
        if (current == Status.DRAFT) return null;
        if (current == Status.INACTIVE && !reopenInactive) return null;

        Instant applicationDeadline = ProgramTimes.applicationDeadline(program);
        if (applicationDeadline == null) return null;

        Instant now = Instant.now();
        Instant applicationStart = ProgramTimes.applicationStart(program);
        if (applicationStart != null && now.isBefore(applicationStart)) return Status.SCHEDULED;
        if (now.isBefore(applicationDeadline)) return Status.RECRUITING;

        Instant evaluationDeadline = ProgramTimes.evaluationDeadline(program);
        if (evaluationDeadline != null && !now.isBefore(evaluationDeadline)) return Status.INACTIVE;

        Instant evaluationStart = ProgramTimes.evaluationStart(program);
        if (evaluationStart != null && now.isBefore(evaluationStart)) return Status.PENDING_EVALUATION;
        return Status.ACTIVE;
    }

    // 단일 프로그램에 자동 상태 규칙을 적용한다. 실제로 바뀌면 true.
    // INACTIVE인 프로그램은 그대로 둔다. 다시 열려면 applyAutoStatus(program, true)를 쓴다.
    private boolean applyAutoStatus(Program program) {
        return applyAutoStatus(program, false);
    }

    private boolean applyAutoStatus(Program program, boolean reopenInactive) {
        Status target = resolveAutoStatus(program, reopenInactive);
        if (target != null && target != program.getStatus()) {
            program.changeStatus(target);
            return true;
        }
        return false;
    }

    // 현재는 전체 프로그램을 반환한다. 인증 도입 후에는 manager 권한에 맞춰 필터링한다.
    @Transactional(readOnly = true)
    public List<ProgramResponse> findPrograms() {
        return programRepository.findAll().stream()
                .map(p -> ProgramResponse.from(
                        p,
                        teamRepository.countByProgramId(p.getId()) - draftTeamIds(p.getId()).size(),
                        // 결과 조회 권한만 가진 배정은 심사위원 수·평가율에서 뺀다.
                        assignmentRepository.countByProgramIdAndCanEvaluateTrue(p.getId()),
                        assignmentRepository.countByProgramIdAndStatusAndCanEvaluateTrue(p.getId(), AssignmentStatus.SUBMITTED)
                ))
                .toList();
    }

    // 지원 마감일을 바꿀 수 있는 상태.
    // 평가가 시작된 뒤(ACTIVE)나 끝난 뒤(INACTIVE)에 접수 기간을 늘리면 이미 진행된 평가와 어긋나므로 막는다.
    private static final Set<Status> APPLICATION_DEADLINE_EDITABLE = Set.of(
            Status.DRAFT, Status.SCHEDULED, Status.RECRUITING, Status.PENDING_EVALUATION
    );


    // 개설(공개)되는 프로그램은 지원 마감일과 평가 마감일이 모두 있어야 한다. 임시 저장(DRAFT)은 예외.
    // 지원 마감일이 없으면 resolveAutoStatus가 null을 반환해 자동 전환이 아예 돌지 않고,
    // 평가 마감일이 없으면 평가 완료 시점을 판단할 수 없어 계속 ACTIVE(평가 진행)로 남는다.
    private void requirePublishDates(Instant applicationDeadline, Instant evaluationDeadline) {
        if (applicationDeadline == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application deadline is required to publish a program.");
        }
        if (evaluationDeadline == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Evaluation deadline is required to publish a program.");
        }
    }

    private void validateProgramDates(
            Instant applicationStart,
            Instant applicationDeadline,
            Instant evaluationStart,
            Instant evaluationDeadline
    ) {
        if (applicationStart != null && applicationDeadline != null
                && !applicationStart.isBefore(applicationDeadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application start must be before its deadline.");
        }
        if (applicationDeadline != null && evaluationStart != null
                && evaluationStart.isBefore(applicationDeadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evaluation start must not be before the application deadline.");
        }
        if (evaluationStart != null && evaluationDeadline != null
                && !evaluationStart.isBefore(evaluationDeadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evaluation deadline must be after its start.");
        }
    }


    // Module A 개인 참가자는 participantType=INDIVIDUAL인 1인 팀으로 저장한다.
    @Transactional
    public TeamResponse createTeam(Long programId, CreateTeamRequest request) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> notFound("Program not found."));

        ParticipantType participantType = request.participantType() == null
                ? ParticipantType.TEAM
                : request.participantType();

        Team team = teamRepository.save(new Team(
                program,
                request.name(),
                participantType,
                request.description(),
                request.name(),
                request.submissionUrl(),
                request.notes()
        ));
        if (request.memberIds() != null) {
            for (Long memberId : request.memberIds()) {
                User member = userRepository.findById(memberId)
                        .orElseThrow(() -> notFound("Member not found: " + memberId));
                teamMemberRepository.save(new TeamMember(team, member));
            }
        }

        return teamResponse(team);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> findTeams(Long programId) {
        Set<Long> draftTeamIds = draftTeamIds(programId);
        return teamRepository.findByProgramIdOrderByIdAsc(programId).stream()
                .filter(team -> !draftTeamIds.contains(team.getId()))
                .map(this::teamResponse)
                .toList();
    }

    // 아직 제출되지 않은(초안) 지원서에 연결된 팀 ID. 매니저 화면에는 제출한 지원 팀만 노출한다.
    private Set<Long> draftTeamIds(Long programId) {
        return applicationRepository.findByProgramIdAndStatus(programId, ApplicationStatus.DRAFT).stream()
                .map(Application::getTeam)
                .filter(Objects::nonNull)
                .map(Team::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public TeamResponse findTeam(Long programId, Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Team not found."));
        if (!team.getProgram().getId().equals(programId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team does not belong to this program.");
        }
        return teamResponse(team);
    }

    @Transactional
    public TeamResponse updateTeam(Long programId, Long teamId, UpdateTeamRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Team not found."));
        if (!team.getProgram().getId().equals(programId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team does not belong to this program.");
        }

        team.update(request.name(), request.description(), request.name(), request.submissionUrl(), request.notes());
        return teamResponse(team);
    }

    @Transactional
    public TeamResponse changeTeamStatus(Long programId, Long teamId, ChangeTeamStatusRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Team not found."));
        if (!team.getProgram().getId().equals(programId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team does not belong to this program.");
        }

        team.changeStatus(request.status());
        return teamResponse(team);
    }

    // One transaction validates every item before replacing a team's score values.
    @Transactional
    public TeamResponse updateTeamBonus(Long programId, Long teamId, UpdateTeamBonusRequest request) {
        Program program = programRepository.findById(programId).orElseThrow(() -> notFound("Program not found."));
        entityManager.refresh(program, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        Team team = teamRepository.findById(teamId).orElseThrow(() -> notFound("Team not found."));
        if (!team.getProgram().getId().equals(programId)) throw badBonus("다른 프로그램의 팀입니다.");
        if (!program.isBonusEnabled()) throw badBonus("가산점을 사용하지 않는 프로그램입니다.");
        if (request.scores() == null) throw badBonus("가산점 항목별 점수가 필요합니다.");
        Map<Long, BonusItem> items = program.getBonusItems().stream()
                .collect(Collectors.toMap(BonusItem::getId, item -> item));
        Set<Long> seen = new HashSet<>();
        for (UpdateTeamBonusRequest.Item value : request.scores()) {
            if (value == null || value.itemId() == null || !items.containsKey(value.itemId()) || !seen.add(value.itemId())) {
                throw badBonus("가산점 항목이 잘못되었거나 중복되었습니다.");
            }
            validateBonusNumber(value.score(), true);
            BigDecimal max = items.get(value.itemId()).getMaxScore();
            if (value.score() != null && max != null && value.score().compareTo(max) > 0) {
                throw badBonus("가산점은 항목별 최대 점수를 초과할 수 없습니다.");
            }
        }
        if (!seen.equals(items.keySet())) throw badBonus("모든 가산점 항목을 포함해야 합니다. 미심사는 null로 전송하세요.");
        for (UpdateTeamBonusRequest.Item value : request.scores()) {
            TeamBonusScore score = team.getBonusScores().stream()
                    .filter(v -> v.getBonusItem().getId().equals(value.itemId())).findFirst().orElse(null);
            if (score == null) team.getBonusScores().add(new TeamBonusScore(team, items.get(value.itemId()), value.score()));
            else score.update(value.score());
        }
        entityManager.flush();
        return teamResponse(team);
    }

    private void updateBonusItems(Program program, Boolean enabled, BigDecimal legacyMax, List<BonusItemRequest> requested) {
        boolean nextEnabled = enabled == null ? program.isBonusEnabled() : enabled;
        if (program.isEvaluationStarted() && program.isBonusEnabled() && !nextEnabled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "심사가 시작되어 가산점 사용을 해제할 수 없습니다.");
        }
        // Old clients may omit the item array when updating unrelated program fields.
        if (requested == null) {
            if (nextEnabled && program.getBonusItems().isEmpty()) {
                validateBonusNumber(legacyMax, true);
                program.getBonusItems().add(new BonusItem(program, "가산점", legacyMax, 1));
            }
        } else {
            if (nextEnabled && requested.isEmpty()) throw badBonus("가산점 항목을 최소 1개 등록하세요.");
            Map<Long, BonusItem> existing = program.getBonusItems().stream()
                    .collect(Collectors.toMap(BonusItem::getId, item -> item));
            Set<Long> kept = new HashSet<>();
            for (BonusItemRequest item : requested) {
                if (item == null || item.name() == null || item.name().isBlank() || item.name().trim().length() > 255) {
                    throw badBonus("가산점 항목명을 1~255자로 입력하세요.");
                }
                BonusItem old = item.id() == null ? null : existing.get(item.id());
                if (item.id() != null && (old == null || !kept.add(item.id()))) throw badBonus("잘못되거나 중복된 가산점 항목입니다.");
                // Only migrated unbounded items may retain their null limit.
                validateBonusNumber(item.maxScore(), old != null && old.getMaxScore() == null);
                boolean reduced = old != null && item.maxScore() != null
                        && (old.getMaxScore() == null || item.maxScore().compareTo(old.getMaxScore()) < 0);
                if (reduced && program.isEvaluationStarted()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "심사가 시작되어 최대 가산점을 줄일 수 없습니다.");
                }
                if (reduced) {
                    Long over = entityManager.createQuery("select count(s) from TeamBonusScore s where s.bonusItem.id = :id and s.score > :max", Long.class)
                            .setParameter("id", item.id()).setParameter("max", item.maxScore()).getSingleResult();
                    if (over > 0) throw badBonus("이미 부여한 가산점보다 최대 점수를 낮출 수 없습니다. 팀별 점수를 먼저 조정하세요.");
                }
            }
            if (program.isEvaluationStarted() && !kept.containsAll(existing.keySet())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "심사가 시작되어 가산점 항목을 삭제할 수 없습니다.");
            }
            Set<Long> removed = new HashSet<>(existing.keySet());
            removed.removeAll(kept);
            if (!removed.isEmpty()) {
                // Remove managed score references before deleting their item; DB cascade alone leaves stale JPA objects.
                for (Team team : teamRepository.findByProgramIdOrderByIdAsc(program.getId())) {
                    team.getBonusScores().removeIf(score -> removed.contains(score.getBonusItem().getId()));
                }
                entityManager.flush();
            }
            program.getBonusItems().removeIf(item -> !kept.contains(item.getId()));
            for (int i = 0; i < requested.size(); i++) {
                BonusItemRequest item = requested.get(i);
                if (item.id() == null) program.getBonusItems().add(new BonusItem(program, item.name().trim(), item.maxScore(), i + 1));
                else existing.get(item.id()).update(item.name().trim(), item.maxScore(), i + 1);
            }
            program.getBonusItems().sort(java.util.Comparator.comparingInt(BonusItem::getDisplayOrder));
        }
        BigDecimal max = program.getBonusItems().stream().anyMatch(item -> item.getMaxScore() == null) ? null
                : program.getBonusItems().stream().map(BonusItem::getMaxScore).reduce(BigDecimal.ZERO, BigDecimal::add);
        program.updateBonusSettings(nextEnabled, max);
    }

    private void validateBonusNumber(BigDecimal value, boolean nullable) {
        if (value == null) {
            if (!nullable) throw badBonus("최대 점수를 입력하세요.");
            return;
        }
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 1 || value.compareTo(new BigDecimal("99999999.9")) > 0) {
            throw badBonus("점수는 0 이상 99999999.9 이하, 소수 첫째 자리까지 입력하세요.");
        }
    }

    private ResponseStatusException badBonus(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private TeamResponse teamResponse(Team team) {
        return TeamResponse.from(
                team,
                membersOf(team.getId()),
                attachmentRepository.countByTeamId(team.getId())
        );
    }

    private List<TeamMemberResponse> membersOf(Long teamId) {
        return teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    // 팀에 소속된 팀원 목록을 조회한다.
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> findTeamMembers(Long teamId) {
        return teamMemberRepository.findByTeamIdOrderByIdAsc(teamId).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional
    public TeamMemberResponse addTeamMember(Long teamId, AddTeamMemberRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Team not found."));
        User member = userRepository.findByEmail(request.email())
                .orElseThrow(() -> notFound("Member not found."));

        teamMemberRepository.findByTeamIdAndMemberId(teamId, member.getId()).ifPresent(m -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Member already in team.");
        });

        return TeamMemberResponse.from(teamMemberRepository.save(new TeamMember(team, member)));
    }

    @Transactional
    public void removeTeamMember(Long teamId, Long memberId) {
        TeamMember teamMember = teamMemberRepository.findByTeamIdAndMemberId(teamId, memberId)
                .orElseThrow(() -> notFound("Member not found in team."));
        teamMemberRepository.delete(teamMember);
    }


    // 질문은 displayOrder 기준으로 정렬해 평가 화면에 표시한다.
    @Transactional(readOnly = true)
    public List<CriterionResponse> findCriteria(Long programId) {
        return criterionRepository.findByProgramIdOrderByDisplayOrderAsc(programId).stream()
                .map(CriterionResponse::from)
                .toList();
    }




    private ResponseStatusException criteriaLocked() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "심사가 시작된 프로그램의 배점과 항목 구성은 변경할 수 없습니다.");
    }

    private void validateCriterionMaxScore(BigDecimal maxScore) {
        if (maxScore == null || maxScore.signum() <= 0 || maxScore.compareTo(new BigDecimal("999999.9")) > 0
                || maxScore.stripTrailingZeros().scale() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "배점은 0보다 크고 999999.9 이하인 소수 첫째 자리까지의 숫자여야 합니다.");
        }
    }

    // 프로그램의 지원 양식 항목을 순서대로 조회한다.
    @Transactional(readOnly = true)
    public List<FormFieldResponse> findFormFields(Long programId) {
        return formFieldRepository.findByProgramIdOrderByDisplayOrderAsc(programId).stream()
                .map(FormFieldResponse::from)
                .toList();
    }


    // 답변이 달린 양식 항목은 삭제를 막는다. 지우면 그 항목의 모든 지원자 답변이 cascade로 사라진다.
    private void guardAnsweredFieldsNotDeleted(List<ApplicationFormField> toDelete) {
        if (toDelete.isEmpty()) return;

        Set<Long> answered = Set.copyOf(answerRepository.findAnsweredFieldIds(
                toDelete.stream().map(ApplicationFormField::getId).toList()
        ));
        if (answered.isEmpty()) return;

        String labels = toDelete.stream()
                .filter(field -> answered.contains(field.getId()))
                .map(ApplicationFormField::getLabel)
                .collect(Collectors.joining(", "));
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "이미 지원자가 답변한 항목은 삭제할 수 없습니다: " + labels);
    }

    // 선택지 목록을 줄바꿈으로 이어붙여 저장한다. 빈 값은 제외하고, 남는 게 없으면 null.
    private String joinOptions(List<String> options) {
        if (options == null) return null;
        String joined = options.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    @Transactional
    public void deleteProgram(Long programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> notFound("Program not found."));
        programRepository.delete(program);
    }

    @Transactional
    public void deleteTeam(Long programId, Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Team not found."));
        if (!team.getProgram().getId().equals(programId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team does not belong to this program.");
        }
        teamRepository.delete(team);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.role() == Role.EVALUATOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "심사위원은 POST /api/evaluators로 생성해주세요.");
        }
        if (request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email, password and role are required.");
        }
        boolean sendInvitation = request.invitationRequested();
        if (sendInvitation && request.role() != Role.MANAGER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "안내 메일은 매니저 계정에만 발송할 수 있습니다.");
        }
        String email = request.email().trim().toLowerCase();
        if (sendInvitation) {
            // 계정을 만들기 전에 확인해서, 메일을 보낼 수 없으면 계정도 만들지 않는다.
            invitationService.requireConfigured();
            if (!InvitationSecrets.validEmail(email)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이메일 주소를 입력해주세요.");
            }
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists.");
        });

        User user = userRepository.saveAndFlush(new User(
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                request.name(),
                request.department()
        ));
        // 같은 트랜잭션이라 대기열 저장이 실패하면 계정 생성도 롤백된다.
        if (sendInvitation) invitationService.enqueue(user, request.password());
        return UserResponse.from(user);
    }

    // 이메일로 사용자를 조회한다. 팀원 추가 화면에서 실제 존재하는 사용자인지 확인하는 데 사용한다.
    @Transactional(readOnly = true)
    public UserResponse findUserByEmail(String email) {
        return userRepository.findByEmail(email).filter(user -> !user.isDeleted())
                .map(UserResponse::from)
                .orElseThrow(() -> notFound("User not found."));
    }

    // 최고 관리자 화면에서 매니저 계정 목록을 조회한다.
    // UserResponse.assignmentCount 자리에 해당 매니저가 담당 중인 프로그램 수를 담아 반환한다.
    @Transactional(readOnly = true)
    public List<UserResponse> findManagers() {
        return userRepository.findByRole(Role.MANAGER).stream()
                .map(u -> UserResponse.from(u, programRepository.countByManagerId(u.getId())))
                .toList();
    }

    // 평가 배정 화면에서 선택 가능한 심사위원 목록을 제공한다.
    @Transactional(readOnly = true)
    public List<UserResponse> findEvaluators() {
        return userRepository.findByRoleAndDeletedAtIsNull(Role.EVALUATOR).stream()
                .map(u -> UserResponse.from(u,
                        assignmentRepository.countByEvaluatorIdAndCanEvaluateTrue(u.getId()),
                        assignmentRepository.countByEvaluatorIdAndCanViewResultsTrue(u.getId())))
                .toList();
    }

    // 특정 프로그램에 배정된 심사위원 목록을 조회한다.
    @Transactional(readOnly = true)
    public List<UserResponse> findEvaluatorsByProgram(Long programId) {
        return assignmentRepository.findByProgramIdOrderByIdAsc(programId).stream()
                .map(assignment -> UserResponse.from(assignment.getEvaluator()))
                .toList();
    }

    // 한 프로그램에 한 심사위원의 중복 배정을 막는다.
    @Transactional
    public AssignmentResponse createAssignment(Long programId, CreateAssignmentRequest request) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> notFound("Program not found."));
        User evaluator = userRepository.findLockedById(request.evaluatorId())
                .orElseThrow(() -> notFound("Evaluator not found."));

        if (evaluator.getRole() != Role.EVALUATOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an evaluator.");
        }
        if (evaluator.isDeleted() || evaluator.getStatus() != Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "삭제되었거나 비활성인 심사위원은 새로 배정할 수 없습니다.");
        }
        assignmentRepository.findByProgramIdAndEvaluatorId(programId, evaluator.getId()).ifPresent(assignment -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment already exists.");
        });
        boolean canEvaluate = request.evaluatePermission();
        boolean canViewResults = request.viewResultsPermission();
        if (!canEvaluate && !canViewResults) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "심사 또는 결과 조회 권한을 하나 이상 선택해주세요.");
        }

        EvaluationAssignment assignment = assignmentRepository.save(
                new EvaluationAssignment(program, evaluator, canEvaluate, canViewResults));

        // 배정이 늘어도 프로그램 상태는 건드리지 않는다.
        // 완료된 프로그램을 다시 열려면 매니저가 평가 마감일을 연장해야 한다. (updateProgram)
        return AssignmentResponse.from(assignment);
    }

    // 심사위원 계정 비활성화/활성화.
    @Transactional
    public UserResponse changeEvaluatorStatus(Long evaluatorId, ChangeStatusRequest request) {
        User evaluator = userRepository.findLockedById(evaluatorId)
                .orElseThrow(() -> notFound("Evaluator not found."));
        if (evaluator.getRole() != Role.EVALUATOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is not an evaluator.");
        }
        if (evaluator.isDeleted()) throw new ResponseStatusException(HttpStatus.CONFLICT, "삭제된 심사위원의 상태는 변경할 수 없습니다.");
        evaluator.changeStatus(request.status());
        return UserResponse.from(evaluator);
    }

    // 심사위원 대시보드에서 자신의 평가 작업 목록을 확인할 때 사용한다.
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findAssignmentsByEvaluator(Long evaluatorId) {
        return assignmentRepository.findByEvaluatorIdOrderByIdDesc(evaluatorId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    // 프로그램에 속한 전체 배정 목록을 조회한다. 배정 매트릭스에서 사용.
    @Transactional(readOnly = true)
    public List<AssignmentResponse> findAssignmentsByProgram(Long programId) {
        return assignmentRepository.findByProgramIdOrderByIdAsc(programId).stream()
                .map(AssignmentResponse::from)
                .toList();
    }

    // 배정은 그대로 두고 심사 / 결과 조회 권한만 바꾼다.
    // 임시저장·제출 기록이 있는 배정에서 심사 권한을 빼면 그 기록이 결과에서 조용히 사라지므로 409로 막는다.
    // 기록까지 지우려면 배정을 해제한다. (cancelAssignment)
    @Transactional
    public AssignmentResponse updateAssignmentPermissions(Long assignmentId, UpdateAssignmentPermissionsRequest request) {
        if (request == null || request.canEvaluate() == null || request.canViewResults() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "canEvaluate와 canViewResults를 모두 보내주세요.");
        }
        boolean canEvaluate = request.canEvaluate();
        boolean canViewResults = request.canViewResults();
        if (!canEvaluate && !canViewResults) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "심사 또는 결과 조회 권한을 하나 이상 선택해주세요. 배정을 없애려면 배정을 해제하세요.");
        }
        EvaluationAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));
        entityManager.refresh(assignment.getProgram(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        try {
            entityManager.refresh(assignment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        } catch (jakarta.persistence.EntityNotFoundException error) {
            throw notFound("Assignment not found.");
        }
        if (assignment.isCanEvaluate() && !canEvaluate) {
            Long records = entityManager.createQuery(
                            "select count(s) from EvaluationSubmission s where s.assignment.id = :assignmentId", Long.class)
                    .setParameter("assignmentId", assignmentId)
                    .getSingleResult();
            if (records > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "이미 채점 기록이 있는 심사위원은 심사 권한만 뺄 수 없습니다. 기록까지 지우려면 배정을 해제하세요.");
            }
        }
        assignment.changePermissions(canEvaluate, canViewResults);
        return AssignmentResponse.from(assignment);
    }

    // 제출 여부와 무관하게 배정과 평가 기록을 삭제하고 집계에서 제외한다.
    @Transactional
    public void cancelAssignment(Long assignmentId) {
        EvaluationAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> notFound("Assignment not found."));

        entityManager.refresh(assignment.getProgram(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        try {
            entityManager.refresh(assignment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        } catch (jakarta.persistence.EntityNotFoundException error) {
            throw notFound("Assignment not found.");
        }
        assignmentRepository.delete(assignment);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
