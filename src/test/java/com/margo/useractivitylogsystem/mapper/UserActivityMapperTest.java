package com.margo.useractivitylogsystem.mapper;

import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static com.margo.useractivitylogsystem.data.TestData.activityResponse;
import static com.margo.useractivitylogsystem.data.TestData.userActivity;
import static org.junit.jupiter.api.Assertions.*;

class UserActivityMapperTest {
    private final UserActivityMapper userActivityMapper = Mappers.getMapper(UserActivityMapper.class);

    private static final UserActivity ENTITY = userActivity();
    private static final ActivityResponse RESPONSE = activityResponse();

    @Test
    void toResponse_whenSingleEntity_shouldMapToResponseFromEntity() {
        // when
        ActivityResponse activityResponse = userActivityMapper.toResponse(ENTITY);

        // then
        assertNotNull(activityResponse);
        assertEquals(RESPONSE, activityResponse);
    }

    @Test
    void toResponse_whenNullableSingleEntity_shouldMapToNullableResponse() {
        // given
        UserActivity entity = new UserActivity(null, null, null);
        // when
        ActivityResponse activityResponse = userActivityMapper.toResponse(entity);

        // then
        assertNotNull(activityResponse);
        assertNull(activityResponse.userId());
        assertNull(activityResponse.activityId());
        assertNull(activityResponse.activityTimestamp());
        assertNull(activityResponse.details());
        assertNull(activityResponse.activityType());
    }

    @Test
    void toResponse_whenNullEntity_shouldReturnNull() {
        // when
        ActivityResponse activityResponse = userActivityMapper.toResponse((UserActivity) null);

        // then
        assertNull(activityResponse);
    }

    @Test
    void toResponse_whenMultipleEntities_shouldMapToResponseList() {
        // given
        List<UserActivity> entities = List.of(ENTITY);

        // when
        List<ActivityResponse> activityResponse = userActivityMapper.toResponse(entities);

        // then
        assertNotNull(activityResponse);
        assertEquals(entities.size(), activityResponse.size());
        assertEquals(RESPONSE, activityResponse.getFirst());
    }

    @Test
    void toResponse_whenEmptyList_shouldReturnEmptyList() {
        // when
        List<ActivityResponse> activityResponse = userActivityMapper.toResponse(List.of());

        // then
        assertNotNull(activityResponse);
        assertTrue(activityResponse.isEmpty());
    }

    @Test
    void toResponse_whenNullEntities_shouldReturnNull() {
        // when
        List<ActivityResponse> activityResponse = userActivityMapper.toResponse((List<UserActivity>) null);

        // then
        assertNull(activityResponse);
    }
}