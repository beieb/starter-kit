package wot.motion;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MotionSensorApplicationTests {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GatewayClient gateway;

    @Test
    void testGetModel() throws Exception {
        mvc.perform(get("/model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("motion")))
                .andExpect(jsonPath("$.properties.lastMotion.readOnly", is(true)))
                .andExpect(jsonPath("$.actions.simulateMotion", notNullValue()))
                .andExpect(jsonPath("$.events.motion", notNullValue()))
                .andExpect(jsonPath("$.links.simulateMotion", is("/actions/simulateMotion")));
    }

    @Test
    void testGetProperties() throws Exception {
        mvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastMotion", notNullValue()));
    }

    @Test
    void testGetProperty() throws Exception {
        mvc.perform(get("/properties/lastMotion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("lastMotion")))
                .andExpect(jsonPath("$.value", notNullValue()));
    }

    @Test
    void testGetUnknownProperty() throws Exception {
        mvc.perform(get("/properties/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", containsString("unknown property")));
    }

    @Test
    void testWriteReadOnlyProperty() throws Exception {
        mvc.perform(put("/properties/lastMotion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", containsString("read-only")));
    }

    @Test
    void testSimulateMotion() throws Exception {
        mvc.perform(post("/actions/simulateMotion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action", is("simulateMotion")))
                .andExpect(jsonPath("$.status", is("completed")))
                .andExpect(jsonPath("$.properties.lastMotion", notNullValue()));
    }

    @Test
    void testUnknownAction() throws Exception {
        mvc.perform(post("/actions/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }
}
