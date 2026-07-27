/*
package com.rhino_horn.safari.rhino_horn;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RhinoHornApplicationTests {

	@Test
	void contextLoads() {
	}

}
*/

// This test checks if the index page loads successfully and verifies the model attributes.
package com.rhino_horn.safari.rhino_horn;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class RhinoHornApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void indexPageLoadsSuccessfully() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(model().attribute("mainTitle", "EUREKA...!!"))
            .andExpect(model().attribute("subTitle", "A Complete CI/CD Pipeline Potraying:"))
            .andExpect(model().attribute("msg", "GitOps/DevSecOps Workflow"));
    }
}
