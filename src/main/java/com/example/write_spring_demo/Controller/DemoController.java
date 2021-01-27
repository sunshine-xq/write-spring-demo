package com.example.write_spring_demo.Controller;

import com.example.framework.annotation.Autowired;
import com.example.framework.annotation.Controller;
import com.example.framework.annotation.RequestMapping;
import com.example.framework.annotation.RequestParam;
import com.example.write_spring_demo.Service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/demo")
public class DemoController {

    @Autowired
    private DemoService demoService;

    @RequestMapping("/demo")
    public void demo(HttpServletRequest req, HttpServletResponse resp, @RequestParam("name") String nam){
        String test = demoService.test(nam);
        try {
            resp.getWriter().write(test);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
