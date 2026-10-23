package com.nagesh.spotify;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NageshSpotifyApplicationTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static String json(String body) {
        return body.replace('\'', '"');
    }

    @Test
    void servesTheCatalog() throws Exception {
        mvc.perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.albums", hasSize(6)))
                .andExpect(jsonPath("$.tracks", hasSize(16)))
                .andExpect(jsonPath("$.tracks[0].artist").value("Neon Harbor"))
                .andExpect(jsonPath("$.tracks[0].src").value(startsWith("https://www.soundhelix.com/")))
                .andExpect(jsonPath("$.genres", hasSize(8)));
    }

    @Test
    void seedsDefaultPlaylists() throws Exception {
        mvc.perform(get("/api/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playlists[*].id", contains("p-daily", "p-chill", "p-drive")))
                .andExpect(jsonPath("$.liked", hasSize(0)))
                .andExpect(jsonPath("$.recent", hasSize(0)));
    }

    @Test
    void likesAndUnlikesTracks() throws Exception {
        mvc.perform(put("/api/liked/t3")).andExpect(jsonPath("$", contains("t3")));
        mvc.perform(put("/api/liked/t1")).andExpect(jsonPath("$", contains("t3", "t1")));
        mvc.perform(put("/api/liked/t1")).andExpect(jsonPath("$", contains("t3", "t1")));
        mvc.perform(delete("/api/liked/t3")).andExpect(jsonPath("$", contains("t1")));
        mvc.perform(put("/api/liked/nope")).andExpect(status().isNotFound());
    }

    @Test
    void managesPlaylists() throws Exception {
        String body = mvc.perform(post("/api/playlists").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'  Gym  ','trackId':'t5'}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gym"))
                .andExpect(jsonPath("$.tracks", contains("t5")))
                .andReturn().getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/playlists/" + id + "/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'trackId':'t7'}")))
                .andExpect(jsonPath("$.tracks", contains("t5", "t7")));
        mvc.perform(post("/api/playlists/" + id + "/tracks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'trackId':'t7'}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already in “Gym”"));
        mvc.perform(delete("/api/playlists/" + id + "/tracks/t5"))
                .andExpect(jsonPath("$.tracks", contains("t7")));
        mvc.perform(patch("/api/playlists/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'name':'Leg Day'}")))
                .andExpect(jsonPath("$.name").value("Leg Day"));
        mvc.perform(get("/api/library"))
                .andExpect(jsonPath("$.playlists[3].name").value("Leg Day"));

        mvc.perform(delete("/api/playlists/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/library")).andExpect(jsonPath("$.playlists", hasSize(3)));
    }

    @Test
    void rejectsBlankPlaylistNames() throws Exception {
        mvc.perform(post("/api/playlists").contentType(MediaType.APPLICATION_JSON).content(json("{'name':'  '}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keepsTheSixMostRecentPlays() throws Exception {
        for (String id : new String[] {"t1", "t2", "t3", "t4", "t5", "t6", "t7", "t2"}) {
            mvc.perform(post("/api/recent").contentType(MediaType.APPLICATION_JSON)
                    .content(json("{'trackId':'" + id + "'}"))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/library"))
                .andExpect(jsonPath("$.recent", contains("t2", "t7", "t6", "t5", "t4", "t3")));
    }

    @Test
    void searchesSongsAlbumsAndPlaylists() throws Exception {
        mvc.perform(get("/api/search").param("q", "  SAFFRON "))
                .andExpect(jsonPath("$.tracks[*].id", contains("t3", "t4", "t14")))
                .andExpect(jsonPath("$.albums[*].id", contains("a2")))
                .andExpect(jsonPath("$.playlists", hasSize(0)));
        mvc.perform(get("/api/search").param("q", "chill"))
                .andExpect(jsonPath("$.playlists[*].id", contains("p-chill")));
    }

    @Test
    void genreMixes() throws Exception {
        mvc.perform(get("/api/genres/Telugu Hits/mix")).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/genres/Polka/mix")).andExpect(status().isNotFound());
    }

    @Test
    void servesTheWebApp() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/app.js")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api")));
    }
}
