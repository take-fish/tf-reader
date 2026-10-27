package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.reader.model.ReadingHeartbeatRequest;
import com.fishmatrix.reader.model.UserStats;
import com.fishmatrix.reader.service.ExperienceService;
import com.fishmatrix.reader.service.LibraryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户的个人数据（经验值、等级、阅读统计）。 */
@RestController
@RequestMapping("/api/v1/reader")
public class MeController {

    private final ExperienceService experienceService;
    private final LibraryService libraryService;

    public MeController(ExperienceService experienceService, LibraryService libraryService) {
        this.experienceService = experienceService;
        this.libraryService = libraryService;
    }

    @GetMapping("/me/stats")
    public UserStats stats() {
        return experienceService.stats(UserContext.require().id());
    }

    /**
     * 阅读心跳。前端在阅读器可见时每分钟调用一次；服务端用两次心跳的时间差累计时长，
     * 并要求调用者对该书有阅读权限，避免对无权书刷经验。
     */
    @PostMapping("/me/reading-heartbeat")
    public UserStats heartbeat(@Valid @RequestBody ReadingHeartbeatRequest request) {
        CurrentUser viewer = UserContext.require();
        libraryService.requireReadable(request.bookId(), viewer);
        return experienceService.heartbeat(viewer.id(), request.bookId());
    }
}
