package com.margo.useractivitylogsystem.controller;

import tools.jackson.databind.ObjectMapper;
import com.margo.useractivitylogsystem.model.ActivityRequest;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import com.margo.useractivitylogsystem.service.UserActivityService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.margo.useractivitylogsystem.data.TestData.ACTIVITY_TYPE;
import static com.margo.useractivitylogsystem.data.TestData.DETAILS;
import static com.margo.useractivitylogsystem.data.TestData.FROM;
import static com.margo.useractivitylogsystem.data.TestData.TO;
import static com.margo.useractivitylogsystem.data.TestData.USER_ID;
import static com.margo.useractivitylogsystem.data.TestData.activityResponse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserActivityController.class)
class UserActivityControllerTest {

    private static final String BASE = "/user_activities";
    private static final String URL = BASE + "/{userId}";

    private static final String LIMIT_PARAM = "limit";
    private static final String FROM_PARAM = "from";
    private static final String TO_PARAM = "to";

    private static final String VALID_BODY = """
            {"activityType":"LOGIN","details":"details","ttlInSeconds":3600}""";

    private static final ActivityResponse RESPONSE = activityResponse();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserActivityService userActivityService;

    @ParameterizedTest
    @MethodSource("validRequests")
    void saveActivity_withValidBody_returns201AndMappedResponse(ActivityRequest request) throws Exception {
        // given
        ActivityResponse response = RESPONSE.toBuilder().details(request.details())
            .activityType(request.activityType()).build();

        when(userActivityService.saveActivity(USER_ID, request)).thenReturn(response);

        // when & then
        mockMvc.perform(post(URL, USER_ID).contentType(APPLICATION_JSON).content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.details").value(response.details()))
                .andExpect(jsonPath("$.activityId").value(response.activityId().toString()))
                .andExpect(jsonPath("$.activityType").value(response.activityType()))
                .andExpect(jsonPath("$.activityTimestamp").value(response.activityTimestamp().toString()));
        verify(userActivityService).saveActivity(USER_ID, request);
    }

    private static Stream<ActivityRequest> validRequests() {
        return Stream.of(
                new ActivityRequest(ACTIVITY_TYPE, DETAILS, 3600),
                new ActivityRequest(ACTIVITY_TYPE, null, null),
                new ActivityRequest("a".repeat(50), null, 60),
                new ActivityRequest(ACTIVITY_TYPE, null, 31_536_000));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void saveActivity_withInvalidBody_returns400(ActivityRequest body) throws Exception {
        // when & then
        mockMvc.perform(post(URL, USER_ID).contentType(APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userActivityService);
    }

    private static Stream<ActivityRequest> invalidRequests() {
        return Stream.of(
                new ActivityRequest(null, "x", null),
                new ActivityRequest("   ", null, null),
                new ActivityRequest("a".repeat(51), null, null),
                new ActivityRequest(ACTIVITY_TYPE, "d".repeat(2001), null),
                new ActivityRequest(ACTIVITY_TYPE, null, 59),
                new ActivityRequest(ACTIVITY_TYPE, null, 31_536_001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{not json"})
    void saveActivity_withMalformedOrMissingBody_returns400(String rawBody) throws Exception {
        // when / then
        mockMvc.perform(post(URL, USER_ID).contentType(APPLICATION_JSON).content(rawBody))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userActivityService);
    }

    @ParameterizedTest
    @MethodSource("validQueries")
    void getActivities_withValidQuery_passesParametersToServiceAndReturns200(
            Integer limit, Instant from, Instant to) throws Exception {
        // given
        when(userActivityService.getUserActivities(USER_ID, limit, from, to)).thenReturn(List.of(RESPONSE));

        // when / then
        mockMvc.perform(getActivities(limit, from, to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        verify(userActivityService).getUserActivities(USER_ID, limit, from, to);
    }

    private static Stream<Arguments> validQueries() {
        return Stream.of(
                Arguments.of(null, null, null),
                Arguments.of(1, null, null),
                Arguments.of(100, null, null),
                Arguments.of(null, FROM, TO),
                Arguments.of(10, FROM, TO));
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void getActivities_withInvalidQuery_returns400AndSkipsService(Map<String, String> params) throws Exception {
        // when & then
        mockMvc.perform(getActivities(params)).andExpect(status().isBadRequest());
        verifyNoInteractions(userActivityService);
    }

    private static Stream<Map<String, String>> invalidQueries() {
        return Stream.of(
                Map.of(LIMIT_PARAM, "0"),
                Map.of(LIMIT_PARAM, "101"),
                Map.of(LIMIT_PARAM, "abc"),
                Map.of(FROM_PARAM, "not-a-date", TO_PARAM, TO.toString()));
    }

    @ParameterizedTest
    @MethodSource("requestsWithInvalidUserId")
    void withNonUuidUserId_returns400AndSkipsService(MockHttpServletRequestBuilder request) throws Exception {
        // when & then
        mockMvc.perform(request).andExpect(status().isBadRequest());
        verifyNoInteractions(userActivityService);
    }

    private static Stream<MockHttpServletRequestBuilder> requestsWithInvalidUserId() {
        return Stream.of(
                get(BASE + "/not-a-uuid"),
                post(BASE + "/not-a-uuid").contentType(APPLICATION_JSON).content(VALID_BODY));
    }

    private String json(ActivityRequest body) {
        return objectMapper.writeValueAsString(body);
    }

    private MockHttpServletRequestBuilder getActivities(Integer limit, Instant from, Instant to) {
        Map<String, String> params = new HashMap<>();
        if (limit != null) {
            params.put(LIMIT_PARAM, limit.toString());
        }

        if (from != null) {
            params.put(FROM_PARAM, from.toString());
        }

        if (to != null) {
            params.put(TO_PARAM, to.toString());
        }
        return getActivities(params);
    }

    private MockHttpServletRequestBuilder getActivities(Map<String, String> params) {
        MockHttpServletRequestBuilder request = get(URL, USER_ID);
        params.forEach(request::param);
        return request;
    }
}