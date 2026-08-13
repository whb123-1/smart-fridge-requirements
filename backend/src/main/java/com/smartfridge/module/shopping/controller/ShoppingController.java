package com.smartfridge.module.shopping.controller;

import com.smartfridge.common.Result;
import com.smartfridge.module.shopping.entity.ShoppingListItem;
import com.smartfridge.module.shopping.service.ShoppingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shopping")
@RequiredArgsConstructor
public class ShoppingController {

    private final ShoppingService shoppingService;

    @GetMapping("/lists")
    public Result<List<ShoppingService.ListVO>> lists() {
        return Result.ok(shoppingService.lists());
    }

    @PostMapping("/lists")
    public Result<?> create(@RequestParam(required = false) String name) {
        return Result.ok(shoppingService.create(name));
    }

    @PostMapping("/lists/auto")
    public Result<ShoppingService.ListVO> auto() {
        return Result.ok(shoppingService.auto());
    }

    @DeleteMapping("/lists/{listId}")
    public Result<Void> deleteList(@PathVariable Long listId) {
        shoppingService.deleteList(listId);
        return Result.ok();
    }

    @PostMapping("/lists/{listId}/items")
    public Result<ShoppingListItem> addItem(@PathVariable Long listId,
                                            @RequestBody ShoppingService.ItemReq req) {
        return Result.ok(shoppingService.addItem(listId, req));
    }

    @PutMapping("/items/{itemId}")
    public Result<ShoppingListItem> updateItem(@PathVariable Long itemId,
                                               @RequestBody ShoppingService.ItemUpdateReq req) {
        return Result.ok(shoppingService.updateItem(itemId, req));
    }

    @DeleteMapping("/items/{itemId}")
    public Result<Void> removeItem(@PathVariable Long itemId) {
        shoppingService.removeItem(itemId);
        return Result.ok();
    }
}
