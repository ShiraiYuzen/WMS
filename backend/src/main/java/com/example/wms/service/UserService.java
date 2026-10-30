package com.example.wms.service;

import com.example.wms.dao.UserDao;
import com.example.wms.dto.PageResult;
import com.example.wms.entity.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public PageResult<User> list(String keyword, String role, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 200);
        long total = userDao.count(keyword, role);
        List<User> list = userDao.findPage(keyword, role, (p - 1) * s, s);
        list.forEach(u -> u.setPassword(null));
        return new PageResult<>(list, total);
    }

    public List<User> listByRole(String role) {
        return userDao.findByRole(role);
    }

    public List<User> listAll() {
        List<User> list = userDao.findPage(null, null, 0, 500);
        list.forEach(u -> u.setPassword(null));
        return list;
    }

    public User add(User u) {
        if (u.getUsername() == null || u.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (userDao.findByUsername(u.getUsername().trim()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (u.getPassword() == null || u.getPassword().isBlank()) {
            u.setPassword("123456");
        }
        if (u.getNickname() == null || u.getNickname().isBlank()) {
            u.setNickname(u.getUsername());
        }
        long id = userDao.insert(u);
        return userDao.findById(id);
    }

    public void update(User u) {
        if (u.getId() == null) {
            throw new IllegalArgumentException("缺少用户 ID");
        }
        userDao.update(u);
    }

    public void resetPassword(Long id, String password) {
        if (id == null) {
            throw new IllegalArgumentException("缺少用户 ID");
        }
        userDao.updatePassword(id, password == null || password.isBlank() ? "123456" : password);
    }

    public void delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("缺少用户 ID");
        }
        userDao.delete(id);
    }
}
