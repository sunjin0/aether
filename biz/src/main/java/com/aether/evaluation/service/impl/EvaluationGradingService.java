package com.aether.evaluation.service.impl;

import com.aether.evaluation.entity.EvaluationCaseVersion;
import com.aether.evaluation.entity.EvaluationEvaluator;
import com.aether.evaluation.entity.EvaluationEvaluatorVersion;
import com.aether.evaluation.entity.EvaluationResult;
import com.aether.evaluation.entity.EvaluationScore;
import com.aether.evaluation.service.EvaluationEvaluatorService;
import com.aether.evaluation.service.EvaluationEvaluatorVersionService;
import com.aether.evaluation.service.EvaluationResultService;
import com.aether.evaluation.service.EvaluationScoreService;
import com.aether.evaluation.service.LlmEvaluationGrader;
import com.aether.evaluation.service.RuleEvaluationGrader;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Applies immutable evaluator bindings and hard assertions to one evaluation result. */
@Service
public class EvaluationGradingService {
    private final EvaluationScoreService scoreService;
    private final EvaluationResultService resultService;
    private final EvaluationEvaluatorVersionService evaluatorVersionService;
    private final EvaluationEvaluatorService evaluatorService;
    private final LlmEvaluationGrader llmGrader;

    public EvaluationGradingService(EvaluationScoreService scoreService, EvaluationResultService resultService,
                                    EvaluationEvaluatorVersionService evaluatorVersionService,
                                    EvaluationEvaluatorService evaluatorService, LlmEvaluationGrader llmGrader) {
        this.scoreService = scoreService;
        this.resultService = resultService;
        this.evaluatorVersionService = evaluatorVersionService;
        this.evaluatorService = evaluatorService;
        this.llmGrader = llmGrader;
    }

    @Transactional(rollbackFor = Exception.class)
    public void grade(EvaluationResult result, EvaluationCaseVersion evaluationCase) {
        if (result == null) return;
        String bindingsJson = evaluationCase == null ? null : evaluationCase.getEvaluatorBindingsJson();
        if (bindingsJson == null || bindingsJson.trim().isEmpty()) {
            fail(result, "NO_BINDINGS", "");
            return;
        }
        JSONArray bindings;
        try {
            bindings = JSONArray.parseArray(bindingsJson);
        } catch (RuntimeException ex) {
            fail(result, "INVALID_BINDINGS", "");
            return;
        }
        if (bindings == null || bindings.isEmpty()) {
            fail(result, "NO_BINDINGS", "");
            return;
        }

        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        int round = result.getActiveGradingRound() == null ? 0 : result.getActiveGradingRound();
        for (int i = 0; i < bindings.size(); i++) {
            JSONObject binding = bindings.getJSONObject(i);
            if (binding == null) continue;
            EvaluationScore score = newScore(result, binding, round, i);
            EvaluationEvaluatorVersion version = evaluatorVersionService.getById(binding.getString("evaluatorVersionId"));
            EvaluationEvaluator evaluator = version == null ? null : evaluatorService.getById(version.getEvaluatorId());
            if (version == null || evaluator == null) {
                score.setStatus("ERROR");
                score.setReason("EVALUATOR_VERSION_NOT_FOUND");
                scoreService.save(score);
                fail(result, "GRADER_FAILED", score.getReason());
                return;
            }
            gradeBinding(score, binding, version, evaluator, evaluationCase, result.getOutputJson());
            scoreService.save(score);
            if (score.getScore() == null) {
                fail(result, "GRADER_FAILED", score.getReason());
                return;
            }
            weighted = weighted.add(score.getScore().multiply(score.getWeight()));
            weights = weights.add(score.getWeight());
        }

        if (weights.signum() == 0) {
            fail(result, "INVALID_BINDINGS", "");
            return;
        }
        AssertionResult assertions = evaluateAssertions(result, evaluationCase == null ? null : evaluationCase.getAssertionsJson());
        if (!assertions.valid) {
            fail(result, "GRADER_FAILED", assertions.reason);
            return;
        }
        result.setScore(weighted.divide(weights, 2, RoundingMode.HALF_UP));
        int passThreshold = evaluationCase != null && evaluationCase.getPassThreshold() != null
                ? evaluationCase.getPassThreshold() : 80;
        boolean scorePassed = result.getScore().compareTo(BigDecimal.valueOf(passThreshold)) >= 0;
        result.setGradingStatus("SUCCEEDED");
        result.setQualityStatus(scorePassed && assertions.passed ? "PASSED" : "FAILED");
        result.setErrorCode(assertions.passed ? null : "ASSERTION_FAILED");
        result.setErrorMessage(assertions.passed ? null : assertions.reason);
        resultService.updateById(result);
    }

