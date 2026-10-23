package com.nagesh.spotify.catalog;

import java.util.List;

public record Album(String id, String title, String artist, int year, List<String> colors) {
}
