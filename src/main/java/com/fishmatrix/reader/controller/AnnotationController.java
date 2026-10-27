package com.fishmatrix.reader.controller;

import com.fishmatrix.common.auth.UserContext;
import com.fishmatrix.reader.model.Annotation;
import com.fishmatrix.reader.model.AnnotationList;
import com.fishmatrix.reader.model.AnnotationRequest;
import com.fishmatrix.reader.service.AnnotationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reader")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    @GetMapping("/books/{bookId}/chapters/{chapterIndex}/annotations")
    public AnnotationList annotations(
            @PathVariable long bookId,
            @PathVariable int chapterIndex,
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "200") int limit) {
        return new AnnotationList(annotationService.listBookAnnotations(
                bookId, chapterIndex, afterId, limit, UserContext.require()));
    }

    @PostMapping("/books/{bookId}/chapters/{chapterIndex}/annotations")
    @ResponseStatus(HttpStatus.CREATED)
    public Annotation addAnnotation(
            @PathVariable long bookId,
            @PathVariable int chapterIndex,
            @Valid @RequestBody AnnotationRequest request) {
        return annotationService.addAnnotation(
                bookId, chapterIndex, request, UserContext.require());
    }
}
