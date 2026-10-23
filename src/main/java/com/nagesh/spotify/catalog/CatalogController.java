package com.nagesh.spotify.catalog;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private static final int MIX_SIZE = 6;

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/catalog")
    public Catalog catalog() {
        return catalog.catalog();
    }

    @GetMapping("/genres/{name}/mix")
    public List<String> genreMix(@PathVariable String name) {
        if (catalog.genre(name).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown genre: " + name);
        }
        return catalog.randomMix(MIX_SIZE);
    }
}
