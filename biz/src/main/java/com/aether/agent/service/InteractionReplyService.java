package com.aether.agent.service;

import com.aether.agent.entity.AgentMessage;
import com.aether.exception.ServerException;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 统一校验交互卡片回复，并生成模型上下文文本和前端可展示的已回答配置。 */
@Service
public class InteractionReplyService {
    public String renderAnswerContent(AgentMessage question, Map<String, Object> answer) {
        if (answer == null) throw new ServerException(400, "回复内容不能为空");
        JSONObject config = JSONObject.parseObject(question.getQuestionConfig());
        String type = config.getString("type");
        if ("group".equals(type)) return renderGroupAnswer(config, answer);
        if ("choice".equals(type)) return renderChoiceAnswer(config, answer);
        if ("confirm".equals(type)) return "用户选择：" + (confirmed(answer) ? "确认" : "取消");
        throw new ServerException(400, "未知提问类型");
    }

    /** 将原始回答规范化保存，前端无需再次根据选项表反查显示文案。 */
    public String buildAnsweredQuestionConfig(AgentMessage question, Map<String, Object> answer, Long answeredAt) {
        JSONObject config = JSONObject.parseObject(question.getQuestionConfig());
        JSONObject answerConfig = new JSONObject(); answerConfig.put("answeredAt", answeredAt);
        if ("group".equals(config.getString("type"))) {
            Object answersObj = answer.get("answers");
            if (!(answersObj instanceof Map)) throw new ServerException(400, "group回复必须包含answers对象");
            Map<?, ?> answers = (Map<?, ?>) answersObj; JSONArray questions = config.getJSONArray("questions");
            JSONObject normalized = new JSONObject();
            for (int i = 0; i < questions.size(); i++) {
                JSONObject item = questions.getJSONObject(i); Object itemAnswer = answers.get(item.getString("id"));
                if (!(itemAnswer instanceof Map)) throw new ServerException(400, "缺少问题回复: " + item.getString("id"));
                JSONObject display = buildDisplayAnswer(item, (Map<String, Object>) itemAnswer);
                item.put("answer", display); normalized.put(item.getString("id"), display);
            }
            answerConfig.put("answers", normalized); config.put("answer", answerConfig); return config.toJSONString();
        }
        JSONObject display = buildDisplayAnswer(config, answer); config.put("answer", display);
        answerConfig.put("value", display); config.put("answered", answerConfig); return config.toJSONString();
    }

    private String renderGroupAnswer(JSONObject config, Map<String, Object> answer) {
        Object raw = answer.get("answers"); if (!(raw instanceof Map)) throw new ServerException(400, "group回复必须包含answers对象");
        JSONArray questions = config.getJSONArray("questions"); if (questions == null || questions.isEmpty()) throw new ServerException(400, "提问配置不合法");
        Map<?, ?> answers = (Map<?, ?>) raw; List<String> lines = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) { JSONObject item = questions.getJSONObject(i); Object itemAnswer = answers.get(item.getString("id"));
            if (!(itemAnswer instanceof Map)) throw new ServerException(400, "缺少问题回复: " + item.getString("id"));
            lines.add(item.getString("question") + "：" + renderSingle(item, (Map<String, Object>) itemAnswer)); }
        return "用户回复：" + StringUtils.join(lines, "；");
    }
    private String renderSingle(JSONObject config, Map<String, Object> answer) { return "choice".equals(config.getString("type")) ? renderChoiceAnswer(config, answer).replaceFirst("^用户选择：", "") : (confirmed(answer) ? "确认" : "取消"); }
    private String renderChoiceAnswer(JSONObject config, Map<String, Object> answer) {
        List<String> selected = selected(answer.get("selected")); if (selected.isEmpty()) throw new ServerException(400, "choice回复必须包含selected");
        if (!Boolean.TRUE.equals(config.getBoolean("multiple")) && selected.size() > 1) throw new ServerException(400, "该提问只能选择一个选项");
        List<String> labels = new ArrayList<>(); JSONArray options = config.getJSONArray("options");
        for (String value : selected) { JSONObject found = findOption(options, value); if (found == null) { if (!Boolean.TRUE.equals(config.getBoolean("allowCustomInput")) || StringUtils.isBlank(value) || value.length() > 200) throw new ServerException(400, "回复选项不在允许范围内"); labels.add(value); } else labels.add(found.getString("label") + "(" + found.getString("value") + ")"); }
        return "用户选择：" + StringUtils.join(labels, ", ");
    }
    private JSONObject buildDisplayAnswer(JSONObject config, Map<String, Object> answer) { if ("confirm".equals(config.getString("type"))) { JSONObject result = new JSONObject(); result.put("confirmed", answer.get("confirmed")); result.put("label", confirmed(answer) ? StringUtils.defaultIfBlank(config.getString("confirmText"), "确认") : StringUtils.defaultIfBlank(config.getString("cancelText"), "取消")); return result; }
        List<String> values = selected(answer.get("selected")); JSONArray choices = new JSONArray(); for (String value : values) { JSONObject found = findOption(config.getJSONArray("options"), value); choices.add(found == null ? new JSONObject().fluentPut("label", value).fluentPut("value", value).fluentPut("custom", true) : new JSONObject().fluentPut("id", found.getString("id")).fluentPut("label", found.getString("label")).fluentPut("value", found.getString("value"))); } JSONObject result = new JSONObject(); result.put("selected", Boolean.TRUE.equals(config.getBoolean("multiple")) ? values : (values.isEmpty() ? null : values.get(0))); result.put("selectedOptions", choices); return result; }
    private boolean confirmed(Map<String, Object> answer) { Object value = answer.get("confirmed"); if (!(value instanceof Boolean)) throw new ServerException(400, "confirm回复必须包含confirmed布尔值"); return Boolean.TRUE.equals(value); }
    private JSONObject findOption(JSONArray options, String value) { if (options != null) for (int i = 0; i < options.size(); i++) { JSONObject option = options.getJSONObject(i); if (value.equals(option.getString("id")) || value.equals(option.getString("value"))) return option; } return null; }
    private List<String> selected(Object value) { List<String> result = new ArrayList<>(); if (value instanceof List) { for (Object item : (List<?>) value) { if (item != null) result.add(item.toString()); } } else if (value != null) { result.add(value.toString()); } return result; }
}
