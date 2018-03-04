package com.nian.config;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

import redis.clients.jedis.JedisPoolConfig;

/**
 * 注解@EnableTransactionManagement，并且实现TransactionManagementConfigurer，
 * 是为了实现注解式的事务，将来可以通过@Transactional配置事务。
 * annotationDrivenTransactionManager方法返回一个事务管理器。
 * 
 * 除了事务外，还配置了数据源SqlSessionFactoryBean和MyBatis的扫描类，并把MyBatis的扫描类通过
 * 注解@Repository和包名("com.*")限定。这样MyBatis就会通过Spring的机制找到对应的接口和配置，
 * Spring会把对应的接口装配到IoC容器中。
 * 
 * @author Niantianlei
 */
@Configuration
//定义Spring 扫描的包
@ComponentScan(value= "com.*", includeFilters= {@Filter(type = FilterType.ANNOTATION, value ={Service.class})})
//使用事务驱动管理器
@EnableTransactionManagement
//实现接口TransactionManagementConfigurer，这样可以配置注解驱动事务
public class RootConfig implements TransactionManagementConfigurer {
	
	private DataSource dataSource = null;
	
	/**
	 * 配置数据库
	 * @return 数据连接池
	 */
	@Bean(name = "dataSource")
	public DataSource initDataSource() {
		if (dataSource != null) {
			return dataSource;
		}
		Properties props = new Properties();
		props.setProperty("driverClassName", "com.mysql.jdbc.Driver");
		props.setProperty("url", "jdbc:mysql://localhost:3306/chapter22");
		props.setProperty("username", "root");
		props.setProperty("password", "admin");
        props.setProperty("maxActive", "200");
		props.setProperty("maxIdle", "20");
		props.setProperty("maxWait", "30000");
		try {
			dataSource = BasicDataSourceFactory.createDataSource(props);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dataSource;
	}
	
	/***
	 * 配置SqlSessionFactoryBean
	 * @return SqlSessionFactoryBean
	 */
	@Bean(name="sqlSessionFactory")
	public SqlSessionFactoryBean initSqlSessionFactory() {
		SqlSessionFactoryBean sqlSessionFactory = new SqlSessionFactoryBean();
		sqlSessionFactory.setDataSource(initDataSource());
		//配置MyBatis配置文件
		Resource resource = new ClassPathResource("mybatis/mybatis-config.xml");
		sqlSessionFactory.setConfigLocation(resource);
		return sqlSessionFactory;
	}
	
	/***
	 * 通过自动扫描，发现MyBatis Mapper接口
	 * @return Mapper扫描器
	 */
	@Bean 
	public MapperScannerConfigurer initMapperScannerConfigurer() {
		MapperScannerConfigurer msc = new MapperScannerConfigurer();
		msc.setBasePackage("com.*");
		msc.setSqlSessionFactoryBeanName("sqlSessionFactory");
		msc.setAnnotationClass(Repository.class);
		return msc;
	}
	
	
	/**
	 * 实现接口方法，注册注解事务，当@Transactional 使用的时候产生数据库事务 
	 */
	@Override
	@Bean(name="annotationDrivenTransactionManager")
	public PlatformTransactionManager annotationDrivenTransactionManager() {
		DataSourceTransactionManager transactionManager = 
           new DataSourceTransactionManager();
		transactionManager.setDataSource(initDataSource());
		return transactionManager;
	}
	
	/*
	 * 创建一个RedisTemplate对象，并装载到IoC容器中。
	 * 这样RedisTemplate就能在Spring上下文中使用了。
	 * JedisConnectionFactory对象在最后的时候需要自行调用afterPropertiesSet方法，
	 * 它实现了InitializingBean接口。如果将其配置在IoC容器中，Spring会自动调用它，但是这里我们自行创建，
	 * 因为需要自行调用，否则在运用的时候会抛出异常，出现错误。
	 */
	@Bean(name = "redisTemplate")
	public RedisTemplate initRedisTemplate() {
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		//最大空闲数
		poolConfig.setMaxIdle(50);
		//最大连接数
		poolConfig.setMaxTotal(100);
		//最大等待毫秒数
		poolConfig.setMaxWaitMillis(20000);
		//创建Jedis连接工厂
		JedisConnectionFactory connectionFactory = new JedisConnectionFactory(poolConfig);
		connectionFactory.setHostName("localhost");
		connectionFactory.setPort(6379);
		//调用后初始化方法，没有它将抛出异常
		connectionFactory.afterPropertiesSet();
		//自定Redis序列化器
		RedisSerializer jdkSerializationRedisSerializer = new JdkSerializationRedisSerializer();
		RedisSerializer stringRedisSerializer = new StringRedisSerializer();
		//定义RedisTemplate，并设置连接工程[修改为：工厂]
		RedisTemplate redisTemplate = new RedisTemplate();
		redisTemplate.setConnectionFactory(connectionFactory);
		//设置序列化器
		redisTemplate.setDefaultSerializer(stringRedisSerializer);
		redisTemplate.setKeySerializer(stringRedisSerializer);
		redisTemplate.setValueSerializer(stringRedisSerializer);
		redisTemplate.setHashKeySerializer(stringRedisSerializer);
		redisTemplate.setHashValueSerializer(stringRedisSerializer);
		return redisTemplate;
	}
	
}