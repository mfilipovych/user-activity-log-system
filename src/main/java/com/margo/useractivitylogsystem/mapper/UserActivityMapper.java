package com.margo.useractivitylogsystem.mapper;

import com.margo.useractivitylogsystem.entity.UserActivity;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface UserActivityMapper {

    @Mapping(target = ".", source = "key")
    ActivityResponse toResponse(UserActivity entity);

    List<ActivityResponse> toResponse(List<UserActivity> entities);
}
