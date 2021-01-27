package com.example.write_spring_demo.Service.impl;

import com.example.framework.annotation.Service;
import com.example.write_spring_demo.Service.DemoService;

@Service
public class DemoServiceImpl implements DemoService {

    @Override
    public String test(String name) {

        return String.format("my name is %s.",name);
    }
}
