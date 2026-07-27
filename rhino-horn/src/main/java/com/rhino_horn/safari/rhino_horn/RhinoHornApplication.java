package com.rhino_horn.safari.rhino_horn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
@Controller
public class RhinoHornApplication {

    @GetMapping("/")
    public String index(final Model model) {
        model.addAttribute("mainTitle", "EUREKA...!!");
        model.addAttribute("subTitle", "A Complete CI/CD Pipeline Potraying:");
        model.addAttribute("msg", "GitOps/DevSecOps Workflow");
        return "index";
    }

    public static void main(String[] args) {
	SpringApplication.run(RhinoHornApplication.class, args);
    }
}