    private EvaluationScore newScore(EvaluationResult result, JSONObject binding, int round, int index) {
        EvaluationScore score = new EvaluationScore();
        score.setResultId(result.getId());
        score.setGradingRound(round);
        score.setBindingKey(binding.getString("bindingKey") == null ? "rule-" + index : binding.getString("bindingKey"));
        score.setEvaluatorVersionId(binding.getString("evaluatorVersionId"));
        BigDecimal weight = binding.getBigDecimal("weight");
        score.setWeight(weight == null ? BigDecimal.ONE : weight);
        return score;
    }

    private void gradeBinding(EvaluationScore score, JSONObject binding, EvaluationEvaluatorVersion version,
                              EvaluationEvaluator evaluator, EvaluationCaseVersion evaluationCase, String actual) {
        if ("RULE".equals(evaluator.getKind())) {
            JSONObject config = JSONObject.parseObject(version.getConfigJson());
            String expected = binding.containsKey("expected") ? binding.getString("expected")
                    : config == null ? null : config.getString("expected");
            RuleEvaluationGrader.Result graded = RuleEvaluationGrader.grade(actual,
                    config == null ? null : config.getString("operator"), expected,
                    config == null ? null : config.getString("jsonPath"),
                    config == null ? null : config.getString("schema"));
            score.setStatus(graded.getStatus());
            score.setScore(graded.getScore() == null ? null : BigDecimal.valueOf(graded.getScore()));
            score.setReason(graded.getReason());
            return;
        }
        if ("LLM".equals(evaluator.getKind())) {
            LlmEvaluationGrader.Result graded = llmGrader.grade(JSONObject.parseObject(version.getConfigJson()), evaluationCase, actual);
            score.setStatus(graded.getStatus());
            score.setScore(graded.getScore());
            score.setReason(graded.getReason());
            score.setEvidenceJson(graded.getEvidenceJson());
            score.setUsageJson(graded.getUsageJson());
            score.setAttemptHistoryJson(graded.getAttemptsJson());
            return;
        }
        score.setStatus("ERROR");
        score.setReason("EVALUATOR_KIND_INVALID");
    }

    private AssertionResult evaluateAssertions(EvaluationResult result, String assertionsJson) {
        if (assertionsJson == null || assertionsJson.trim().isEmpty()) return AssertionResult.pass();
        JSONArray assertions;
        JSONObject evidence;
        try {
            assertions = JSONArray.parseArray(assertionsJson);
            evidence = JSONObject.parseObject(result.getEvidenceJson());
        } catch (RuntimeException ex) {
            return AssertionResult.invalid("ASSERTION_INVALID");
        }
        if (assertions == null) return AssertionResult.invalid("ASSERTION_INVALID");
        JSONArray outcomes = new JSONArray();
        for (int index = 0; index < assertions.size(); index++) {
            JSONObject assertion = assertions.getJSONObject(index);
            if (assertion == null) return AssertionResult.invalid("ASSERTION_INVALID");
            String type = assertion.getString("type");
            String target = assertion.getString("target");
            if (target == null || target.isBlank()) return AssertionResult.invalid("ASSERTION_INVALID");
            Boolean matched = matchAssertion(evidence, type, target);
            if (matched == null) return AssertionResult.invalid("ASSERTION_EVIDENCE_MISSING");
            JSONObject outcome = new JSONObject();
            outcome.put("type", type);
            outcome.put("target", target);
            outcome.put("status", matched ? "PASS" : "FAIL");
            outcomes.add(outcome);
            if (!matched) {
                appendAssertionEvidence(result, evidence, outcomes);
                return AssertionResult.failed("ASSERTION_FAILED");
            }
        }
        appendAssertionEvidence(result, evidence, outcomes);
        return AssertionResult.pass();
    }

