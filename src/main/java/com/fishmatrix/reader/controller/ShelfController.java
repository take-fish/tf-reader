package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.RequirePermission;
import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.reader.model.Shelf;
import com.fishmatrix.reader.model.ShelfDetail;
import com.fishmatrix.reader.model.ShelfList;
import com.fishmatrix.reader.model.ShelfRequest;
import com.fishmatrix.reader.service.ShelfService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reader")
@RequirePermission("reader.private.manage")
public class ShelfController {

    private final ShelfService shelfService;

    public ShelfController(ShelfService shelfService) {
        this.shelfService = shelfService;
    }

    @GetMapping("/shelves")
    public ShelfList list() {
        return new ShelfList(shelfService.list(UserContext.require()));
    }

    @GetMapping("/shelves/{shelfId}")
    public ShelfDetail get(@PathVariable long shelfId) {
        return shelfService.get(shelfId, UserContext.require());
    }

    @PostMapping("/shelves")
    @ResponseStatus(HttpStatus.CREATED)
    public Shelf create(@Valid @RequestBody ShelfRequest request) {
        return shelfService.create(request, UserContext.require());
    }

    @PutMapping("/shelves/{shelfId}")
    public Shelf update(@PathVariable long shelfId, @Valid @RequestBody ShelfRequest request) {
        return shelfService.update(shelfId, request, UserContext.require());
    }

    @DeleteMapping("/shelves/{shelfId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long shelfId) {
        shelfService.delete(shelfId, UserContext.require());
    }

    @PostMapping("/shelves/{shelfId}/books/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addBook(@PathVariable long shelfId, @PathVariable long bookId) {
        shelfService.addBook(shelfId, bookId, UserContext.require());
    }

    @DeleteMapping("/shelves/{shelfId}/books/{bookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeBook(@PathVariable long shelfId, @PathVariable long bookId) {
        shelfService.removeBook(shelfId, bookId, UserContext.require());
    }
}
