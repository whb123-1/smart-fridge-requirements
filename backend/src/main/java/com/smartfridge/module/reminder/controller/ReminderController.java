package com.smartfridge.module.reminder.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.reminder.entity.Reminder;
import com.smartfridge.module.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    public Result<List<Reminder>> list(@RequestParam(required = false) String status) {
        return Result.ok(reminderService.list(status));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(reminderService.unreadCount());
    }

    @PatchMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        reminderService.markRead(id);
        return Result.ok();
    }

    @PostMapping("/{id}/dismiss")
    public Result<Void> dismiss(@PathVariable Long id) {
        reminderService.dismiss(id);
        return Result.ok();
    }
}