    private Boolean matchAssertion(JSONObject evidence, String type, String target) {
        if ("TOOL_CALLED".equals(type) || "TOOL_NOT_CALLED".equals(type)
                || "TOOL_SUCCEEDED".equals(type) || "TOOL_NOT_SUCCEEDED".equals(type)) {
            JSONArray tools = evidence == null ? null : evidence.getJSONArray("tools");
            if (tools == null) return null;
            boolean present = false;
            for (Object item : tools) {
                if (!(item instanceof JSONObject)) continue;
                JSONObject tool = (JSONObject) item;
                if (target.equals(tool.getString("toolId")) || target.equals(tool.getString("wireName"))) {
                    present = true;
                    break;
                }
            }
            if ("TOOL_CALLED".equals(type)) return present;
            if ("TOOL_NOT_CALLED".equals(type)) return !present;
            boolean succeeded = false;
            for (Object item : tools) {
                if (!(item instanceof JSONObject)) continue;
                JSONObject tool = (JSONObject) item;
                if ((target.equals(tool.getString("toolId")) || target.equals(tool.getString("wireName")))
                        && "SUCCEEDED".equals(tool.getString("status"))) {
                    succeeded = true;
                    break;
                }
            }
            return "TOOL_SUCCEEDED".equals(type) ? succeeded : !succeeded;
        }
        if ("NODE_VISITED".equals(type) || "NODE_NOT_VISITED".equals(type)
                || "NODE_COMPLETED".equals(type) || "NODE_NOT_COMPLETED".equals(type)) {
            JSONArray nodes = evidence == null ? null : evidence.getJSONArray("nodes");
            if (nodes == null) return null;
            boolean present = false;
            for (Object item : nodes) {
                if (!(item instanceof JSONObject)) continue;
                JSONObject node = (JSONObject) item;
                if (target.equals(node.getString("nodeId"))) {
                    present = true;
                    break;
                }
            }
            if ("NODE_VISITED".equals(type)) return present;
            if ("NODE_NOT_VISITED".equals(type)) return !present;
            boolean completed = false;
            for (Object item : nodes) {
                if (!(item instanceof JSONObject)) continue;
                JSONObject node = (JSONObject) item;
                if (target.equals(node.getString("nodeId")) && "COMPLETED".equals(node.getString("status"))) {
                    completed = true;
                    break;
                }
            }
            return "NODE_COMPLETED".equals(type) ? completed : !completed;
        }
        return null;
    }

    private void appendAssertionEvidence(EvaluationResult result, JSONObject evidence, JSONArray outcomes) {
        JSONObject value = evidence == null ? new JSONObject() : evidence;
        value.put("assertions", outcomes);
        result.setEvidenceJson(value.toJSONString());
    }

    private void fail(EvaluationResult result, String code, String message) {
        result.setGradingStatus("FAILED");
        result.setQualityStatus("INCOMPLETE");
        result.setErrorCode(code);
        result.setErrorMessage(message);
        resultService.updateById(result);
    }

    private static final class AssertionResult {
        private final boolean valid;
        private final boolean passed;
        private final String reason;

        private AssertionResult(boolean valid, boolean passed, String reason) {
            this.valid = valid;
            this.passed = passed;
            this.reason = reason;
        }

        static AssertionResult pass() { return new AssertionResult(true, true, null); }
        static AssertionResult failed(String reason) { return new AssertionResult(true, false, reason); }
        static AssertionResult invalid(String reason) { return new AssertionResult(false, false, reason); }
    }
}
