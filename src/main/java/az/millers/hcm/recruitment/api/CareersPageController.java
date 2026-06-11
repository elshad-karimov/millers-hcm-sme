package az.millers.hcm.recruitment.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * M279 — forwards the friendly {@code /careers} URL to the static
 * public job-board page (served from {@code static/careers/index.html}).
 */
@Controller
public class CareersPageController {

    @GetMapping("/careers")
    public String careers() {
        return "forward:/careers/index.html";
    }
}
