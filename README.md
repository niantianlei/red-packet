# red packet

博客地址[http://niantianlei.com/2018/03/03/RedPack/](http://niantianlei.com/2018/03/03/RedPack/)，这里格式比较清楚。  


对于高并发系统，存在数据不一致的问题。   
以抢红包为例，假设有一个20万的红包分成2万个，模拟5万个用户同时抢红包，实现一个高并发的场景。在此场景中，线程每一步的顺序不一样，容易导致数据的一致性问题。例如：红包中剩余金额也就为0了，但是一些线程在剩余金额变0之前就进行了库存判断，即认为仍有红包可抢，在金额变0后，仍然可以抢到红包。  这是明显的数据不一致错误。  
为模拟这个场景，搭建了一个SSM开发环境。
## 超发现象
数据库的设计就是一个红包表和一个抢红包的信息表。红包表存有id、红包总金额、红包数量、每个包大小相等，抢红包信息表包括id，时间等。表中的时间是为了标记并发处理请求的速度。  

抢红包的逻辑：先查询红包的信息，判断是否拥有剩余红包可抢。如果红包金额大于0，用户就可以抢，否则不能抢。  
为了演示超发现象，抢红包过程没有加锁。  
```
// 获取大红包信息
RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
// 当前小红包库存大于0
if (redPacket.getStock() > 0) {
	redPacketDao.decreaseRedPacket(redPacketId);
	// 生成抢红包信息
	UserRedPacket userRedPacket = new UserRedPacket();
	userRedPacket.setRedPacketId(redPacketId);
	userRedPacket.setUserId(userId);
	userRedPacket.setAmount(redPacket.getUnitAmount());
	userRedPacket.setNote("抢红包 " + redPacketId);
	// 将抢红包信息插入数据库
	int result = userRedPacketDao.grapRedPacket(userRedPacket);
	return result;
}
// 失败返回
return FAILED;
```
然后借助jQuery的ajax异步请求进行并发操作   
```
var max = 50000;
for (var i = 1; i <= max; i++) {
    $.post({
        //我的数据库中大红包的id为1
        url: "./userRedPacket/grapRedPacket.do?redPacketId=1&userId=" + i,
        success: function (result) {
        	//to do
        }
    });
}
```
用5万个异步请求模拟抢红包场景，等待一段时间后，查看数据的变化。  
<img src="/git-img/chaofa.png"/> 
由数据库的记录可以看到现在的库存为-1，也就是超发现象。已经没有红包了，仍然能发出去。，20万分2万个包，结果抢红包信息表中有20001条记录，明显的错误逻辑。  

然后看性能问题，82秒完成20001个抢红包请求。后面做比较。    

接下来为解决数据不一致的问题分别考虑悲观锁和乐观锁，屏蔽超发现象。  

## 悲观锁
悲观锁是利用数据库内部机制提供的锁方法，加锁才能访问数据。  
在映射器mapper中，新增一个方法，在XML文件中也添加`select...for update`的查询语句。  
这里最好用到主键查询，因为主键索引是行锁，性能更好。  

在红包表中再添加一个20万大红包以供用户抢。  
结果如下：  
<img src="/git-img/beiguan.png"/> 
可以看到库存为0，解决了超发问题。但是考虑性能的话，同样的请求数量花了91秒，相对于不加锁的方式慢了不少。  

对于悲观锁来说，当一条线程抢占了资源后，其他的线程就得不到资源，此时，CPU就会将这些得不到资源的线程挂起，挂起的线程也会消耗CPU，尤其是在高并发场景下请求数量很多。  
只有一个事务占据资源，其他事务被挂起等待持有资源的事务提交并释放资源。线程1提交了事务，红包资源就会释放，线程2在获取。。。  
获取到资源的线程就能恢复到运行态。  
频繁的线程挂起和线程切换，造成很大的开销，这是影响性能的主要原因。悲观锁利用独占锁，只有一个线程可以获得资源。其他线程就要阻塞。造成性能的下降。  

为了克服这一问题，提高并发的能力，避免大量线程因为阻塞导致CPU进行大量的上下文切换，采用乐观锁机制。  

## 乐观锁
乐观锁采用一种不会阻塞其他线程并发的机制，不使用数据库的锁进行实现，不会引发线程频繁挂起和恢复，是一种非阻塞锁，能够提高并发能力。乐观锁采用CAS原理。  
CAS指线程在开始阶段就读入线程共享数据，保存为旧的期望值。当处理完逻辑，需要更新数据的时候，会进行一次比较，即数据的当前值是否和旧的期望值一致。如果一致，更新数据；不一致，则认为数据已经被其他线程更改，就会放弃修改。也可以自旋重试等等。  

但是CAS具有ABA问题，加入版本号即可解决。  

#### 乐观锁实现抢红包
为了规避ABA问题，在高并发中顺利使用乐观锁（CAS），在红包表中增加版本号字段。这里的版本号只能增加不能减少。    
并且在XML中增加一个查询方法。  
```
<update id="decreaseRedPacketForVersion">
update T_RED_PACKET set stock = stock - 1, version = version + 1 
where id = #{id} and version = #{version}
</update>
```
version值一开始保存到了对象中，当减红包库存的时候，再次传给SQL，让SQL对数据库的version和当前线程的旧值进行比较，一致才减库存。  
这样，查询时就不能带上`for update`语句了，不需要加锁了。  
在库存大于0条件下，应该还判断
`update = redPacketDao.decreaseRedPacketForVersion(redPacketId, redPacket.getVersion());`update是否为0，如果为0就是修改失败，版本号不一致。就是CAS失败了。  
如果成功，才在表中插入抢红包信息。  

最后在Controller中添加新的方法，并设置请求路径，ajax的请求路径也一并设置。两个路径相同即可。  
依旧在红包表中添加一个大红包。  
结果如下：  
<img src="/git-img/leguan1.png"/> 
处理用时81秒，和不加锁差不多，这里理论上应该是比不加锁慢一点点的，但影响因素太多，可以理解。  
<img src="/git-img/leguan2.png"/>  
但是，经过5万次抢夺，还会存在大量的红包，也就是存在大量的因为版本不一致的原因造成抢红包失败的请求，失败率很高。  

为克服这个问题，提高成功率，考虑重入机制。也就是说一旦因为版本原因没有抢到红包，则进行重新重试。但是过多的重入会造成大量的SQL语句执行，所以要加入限制，不能一味的重入，一直不成功就一直重入，显然不可以。   
常用的两种限制：  
1.按时间戳重入，在一定时间戳内，不成功会循环到成功，直至超出时间戳，不成功才会退出，返回失败；  
2.按次数，比如限定5次重入，尝试5次后仍失败就判定为请求失败。  
#### 乐观锁的重入（时间戳）
因为上面使用乐观锁造成大量抢红包请求失败的问题，使用时间戳执行乐观锁重入，是一种提高成功率的方法，比如考虑在100s内允许重入。  
逻辑是：   
```
// 记录开始时间
long start = System.currentTimeMillis();
// 无限循环，等待成功或者时间满100毫秒退出
while (true) {
	// 获取循环当前时间
	long end = System.currentTimeMillis();
	// 当前时间已经超过100毫秒，返回失败
	if (end - start > 100) {
		return FAILED;
	}
	// 获取红包信息,注意version值
	RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
	// 当前小红包库存大于0
	if (redPacket.getStock() > 0) {
		// 传入线程保存的version旧值给SQL判断，是否有其他线程修改过数据
		int update = redPacketDao.decreaseRedPacketForVersion(redPacketId,
		redPacket.getVersion());
		// 如果没有数据更新，则说明其他线程已经修改过数据，则重新抢夺
		if (update == 0) {
			continue;
		}
		生成抢红包信息
		插入抢红包信息
		返回结果
	} else {
		没库存返回失败
	}
}
```
<img src="/git-img/reentrant1.png"/>   
可以看到最后没有库存红包了，成功解决问题。  
花费的时间肯定也会多一点，但仍然比悲观锁所用时间短，因此比悲观锁性能高。  
#### 乐观锁的重入（循环次数）
使用时间戳有时候并不是很妥当，随着系统空闲或繁忙导致尝试次数不一。这个时候就要考虑限制重试次数。考虑在5次内允许重入。   
逻辑为：  
```
for (int i = 0; i < 5; i++) {
	// 获取红包信息，注意version值
	RedPacket redPacket = redPacketDao.getRedPacket(redPacketId);
	// 当前小红包库存大于0
	if (redPacket.getStock() > 0) {
		// 再次传入线程保存的version旧值给SQL判断，是否有其他线程修改过数据
		int update = redPacketDao.decreaseRedPacketForVersion(redPacketId,
		redPacket.getVersion());
		// 如果没有数据更新，则说明其他线程已经修改过数据，则重新抢夺
		if (update == 0) {
			continue;
		}
		生成抢红包信息
		...
	} else {
		没库存返回失败
	}
}
```
尝试5次后，不管成败都会判定为失败进行退出。这样避免了过多的重试导致过多SQL被执行的问题，保证性能。  
测试结果如下：  
<img src="/git-img/reentrant2.png"/>   


用悲观锁、乐观锁实现抢红包虽然保证数据一致性，但处理并发的性能远远不够，如果同时存在大量用户，则需要很长时间才能处理完全部请求，那对于后处理请求的用户早就上火了。  
使用Redis做缓存而非直接操作数据库可以大幅度提高性能，因为Redis直接操作内存，比数据库操作磁盘速度要快很多。  

有一个问题是，Redis存储数据并不是长久之计，它只是为了提供更为快速的缓存，而非永久存储的地方。所以当红包金额为0时，要将红包数据保存到数据库中，这样才能保证数据的安全性和持久性。  

首先要创建一个RedisTemplate对象，并注册到IoC容器中  
```
@Bean(name = "redisTemplate")
public RedisTemplate initRedisTemplate() {
	JedisPoolConfig poolConfig = new JedisPoolConfig();
	poolConfig.setMaxIdle(50);//最大空闲数
	poolConfig.setMaxTotal(100);//最大连接数
	poolConfig.setMaxWaitMillis(20000);//最大等待毫秒数
	//创建Jedis连接工厂
	JedisConnectionFactory connectionFactory = new JedisConnectionFactory(poolConfig);
	connectionFactory.setHostName("localhost");
	connectionFactory.setPort(6379);
	//调用后初始化方法，之前没写，抛出异常，网上查原因才发现必须写上
	connectionFactory.afterPropertiesSet();
	..
}
```
此时RedisTemplate就可以在Spring上下文中使用了。  
此外，使用Lua脚本保证原子性，保证数据的一致性访问。避免超发现象。   
第一次运行Lua脚本时，在Redis中编译并缓存，这样可以得到SHA字符串，之后通过这个字符串和参数就能调用Lua脚本了。   
在Redis运行可以使用`redis-cli --eval /路径 , 参数`类似的命令。  
Lua可以通过call()函数来执行Redis命令。  
因此可以将是否缓存抢红包信息的逻辑交给Lua执行，保证原子性。  
具体逻辑是：获取库存，红包库存大于0，库存减1，库存为0就需要将抢红包信息保存到数据库，给出一个状态标志即可。如果库存小于0直接返回。  
当全部红包抢完后，要开启一个新的线程将缓存数据保存到数据库中。否则如果用最后一个用户线程来执行这个任务，会造成该用户等待较长时间，影响体验。  
最后将缓存的两万条数据添加到数据库中，就完成了。  

当然首先要在Redis中设置缓存，然后启动程序，访问路径，发送请求。  
<img src="/git-img/Redis2.png"/> 
前两条为在缓存中写入数据，这里写入了stock剩余红包数量和unit_amount每个红包的面值。  
最后一条是我运行完程序，处理完请求后进行查询，得到当前库存为0，逻辑正确。  

又在数据库中进行查询，得到如下结果  
<img src="/git-img/Redis1.png"/>  
可以看到已经成功把缓存添加到了数据库中，数据库的红包数量也是0。  

再看一下性能  
<img src="/git-img/time.png"/>
5秒多就完成了20000个抢红包操作，并且保证了数据一致性，没有超发现象。  
虽然1秒只完成几千个请求，不能实际应用，我的电脑也不太好用了很多年了对性能也造成一定的影响。实际还有很多提高并发性的措施：主从服务器，消息队列等等。  
但比较下来性能还是远超悲观锁和乐观锁的（两者都用了一分半左右）。  

使用Redis实现高并发，通过Lua脚本规避了数据不一致的问题，当然也可以使用Redis事务锁的方式。最后另创建一个线程操作数据库，因此整个过程只有最后一次涉及到了数据库的操作，操作磁盘，性能得到提高。  
## 问题
最后提交数据的时候遇到了问题，如图  
<img src="/git-img/HotSpot.png"/>  
提示的错误信息是代码缓存空间不够用了，那我就改下虚拟机参数   
在Run->Debug configurations中进行设置，我设置到256m还是溢出，电脑比较差一共才4G内存，于是就改了下程序，不能一次性吧缓存数据全部添加到数据库，一部分一部分的添加。从Redis列表中每次取出以前个数据添加到数据库，直至完成，就成功解决了。  


## 总结
本次实验首先演示了超发现象，然后使用了三种方法模拟安全的抢红包场景，分别为悲观锁、乐观锁、Redis缓存。乐观锁又分为三种，不使用重入、时间戳重入、循环次数重入。    
悲观锁方法中事务串行执行，虽然避免了超发现象，但性能有了很大降低。  
乐观锁采用CAS实现，虽然性能高，但有大量请求失效。需要采用重入技术，增加重入后，发现能大幅度提高请求成功率并且并发性能也很高。  