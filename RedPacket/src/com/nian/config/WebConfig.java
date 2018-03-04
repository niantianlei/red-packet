package com.nian.config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * 配置DispatcherServlet上下文
 * 配置一个试图解析器，通过它找到对应的jsp文件，然后使用数据模型进行渲染，采用自定义创建RequestMappingHandlerAdapter，
 * 为了让他能够支持JSON格式（@ResponseBody）的转换，所以需要创建一个关于对象和JSON的转换消息类，
 * 那就是MappingJackson2HttpMessageConverter类对象。
 * 创建它之后，把它注册给RequestMappingHandlerAdapter对象，这样当控制器遇到注解@ResponseBody时就知道采用
 * JSON消息类型进行应答，那么在控制器完成逻辑后，由处理器将其和消息转换类型做匹配，找到
 * MappingJackson2HttpMessageConverter类对象，从而转变为JSON数据。
 * 
 * @author Niantianlei
 */
@Configuration
//定义Spring MVC扫描的包
@ComponentScan(value="com.*", includeFilters= {@Filter(type = FilterType.ANNOTATION, value = Controller.class)})
//启动Spring MVC配置
@EnableWebMvc
public class WebConfig extends AsyncConfigurerSupport { 

	/***
	 * 通过注解 @Bean 初始化视图解析器
	 * @return ViewResolver 视图解析器
	 */
	@Bean(name="internalResourceViewResolver")
	public ViewResolver initViewResolver() {
		InternalResourceViewResolver viewResolver =new InternalResourceViewResolver();
		viewResolver.setPrefix("/WEB-INF/jsp/");
		viewResolver.setSuffix(".jsp");
		return viewResolver;
	}
	
	/**
	 * 初始化RequestMappingHandlerAdapter，并加载Http的Json转换器
	 * @return  RequestMappingHandlerAdapter 对象
	 */
	@Bean(name="requestMappingHandlerAdapter") 
	public HandlerAdapter initRequestMappingHandlerAdapter() {
		//创建RequestMappingHandlerAdapter适配器
		RequestMappingHandlerAdapter rmhd = new RequestMappingHandlerAdapter();
		//HTTP JSON转换器
		MappingJackson2HttpMessageConverter  jsonConverter 
	        = new MappingJackson2HttpMessageConverter();
		//MappingJackson2HttpMessageConverter接收JSON类型消息的转换
		MediaType mediaType = MediaType.APPLICATION_JSON_UTF8;
		List<MediaType> mediaTypes = new ArrayList<MediaType>();
		mediaTypes.add(mediaType);
		//加入转换器的支持类型
		jsonConverter.setSupportedMediaTypes(mediaTypes);
		//往适配器加入json转换器
		rmhd.getMessageConverters().add(jsonConverter);
		return rmhd;
	}
	
	
	@Override
	public Executor getAsyncExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(5);
		taskExecutor.setMaxPoolSize(10);
		taskExecutor.setQueueCapacity(200);
		taskExecutor.initialize();
		return taskExecutor;
	}
}