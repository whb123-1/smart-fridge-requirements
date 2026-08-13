package com.smartfridge.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.user.entity.UserPreference;
import com.smartfridge.module.user.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserPreferenceMapper preferenceMapper;

    public UserPreference get() {
        return getOrCreate(UserContext.get());
    }

    public UserPreference update(PreferenceReq req) {
        UserPreference p = getOrCreate(UserContext.get());
        p.setTaste(req.taste());
        p.setAllergy(req.allergy());
        p.setAvoidFoods(req.avoidFoods());
        p.setDietGoal(req.dietGoal());
        p.setTargetCalories(req.targetCalories());
        preferenceMapper.updateById(p);
        return p;
    }

    private UserPreference getOrCreate(Long userId) {
        UserPreference p = preferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>().eq(UserPreference::getUserId, userId));
        if (p == null) {
            p = new UserPreference();
            p.setUserId(userId);
            p.setDietGoal("均衡");
            preferenceMapper.insert(p);
        }
        return p;
    }

    public record PreferenceReq(
            String taste,
            String allergy,
            String avoidFoods,
            String dietGoal,
            Integer targetCalories) {
    }
}
