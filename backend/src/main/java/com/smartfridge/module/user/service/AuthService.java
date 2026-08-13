package com.smartfridge.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartfridge.common.BusinessException;
import com.smartfridge.common.UserContext;
import com.smartfridge.module.user.entity.SysUser;
import com.smartfridge.module.user.mapper.SysUserMapper;
import com.smartfridge.security.JwtUtil;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserVO register(RegisterReq req) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已被注册");
        }
        SysUser user = new SysUser();
        user.setUsername(req.username());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setNickname(StringUtils.hasText(req.nickname()) ? req.nickname() : req.username());
        user.setEmail(req.email());
        userMapper.insert(user);
        return toVO(user);
    }

    public LoginResp login(LoginReq req) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username()));
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResp(token, toVO(user));
    }

    public UserVO me() {
        SysUser user = userMapper.selectById(UserContext.get());
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return toVO(user);
    }

    private UserVO toVO(SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getAvatar());
    }

    public record RegisterReq(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 20, message = "用户名长度为 3-20 个字符")
            String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 30, message = "密码长度为 6-30 个字符")
            String password,
            String nickname,
            @Email(message = "邮箱格式不正确")
            String email) {
    }

    public record LoginReq(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password) {
    }

    public record LoginResp(String token, UserVO user) {
    }

    public record UserVO(Long id, String username, String nickname, String email, String avatar) {
    }
}
