package com.citylife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citylife.dto.LoginFormDTO;
import com.citylife.dto.Result;
import com.citylife.entity.User;
import jakarta.servlet.http.HttpSession;


/**
 * @description: 用户 接口
 **/
public interface IUserService extends IService<User> {

    Result<String> sendCode(String phone, HttpSession session);

    Result<String> login(LoginFormDTO loginForm, HttpSession session);

    Result<Void> sign();

    Result<Integer> signCount();

}
