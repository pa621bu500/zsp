package com.pabu5h.zsp.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDto {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("id")
    private Long id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("job_type_name")
    private String jobTypeName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("job_task_type")
    private String jobTaskType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("submit_timestamp")
    private LocalDateTime submitTimestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("scheduled_timestamp")
    private LocalDateTime scheduledTimestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("user_id")
    private Long userId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("job_type_id")
    private Long jobTypeId;

//    @JsonInclude(JsonInclude.Include.NON_NULL)
//    private JobStatusEnum status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> request;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("op_list")
    private List<Map<String, Object>> opList;
}