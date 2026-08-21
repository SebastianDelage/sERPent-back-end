package com.empresa.serpent.users.web.mapper;

import com.empresa.serpent.shared.mapper.MapStructConfig;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.web.dto.request.CreateUserRequest;
import com.empresa.serpent.users.web.dto.request.UpdateUserRequest;
import com.empresa.serpent.users.web.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    // Warehouse assignment is resolved from ids by UserService, not copied from the request:
    // the DTO carries warehouseIds, the entity holds WarehouseEntity references.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "warehouses", ignore = true)
    // Role is applied by UserService, which enforces the rules around it.
    @Mapping(target = "role", ignore = true)
    UserEntity toEntity(CreateUserRequest request);

    UserResponse toResponse(UserEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "warehouses", ignore = true)
    @Mapping(target = "role", ignore = true)
    void updateEntityFromRequest(UpdateUserRequest request, @MappingTarget UserEntity entity);
}