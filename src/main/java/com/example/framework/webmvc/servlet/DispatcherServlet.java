package com.example.framework.webmvc.servlet;

import com.example.framework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String, Object> ioc = new HashMap<>();

    private List<Handler> handlerMapping = new ArrayList<>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // 等待请求
        try{
            doDispatch(req,resp);
        }catch (Exception e){
            e.printStackTrace();
            resp.getWriter().write("500 ");
        }

    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        Handler handler = getHandler(req);

        if(handler == null) {
            resp.getWriter().write("404 Not Found!");
            return;
        }
        //获取方法的参数列表
        Class[] paramTypes = handler.method.getParameterTypes();

        //保存所有需要自动赋值的参数值
        Object[] paramValues = new Object[paramTypes.length];

        Map<String, String[]> params = req.getParameterMap();
        for(Map.Entry<String, String[]> param : params.entrySet()) {
            String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", "");
            //如果找到匹配的对象，则开始填充参数值
            if(!handler.paramIndexMapping.containsKey(param.getKey())) continue;
            int index = handler.paramIndexMapping.get(param.getKey());
            paramValues[index] = convert(paramTypes[index], value);
        }

        //设置方法中的request和response对象
        int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
        paramValues[reqIndex] = req;

        int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
        paramValues[respIndex] = resp;

        handler.method.invoke(handler.controller, paramValues);
    }

    private Handler getHandler(HttpServletRequest req) {
        if(handlerMapping.isEmpty()) return null;

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();

        url = url.replace(contextPath, "").replace("/+", "/");

        for(Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);

            if(!matcher.matches()) continue;

            return handler;
        }

        return null;
    }
    private Object convert(Class<?> type, String value) {
        if(Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }


    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1启动加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        // 2扫描所有相关的类
        doScanner(contextConfig.getProperty("scanPackage"));

        // 3初始化所有相关的类
        doInstance();

        // 4自动注入
        doAutowired();

        // 5初始化handlerMapping
        initHandlerMapping();

        System.out.println("spring init ....");

        // ********Spring核心初始化完成*********

        //等待请求

    }

    private void initHandlerMapping() {
        if(ioc.isEmpty()){
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(Controller.class)){
                continue;
            }
            String baseBrl = "";
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                baseBrl = requestMapping.value();
            }

            // 扫描所有的方法
            for (Method method: clazz.getMethods()) {
                if(!method.isAnnotationPresent(RequestMapping.class)){
                   continue;
                }
                RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                String regex = ("/" + baseBrl + requestMapping.value()).replaceAll("/+","/");
                Pattern pattern = Pattern.compile(regex);
                handlerMapping.add(new Handler(entry.getValue(), method, pattern));
                System.out.println("mapping:"+ regex+","+method);
            }
        }
    }

    private void doAutowired() {
        if(ioc.isEmpty()){
            return;
        }
        // 循环ioc容器中所有的类 对需要自动赋值的字段或者属性进行赋值
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            // 依赖注入,不管是什么范围的
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                // 没有加上这个注解就不去管他
                if(!field.isAnnotationPresent(Autowired.class)){
                    continue;
                }
                Autowired autowired = field.getAnnotation(Autowired.class);
                String beanName = autowired.value().trim();
                if("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                // 暴力访问
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }

            }
        }
    }

    private void doInstance() {
        if(classNames.isEmpty()){
            return;
        }
        try {
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);

                // 只有加了注解的类才实例化
                if(clazz.isAnnotationPresent(Controller.class)){
                    // key 默认类名首字母小写
                    String beanName = lowerFirstCase(clazz.getName());
                    ioc.put(beanName, clazz.newInstance());
                }else if(clazz.isAnnotationPresent(Service.class)){





                    //2 如果自己定义了名字,优先使用自定义的名字
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    //1 默认采用首字母小写
                    if("".equals(beanName)){
                        beanName = lowerFirstCase(clazz.getName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //3 根据接口类型来赋值
                    for (Class<?> i : clazz.getInterfaces()) {
                        Object o = ioc.get(i.getName());
                        if(null != o){
                            throw new Exception("类名重复");
                        }
                        ioc.put(i.getName(), instance);
                    }

                }else{
                    continue;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
//        URL url = this.getClass().getClassLoader().getResource("");

        File classDir  = new File(url.getFile());

        for (File file : classDir.listFiles()) {
            if(file.isDirectory()){
                doScanner(scanPackage + "." + file.getName());
            }else {
                String className = scanPackage + "." + file.getName().replace(".class", "");
                classNames.add(className);

            }        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if( null != resourceAsStream){
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String lowerFirstCase(String str){
        char[] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    /**
     * Handler记录Controoler中的RequestMapping和Method的对应关系
     */
    private class Handler {
        //方法对应的实例
        private Object controller;
        //映射的方法
        private Method method;
        //URL正则匹配
        private Pattern pattern;
        //参数顺序
        private Map<String, Integer> paramIndexMapping;

        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {

            //提取方法中加了注解的参数
            Annotation[][] pa = method.getParameterAnnotations();
            for(int i = 0; i < pa.length; i ++) {
                for(Annotation a : pa[i]) {
                    if(a instanceof RequestParam) {
                        String paramName = ((RequestParam) a).value();
                        if(!"".equals(paramName)) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中的request和response参数
            Class<?> [] paramTypes = method.getParameterTypes();
            for(int i = 0; i < paramTypes.length; i ++) {
                Class<?> type = paramTypes[i];

                if(type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }

        }

    }
}
