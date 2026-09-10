package com.aether.evaluation.service;
import java.util.Map;
public interface EvaluationGateService { Map<String,Object> check(String targetType, String targetId, String fingerprint); }
